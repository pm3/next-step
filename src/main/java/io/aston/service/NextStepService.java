package io.aston.service;

import com.aston.micronaut.sql.where.Multi;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.State;
import io.aston.model.Task;
import io.aston.model.TaskDef;
import jakarta.inject.Singleton;
import ognl.Ognl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

@Singleton
public class NextStepService {

    private final IWorkflowDao workflowDao;
    private final ITaskDao taskDao;
    private final TaskQueue taskQueue;
    private final MetaCacheService metaCacheService;

    private static final Logger logger = LoggerFactory.getLogger(NextStepService.class);

    public NextStepService(IWorkflowDao workflowDao, ITaskDao taskDao, TaskQueue taskQueue, MetaCacheService metaCacheService) {
        this.workflowDao = workflowDao;
        this.taskDao = taskDao;
        this.taskQueue = taskQueue;
        this.metaCacheService = metaCacheService;
        taskQueue.setNextStepRunning(this::nextStepRunning);

        List<Task> oldTasks = taskDao.selectByQuery(null, Multi.of(List.of("SCHEDULED")), null, null, null);
        for (Task task : oldTasks) {
            taskQueue.addTask(task);
        }
    }

    public TaskQueue getTaskQueue() {
        return taskQueue;
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
                TaskDef def = metaCacheService.workflowTask(workflow.getId(), lastEntity.getRef());
                if (def != null && def.getRetryCount() > lastEntity.getRetries()) {
                    delayFailedRetry(lastEntity.getId(), def.getRetryWait());
                    return;
                }
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
            taskDao.insert(newTask);

            if (workflow.getState() == State.SCHEDULED) {
                workflow.setState(State.RUNNING);
                workflowDao.updateState(workflow);
            }

            taskQueue.addTask(toTask(newTask));
        }
    }

    private boolean nextStepRunning(Task task) {
        Instant now = Instant.now();
        if (taskDao.updateState(task.getId(), State.SCHEDULED, State.RUNNING, now) == 0) {
            logger.debug("stop running, task not scheduled " + task.getId());
            return false;
        }
        task.setState(State.RUNNING);
        task.setModified(now);
        TaskDef def0 = metaCacheService.workflowTask(task.getWorkflowId(), task.getRef());
        String taskId = task.getId();
        if (def0 != null) {
            long timeout = def0.getTimeout();
            if (timeout <= 0) timeout = 30;
            taskQueue.schedule(new Date(System.currentTimeMillis() + timeout * 1000L), () -> {
                TaskEntity taskEntity = taskDao.loadById(taskId).orElse(null);
                if (taskEntity != null && taskEntity.getState() == State.RUNNING) {
                    taskEntity.setState(State.FAILED);
                    taskEntity.setOutput("timeout");
                    taskEntity.setModified(Instant.now());
                    taskDao.updateState(taskEntity);
                    TaskDef def = metaCacheService.workflowTask(taskEntity.getWorkflowId(), taskEntity.getRef());
                    if (def != null && def.getRetryCount() > taskEntity.getRetries()) {
                        delayFailedRetry(taskEntity.getId(), def.getRetryWait());
                    }
                }
            });
        }
        return true;
    }

    private void delayFailedRetry(String taskId, long retryDelay) {
        if (retryDelay <= 0) retryDelay = 60;
        taskQueue.schedule(new Date(System.currentTimeMillis() + retryDelay * 1000), () -> {
            TaskEntity task = taskDao.loadById(taskId).orElse(null);
            if (task != null && task.getState() == State.FAILED) {
                TaskDef def = metaCacheService.workflowTask(task.getWorkflowId(), task.getRef());
                logger.debug("failed to schedule" + task.getRetries() + " < " + def.getRetryCount() + " " + task.getId());
                task.setState(State.SCHEDULED);
                task.setRetries(task.getRetries() + 1);
                task.setModified(Instant.now());
                taskDao.updateState(task);
                taskQueue.addTask(toTask(task));
            }
        });
    }

    public Task toTask(TaskEntity task) {
        Task t2 = new Task();
        t2.setId(task.getId());
        t2.setWorkflowId(task.getWorkflowId());
        t2.setRef(task.getRef());
        t2.setTaskName(task.getTaskName());
        t2.setWorkflowName(task.getWorkflowName());
        t2.setParams(task.getParams());
        t2.setOutput(task.getOutput());
        t2.setState(task.getState());
        t2.setCreated(task.getCreated());
        t2.setModified(task.getModified());
        t2.setRetries(task.getRetries());
        return t2;
    }

    public void checkChangeState(State state, State state2) {
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
                if (e.getValue() instanceof String sval && sval.startsWith("${") && sval.endsWith("}")) {
                    Object val = computeValue(sval.substring(2, sval.length() - 1), scope);
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
            if (e.getValue() instanceof String sval && sval.startsWith("${") && sval.endsWith("}")) {
                return true;
            }
        }
        return false;
    }

    public Object computeValue(String expr, Map<String, Object> scope) {
        try {
            return Ognl.getValue(expr, scope);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
