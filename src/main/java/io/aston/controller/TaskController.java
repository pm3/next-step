package io.aston.controller;

import com.aston.micronaut.sql.where.Multi;
import io.aston.api.TaskApi;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.*;
import io.aston.service.NextStepService;
import io.aston.service.TaskEventStream;
import io.aston.service.TaskFinishEventQueue;
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
    private final TaskEventStream taskEventStream;
    private final TaskFinishEventQueue taskFinishEventQueue;
    private final NextStepService nextStepService;

    private static final Logger logger = LoggerFactory.getLogger(Task.class);

    public TaskController(ITaskDao taskDao, IWorkflowDao workflowDao, TaskEventStream taskEventStream, TaskFinishEventQueue taskFinishEventQueue, NextStepService nextStepService) {
        this.taskDao = taskDao;
        this.workflowDao = workflowDao;
        this.taskEventStream = taskEventStream;
        this.taskFinishEventQueue = taskFinishEventQueue;
        this.nextStepService = nextStepService;
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

        if (WorkflowController.LOCAL_WORKER.equals(workflow.getWorkerId())) {
            throw new UserDataException("local workflow");
        }

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
        newTask.setRef(taskCreate.getRef());
        newTask.setTaskName(taskCreate.getTaskName());
        newTask.setWorkflowName(workflow.getWorkflowName());
        newTask.setParams(taskCreate.getParams());
        newTask.setState(taskCreate.getState() != null && State.in(taskCreate.getState(), State.COMPLETED, State.FAILED, State.FATAL_ERROR) ? taskCreate.getState() : State.SCHEDULED);
        newTask.setCreated(taskCreate.getCreated() != null && taskCreate.getCreated().isAfter(workflow.getCreated()) && taskCreate.getCreated().isBefore(Instant.now()) ? taskCreate.getCreated() : Instant.now());
        newTask.setModified(Instant.now());
        newTask.setRetries(0);
        newTask.setRunningTimeout(taskCreate.getRunningTimeout());
        newTask.setMaxRetryCount(taskCreate.getMaxRetryCount());
        newTask.setRetryWait(taskCreate.getRetryWait());
        taskDao.insert(newTask);
        taskEventStream.runTask(newTask);
        return taskEventStream.toTask(newTask);
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
        if (task.getState() == State.FATAL_ERROR) {
            workflowFatalError(workflow);
        }
        if (WorkflowController.LOCAL_WORKER.equals(workflow.getWorkerId())) {
            nextStepService.nextStep(workflow, task);
        } else {
            taskFinishEventQueue.add(task, workflow.getWorkerId());
        }
        return taskEventStream.toTask(task);
    }

    private void checkChangeState(TaskEntity task, State newState) {
        if (!State.in(task.getState(), State.SCHEDULED, State.RUNNING)) {
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
