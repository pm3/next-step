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
    private final NextStepService nextStepService;
    private final TaskEventStream taskEventStream;
    private final TaskFinishEventQueue taskFinishEventQueue;

    private static final Logger logger = LoggerFactory.getLogger(Task.class);

    public TaskController(ITaskDao taskDao, IWorkflowDao workflowDao, NextStepService nextStepService, TaskEventStream taskEventStream, TaskFinishEventQueue taskFinishEventQueue) {
        this.taskDao = taskDao;
        this.workflowDao = workflowDao;
        this.nextStepService = nextStepService;
        this.taskEventStream = taskEventStream;
        this.taskFinishEventQueue = taskFinishEventQueue;
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
        if (workflow.getWorkerId() == null && workflow.getState() == State.SCHEDULED) {
            workflow.setWorkerId(taskCreate.getWorkerId());
            workflow.setState(State.RUNNING);
            workflow.setModified(Instant.now());
            workflowDao.updateState(workflow);
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
        newTask.setState(taskCreate.getState() != null && State.in(taskCreate.getState(), State.FAILED, State.COMPLETED, State.FATAL_ERROR) ? taskCreate.getState() : State.SCHEDULED);
        newTask.setCreated(Instant.now());
        newTask.setModified(newTask.getCreated());
        newTask.setRetries(0);
        newTask.setRunningTimeout(taskCreate.getRunningTimeout());
        newTask.setMaxRetryCount(taskCreate.getMaxRetryCount());
        newTask.setRetryWait(taskCreate.getRetryWait());
        taskDao.insert(newTask);
        taskEventStream.add(newTask, newTask.getTaskName());
        return taskEventStream.toTask(newTask);
    }

    @Override
    public Task finishTask(String id, TaskFinish taskFinish) {
        TaskEntity task = taskDao.loadById(id)
                .orElseThrow(() -> new UserDataException("task not found"));
        WorkflowEntity workflow = workflowDao.loadById(task.getWorkflowId())
                .orElseThrow(() -> new UserDataException("workflow not found"));


        if (WorkflowController.LOCAL_WORKER.equals(workflow.getWorkerId())) {
            throw new UserDataException("local workflow");
        }
        if (taskFinish.getWorkerId() != null && workflow.getWorkerId() == null && workflow.getState() == State.SCHEDULED) {
            workflow.setWorkerId(taskFinish.getWorkerId());
            workflow.setState(State.RUNNING);
            workflow.setModified(Instant.now());
            workflowDao.updateState(workflow);
        }
        if (workflow.getState() != State.RUNNING) {
            throw new UserDataException("only running workflow");
        }

        checkChangeState(task, taskFinish.getState());
        task.setOutput(taskFinish.getOutput());
        task.setState(taskFinish.getState());
        task.setModified(Instant.now());
        task.setWorkflowId(taskFinish.getWorkerId());
        taskDao.updateState(task);

        if (task.getState() == State.FAILED && task.getRetries() < task.getMaxRetryCount()) {
            taskEventStream.runTask(task);
        } else {
            if (WorkflowController.LOCAL_WORKER.equals(workflow.getWorkerId())) {
                //local workflow
                nextStepService.nextStep(workflow, task);
            } else {
                //external workflow
                taskFinishEventQueue.add(task, workflow.getWorkerId());
            }
        }
        return taskEventStream.toTask(task);
    }

    private void checkChangeState(TaskEntity task, State newState) {
        if (State.in(task.getState(), State.FATAL_ERROR, State.COMPLETED)) {
            throw new UserDataException("no change final state " + task.getState());
        }
        if (State.in(newState, State.SCHEDULED, State.RUNNING)) {
            throw new UserDataException("no change state to " + newState.name());
        }
        if (task.getMaxRetryCount() > 0 && task.getRetries() >= task.getMaxRetryCount()) {
            throw new UserDataException("no change state, max retries");
        }
        if (!State.in(task.getState(), State.SCHEDULED, State.RUNNING, State.FAILED)) {
            throw new UserDataException("no change state to " + newState.name() + ", old state is " + task.getState().name());
        }
    }
}
