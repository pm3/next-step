package io.aston.service;

import com.aston.micronaut.sql.where.Multi;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.State;
import io.aston.model.TaskDef;
import io.aston.user.UserDataException;
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

        List<TaskDef> taskDefList = metaCacheService.workflowTasks(workflow.getId());
        if (taskDefList == null) throw new UserDataException("no task definitions");

        if (lastEntity != null && lastEntity.getState() == State.FATAL_ERROR) {
            metaCacheService.deleteWorkflowTasks(workflow.getId());
            return;
        }
        if (lastEntity != null && lastEntity.getState() == State.FAILED) {
            workflow.setState(State.FAILED);
            workflow.setModified(Instant.now());
            workflowDao.updateState(workflow.getId(), workflow.getState(), workflow.getModified(), null);
            taskDao.updateWorkflowAll(workflow.getId(),
                    Multi.of(List.of(State.SCHEDULED, State.RUNNING)),
                    State.FAILED,
                    "workflow FAILED",
                    workflow.getModified());
            metaCacheService.deleteWorkflowTasks(workflow.getId());
            return;
        }

        if (workflow.getState() == State.SCHEDULED) {
            workflow.setState(State.RUNNING);
            workflow.setModified(Instant.now());
            workflowDao.updateState(workflow.getId(), workflow.getState(), workflow.getModified(), null);
        }

        if (lastEntity != null && lastEntity.getState() == State.COMPLETED) {
            TaskDef lastTaskDef = taskDef(taskDefList, lastEntity.getRef());
            if (lastTaskDef != null && lastTaskDef.getOutputVar() != null) {
                saveParams(workflow, lastEntity, lastTaskDef.getOutputVar());
            }
        }


        TaskDef nextTaskDef = taskDef(taskDefList, lastEntity != null ? lastEntity.getRef() + 1 : 1);
        if (nextTaskDef == null) {
            //finish workflow
            workflow.setState(State.COMPLETED);
            workflow.setModified(Instant.now());
            workflowDao.updateState(workflow.getId(), workflow.getState(), workflow.getModified(), null);
            metaCacheService.deleteWorkflowTasks(workflow.getId());
            return;
        }
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
        taskDao.insert(newTask);

        taskEventStream.runTask(newTask);
    }

    private TaskDef taskDef(List<TaskDef> l, int ref) {
        if (ref > 0 && ref <= l.size() && l.get(ref - 1).getRef() == ref)
            return l.get(ref - 1);
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> computeParams(Map<String, Object> paramsDef, WorkflowEntity workflow) {
        Map<String, Object> params = new HashMap<>();
        if (paramsDef != null) {
            Map<String, Object> scope = new HashMap<>();
            if (workflow.getParams() != null)
                scope.putAll(workflow.getParams());
            if (workflow.getOutput() instanceof Map) {
                scope.putAll((Map<String, Object>) workflow.getOutput());
            }
            for (Map.Entry<String, Object> e : paramsDef.entrySet()) {
                if (e.getValue() instanceof String str && str.startsWith("${") && str.endsWith("}")) {
                    Object val = computeValue(str.substring(2, str.length() - 1), scope);
                    params.put(e.getKey(), val);
                } else {
                    params.put(e.getKey(), e.getValue());
                }
            }
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    private void saveParams(WorkflowEntity workflow, TaskEntity lastEntity, String outputVar) {
        Map<String, Object> scope = new HashMap<>();
        if (workflow.getOutput() instanceof Map) {
            scope.putAll((Map<String, Object>) workflow.getOutput());
        }
        if (outputVar.equals("$.")) {
            if (lastEntity.getOutput() instanceof Map) {
                scope.putAll((Map<String, Object>) lastEntity.getOutput());
            } else {
                logger.debug("ignore local task output $. " + lastEntity.getOutput());
            }
        } else {
            scope.put(outputVar, lastEntity.getOutput());
        }
        workflow.setOutput(scope);
        workflowDao.updateOutput(workflow.getId(), workflow.getOutput());
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
