package io.aston.service;

import com.aston.micronaut.sql.where.Multi;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.State;
import io.aston.model.Task;
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
    private final TaskQueue taskQueue;

    private static final Logger logger = LoggerFactory.getLogger(NextStepService.class);

    public NextStepService(IWorkflowDao workflowDao, ITaskDao taskDao, TaskQueue taskQueue) {
        this.workflowDao = workflowDao;
        this.taskDao = taskDao;
        this.taskQueue = taskQueue;
        taskQueue.setChangeTaskState(this::changeTaskState);

        List<Task> oldTasks = taskDao.selectByQuery(null, Multi.of(List.of("SCHEDULED")), null, null, null);
        for (Task task : oldTasks) {
            taskQueue.addTask(task);
        }
    }

    public TaskQueue getTaskQueue() {
        return taskQueue;
    }

    public void nextStep(String workflowId, TaskEntity lastEntity) {

        WorkflowEntity workflow = workflowDao.loadById(workflowId)
                .orElseThrow(() -> new UserDataException("workflow not found"));

        if (lastEntity != null && lastEntity.getState() == State.FATAL_ERROR) {
            workflow.setState(State.FATAL_ERROR);
            workflow.setFinished(Instant.now());
            workflowDao.updateState(workflow.getId(), null, workflow.getState(), workflow.getFinished());
            return;
        }
        if (lastEntity != null && lastEntity.getState() == State.FAILED) {
            TaskDef def = taskDef(workflow, lastEntity.getRef());
            if (def != null && def.getRetryCount() > lastEntity.getRetries()) {
                logger.debug("failed to schedule" + lastEntity.getRetries() + " < " + def.getRetryCount() + " " + lastEntity.getId());
                lastEntity.setState(State.SCHEDULED);
                lastEntity.setRetries(lastEntity.getRetries() + 1);
                taskDao.updateState(lastEntity);
                taskQueue.addTask(toTask(lastEntity, workflow));
                return;
            }
            //no retry, fail workflow
            workflow.setState(State.FAILED);
            workflow.setFinished(Instant.now());
            workflowDao.updateState(workflow.getId(), null, workflow.getState(), workflow.getFinished());
            return;
        }

        int lastRef = lastEntity != null ? lastEntity.getRef() : 0;
        TaskDef nextTaskDef = nextTaskDef(workflow, lastRef);
        if (nextTaskDef != null && "#finish".equals(nextTaskDef.getName())) {
            workflow.setState(State.COMPLETED);
            workflow.setFinished(Instant.now());
            workflowDao.updateState(workflow.getId(), State.RUNNING, workflow.getState(), workflow.getFinished());
            return;
        }
        if (nextTaskDef != null) {

            TaskEntity newTask = new TaskEntity();
            newTask.setId(UUID.randomUUID().toString());
            newTask.setWorkflowId(workflow.getId());
            newTask.setRef(nextTaskDef.getRef());
            newTask.setOutputVar(nextTaskDef.getOutputVar());
            newTask.setTaskName(nextTaskDef.getName());
            newTask.setWorkflowName(workflow.getWorkflowName());
            newTask.setParams(computeParams(nextTaskDef.getParams(), workflow));
            newTask.setState(State.SCHEDULED);
            newTask.setCreated(Instant.now());
            newTask.setFinished(null);
            newTask.setRetries(0);
            taskDao.insert(newTask);

            workflow.setState(State.RUNNING);
            workflowDao.updateState(workflow.getId(), State.SCHEDULED, workflow.getState(), workflow.getFinished());

            taskQueue.addTask(toTask(newTask, workflow));
            return;
        }
    }

    private void changeTaskState(Task task) {
        taskDao.updateState(task.getId(), task.getState());
    }

    public Task toTask(TaskEntity task, WorkflowEntity workflow) {
        Task t2 = new Task();
        t2.setId(task.getId());
        t2.setWorkflowId(task.getWorkflowId());
        t2.setTaskName(task.getTaskName());
        t2.setWorkflowName(task.getWorkflowName());
        t2.setParams(task.getParams());
        t2.setOutput(task.getOutput());
        t2.setState(task.getState());
        t2.setCreated(task.getCreated());
        t2.setFinished(task.getFinished());
        t2.setRetries(task.getRetries());
        t2.setTaskDef(taskDef(workflow, task.getRef()));
        return t2;
    }

    public void checkChangeState(State state, State state2) {
    }

    public TaskDef taskDef(WorkflowEntity workflow, int ref) {
        for (TaskDef def : workflow.getDefTasks()) {
            if (def.getRef() == ref) return def;
        }
        return null;
    }

    public TaskDef nextTaskDef(WorkflowEntity workflow, int lastRef) {
        int max = workflow.getDefTasks().size();
        if (max > 0 && workflow.getDefTasks().get(max - 1).getRef() == lastRef) {
            TaskDef finish = new TaskDef();
            finish.setName("#finish");
            return finish;
        }
        for (TaskDef def : workflow.getDefTasks()) {
            if (def.getRef() == lastRef + 1) return def;
        }
        return null;
    }

    public Map<String, Object> computeParams(Map<String, Object> paramsDef, WorkflowEntity workflow) {
        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, Object> e : paramsDef.entrySet()) {
            if (e.getValue() instanceof String sval && sval.startsWith("${") && sval.endsWith("}")) {
                Object val = computeValue(sval.substring(2, sval.length() - 1), workflow.getParams());
                params.put(e.getKey(), val);
            } else {
                params.put(e.getKey(), e.getValue());
            }
        }
        return params;
    }

    public Object computeValue(String expr, Map<String, Object> params) {
        try {
            return Ognl.getValue(expr, params);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
