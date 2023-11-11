package io.aston.controller;

import com.aston.micronaut.sql.where.Multi;
import io.aston.api.TaskApi;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.*;
import io.aston.service.AllEventStream;
import io.aston.service.InternalEvent;
import io.aston.user.UserDataException;
import io.micronaut.http.annotation.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller("/v1")
public class TaskController implements TaskApi {

    private final ITaskDao taskDao;
    private final IWorkflowDao workflowDao;
    private final AllEventStream eventStream;
    private static final Logger logger = LoggerFactory.getLogger(Task.class);

    public TaskController(ITaskDao taskDao, IWorkflowDao workflowDao, AllEventStream eventStream) {
        this.taskDao = taskDao;
        this.workflowDao = workflowDao;
        this.eventStream = eventStream;
        this.eventStream.setHandleFailedStateTask(this::fireTaskState);
    }

    @Override
    public List<Task> search(TaskQuery query) {
        return taskDao.selectByQuery(Multi.of(query.getNames()),
                query.getStates() != null ? Multi.of(query.getStates().stream().map(State::name).toList()) : null,
                Multi.of(query.getWorkflowNames()),
                query.getDateFrom(),
                query.getDateTo());
    }

    @Override
    public Task fetch(String id) {
        return taskDao.loadTaskById(id)
                .orElseThrow(() -> new UserDataException("not found"));
    }

    @Override
    public Task createTask(TaskCreate taskCreate) {

        WorkflowEntity workflow = workflowDao.loadById(taskCreate.getWorkflowId())
                .orElseThrow(() -> new UserDataException("workflow not found"));
        if (workflow.getState() != State.RUNNING) {
            throw new UserDataException("only running workflow");
        }
        TaskEntity newTask = new TaskEntity();
        newTask.setId(UUID.randomUUID().toString());
        newTask.setWorkflowId(workflow.getId());
        newTask.setTaskName(taskCreate.getTaskName());
        newTask.setWorkflowName(workflow.getWorkflowName());
        newTask.setParams(taskCreate.getParams());
        newTask.setState(State.SCHEDULED);
        newTask.setCreated(Instant.now());
        newTask.setModified(newTask.getCreated());
        newTask.setRetries(0);
        newTask.setRunningTimeout(taskCreate.getRunningTimeout());
        newTask.setMaxRetryCount(taskCreate.getMaxRetryCount());
        taskDao.insert(newTask);
        eventStream.runTask(newTask);
        return eventStream.toTask(newTask);
    }

    @Override
    public Task finishTask(String id, TaskFinish taskFinish) {
        TaskEntity task = taskDao.loadById(id)
                .orElseThrow(() -> new UserDataException("task not found"));
        WorkflowEntity workflow = workflowDao.loadById(task.getWorkflowId())
                .orElseThrow(() -> new UserDataException("workflow not found"));

        if (workflow.getState() != State.RUNNING) {
            throw new UserDataException("only running workflow");
        }

        checkChangeState(task, taskFinish.getState());
        task.setOutput(taskFinish.getOutput());
        task.setState(taskFinish.getState());
        task.setModified(Instant.now());
        if (taskFinish.getWorkerId() != null) {
            task.setWorkerId(taskFinish.getWorkerId());
        }
        if (task.getState() == State.RETRY) {
            task.setRetries(task.getRetries() + 1);
            if (task.getRetries() >= task.getMaxRetryCount()) {
                task.setState(State.FAILED);
                Map<String, String> errValue = Map.of("type", "retry", "message", "max retry");
                task.setOutput(errValue);
            }
        }
        taskDao.updateState(task);
        fireTaskState(task, workflow);
        return eventStream.toTask(task);
    }

    private void fireTaskState(TaskEntity task) {
        WorkflowEntity workflow = workflowDao.loadById(task.getWorkflowId())
                .orElseThrow(() -> new UserDataException("workflow not found"));
        fireTaskState(task, workflow);
    }

    private void fireTaskState(TaskEntity task, WorkflowEntity workflow) {
        switch (task.getState()) {
            case AWAIT:
                break;
            case RETRY:
                eventStream.runTask(task);
                break;
            case COMPLETED, FAILED:
                eventStream.add(new InternalEvent(EventType.FINISHED_TASK, workflow.getWorkerId(), null, task, -1L));
        }
    }

    private void checkChangeState(TaskEntity task, State newState) {
        if (State.in(task.getState(), State.COMPLETED, State.FAILED)) {
            throw new UserDataException("no change closed task " + task.getState().name());
        }
        if (State.in(newState, State.SCHEDULED, State.RUNNING)) {
            throw new UserDataException("no change to open state - " + newState.name());
        }
    }
}
