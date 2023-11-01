package io.aston.service;

import com.aston.micronaut.sql.where.Multi;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.State;
import io.aston.model.TaskDef;
import jakarta.inject.Singleton;
import ognl.Ognl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Singleton
public class NextStepService {

    private final IWorkflowDao workflowDao;
    private final ITaskDao taskDao;
    private final TaskEventStream taskEventStream;
    private final MetaCacheService metaCacheService;

    private static final Logger logger = LoggerFactory.getLogger(NextStepService.class);

    public NextStepService(IWorkflowDao workflowDao, ITaskDao taskDao, TaskEventStream taskEventStream, MetaCacheService metaCacheService) {
        this.workflowDao = workflowDao;
        this.taskDao = taskDao;
        this.taskEventStream = taskEventStream;
        this.metaCacheService = metaCacheService;
    }

    public void nextStep(WorkflowEntity workflow, TaskEntity lastEntity) {

        if (lastEntity != null) {
            if (lastEntity.getState() == State.FATAL_ERROR) {
                workflow.setState(State.FATAL_ERROR);
                workflow.setModified(Instant.now());
                workflowDao.updateState(workflow);
                taskDao.updateWorkflowAll(workflow.getId(),
                        Multi.of(List.of(State.SCHEDULED, State.RUNNING, State.FAILED)),
                        State.FATAL_ERROR,
                        "workflow kill",
                        workflow.getModified());
                metaCacheService.deleteWorkflowTasks(workflow.getId());
                return;
            }
            if (lastEntity.getState() == State.FAILED) {
                lastEntity.setState(State.FATAL_ERROR);
                lastEntity.setOutput("max retry");
                lastEntity.setModified(Instant.now());
                taskDao.updateState(lastEntity);
                workflow.setState(State.FATAL_ERROR);
                workflow.setModified(Instant.now());
                workflowDao.updateState(workflow);
                taskDao.updateWorkflowAll(workflow.getId(),
                        Multi.of(List.of(State.SCHEDULED, State.RUNNING, State.FAILED)),
                        State.FATAL_ERROR,
                        "workflow kill",
                        workflow.getModified());
                metaCacheService.deleteWorkflowTasks(workflow.getId());
                return;
            }
        }

        int lastRef = lastEntity != null ? lastEntity.getRef() : 0;
        TaskDef nextTaskDef = nextTaskDef(workflow, lastRef);
        if (nextTaskDef != null && "#finish".equals(nextTaskDef.getName())) {
            workflow.setState(State.COMPLETED);
            workflow.setModified(Instant.now());
            workflowDao.updateState(workflow);
            metaCacheService.deleteWorkflowTasks(workflow.getId());
            return;
        }
        if (nextTaskDef != null) {
            TaskEntity newTask = new TaskEntity();
            newTask.setId(UUID.randomUUID().toString());
            newTask.setWorkflowId(workflow.getId());
            newTask.setRef(nextTaskDef.getRef());
            newTask.setTaskName(nextTaskDef.getName());
            newTask.setWorkflowName(workflow.getWorkflowName());
            newTask.setParams(computeParams(nextTaskDef.getParams(), workflow));
            newTask.setState(State.SCHEDULED);
            newTask.setCreated(Instant.now());
            newTask.setRetries(0);
            newTask.setRunningTimeout(nextTaskDef.getTimeout());
            newTask.setMaxRetryCount(newTask.getMaxRetryCount());
            newTask.setRetryWait(newTask.getRetryWait());
            taskDao.insert(newTask);

            if (workflow.getState() == State.SCHEDULED) {
                workflow.setState(State.RUNNING);
                workflowDao.updateState(workflow);
            }
            taskEventStream.runTask(newTask);
        }
    }

    public TaskDef nextTaskDef(WorkflowEntity workflow, int lastRef) {
        List<TaskDef> l = metaCacheService.workflowTasks(workflow.getId());
        if (l != null) {
            for (int i = 0; i < l.size(); i++) {
                TaskDef def = l.get(i);
                if (def.getRef() == lastRef + 1) return def;
                if (i == l.size() - 1 && def.getRef() == lastRef) {
                    TaskDef finish = new TaskDef();
                    finish.setName("#finish");
                    return finish;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> computeParams(Map<String, Object> paramsDef, WorkflowEntity workflow) {
        Map<String, Object> params = new HashMap<>();
        if (needScope(paramsDef)) {
            Map<String, Object> scope = new HashMap<>(workflow.getParams());
            List<TaskEntity> tasks = taskDao.searchWorkflowScopeTasks(workflow.getId());
            Map<Integer, TaskDef> defMap = metaCacheService.workflowTaskMap(workflow.getId());
            for (TaskEntity task : tasks) {
                TaskDef def = defMap.get(task.getRef());
                if (def != null && def.getOutputVar() != null) {
                    if (def.getOutputVar().equals("$.") && task.getOutput() instanceof Map) {
                        scope.putAll((Map<String, Object>) task.getOutput());
                    } else {
                        scope.put(def.getOutputVar(), task.getOutput());
                    }
                }
            }
            for (Map.Entry<String, Object> e : paramsDef.entrySet()) {
                if (e.getValue() instanceof String str && str.startsWith("${") && str.endsWith("}")) {
                    Object val = computeValue(str.substring(2, str.length() - 1), scope);
                    params.put(e.getKey(), val);
                } else {
                    params.put(e.getKey(), e.getValue());
                }
            }
        } else {
            params.putAll(paramsDef);
        }
        return params;
    }

    private boolean needScope(Map<String, Object> paramsDef) {
        for (Map.Entry<String, Object> e : paramsDef.entrySet()) {
            if (e.getValue() instanceof String str && str.startsWith("${") && str.endsWith("}")) {
                return true;
            }
        }
        return false;
    }

    public Object computeValue(String expr, Map<String, Object> scope) {
        try {
            return Ognl.getValue(expr, scope);
        } catch (Exception e) {
            logger.debug("parse ognl error {}", e.getMessage());
            return null;
        }
    }
}
