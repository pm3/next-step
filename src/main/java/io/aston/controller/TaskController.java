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

        if (workflow.getState() == State.SCHEDULED) {
            if (taskCreate.getWorkerId() == null) {
                throw new UserDataException("start running workflow, workflow workerId is required");
            }
            workflow.setWorkerId(taskCreate.getWorkerId());
            workflow.setState(State.RUNNING);
            workflow.setModified(Instant.now());
            workflowDao.updateState(workflow.getId(), workflow.getState(), workflow.getModified(), workflow.getWorkerId());
        }
        if (workflow.getState() != State.RUNNING) {
            throw new UserDataException("only running workflow");
        }
        TaskEntity newTask = new TaskEntity();
        newTask.setId(UUID.randomUUID().toString());
        newTask.setWorkflowId(workflow.getId());
        newTask.setTaskName(taskCreate.getTaskName());
        newTask.setWorkflowName(workflow.getWorkflowName());
        newTask.setParams(taskCreate.getParams());
        newTask.setState(taskCreate.getState() != null && State.in(taskCreate.getState(), State.COMPLETED, State.FAILED, State.FATAL_ERROR) ? taskCreate.getState() : State.SCHEDULED);
        newTask.setCreated(taskCreate.getCreated() != null && taskCreate.getCreated().isAfter(workflow.getCreated()) && taskCreate.getCreated().isBefore(Instant.now()) ? taskCreate.getCreated() : Instant.now());
        newTask.setModified(Instant.now());
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
        task.setWorkerId(taskFinish.getWorkerId());
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
            case FATAL_ERROR:
                workflowFatalError(workflow);
            case COMPLETED, FAILED:
                eventStream.add(new InternalEvent(EventType.FINISHED_TASK, workflow.getWorkerId(), null, task, -1L));
        }
    }

    private void checkChangeState(TaskEntity task, State newState) {
        if (!State.in(task.getState(), State.SCHEDULED, State.RUNNING, State.RETRY)) {
            throw new UserDataException("no change closed task " + task.getState().name());
        }
        if (State.in(newState, State.SCHEDULED, State.RUNNING)) {
            throw new UserDataException("no change to open state - " + newState.name());
        }
    }

    private void workflowFatalError(WorkflowEntity workflow) {
        workflow.setState(State.FATAL_ERROR);
        workflow.setModified(Instant.now());
        workflowDao.updateState(workflow.getId(), workflow.getState(), workflow.getModified(), null);
        taskDao.updateWorkflowAll(workflow.getId(),
                Multi.of(List.of(State.SCHEDULED, State.RUNNING)),
                State.FAILED,
                "workflow FATAL_ERROR",
                workflow.getModified());
    }
}
