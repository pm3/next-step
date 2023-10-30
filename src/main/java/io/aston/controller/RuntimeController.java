package io.aston.controller;

import io.aston.api.RuntimeApi;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.*;
import io.aston.service.*;
import io.aston.user.UserDataException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Controller("/v1")
public class RuntimeController implements RuntimeApi {

    private final ITaskDao taskDao;
    private final IWorkflowDao workflowDao;
    private final NextStepService nextStepService;
    private final RunTaskService runTaskService;
    private final EventQueue eventQueue;

    public RuntimeController(ITaskDao taskDao, IWorkflowDao workflowDao, NextStepService nextStepService, RunTaskService runTaskService, EventQueue eventQueue) {
        this.taskDao = taskDao;
        this.workflowDao = workflowDao;
        this.nextStepService = nextStepService;
        this.runTaskService = runTaskService;
        this.eventQueue = eventQueue;
    }

    public record TaskEvent(TaskEntity task) implements IEvent {
        @Override
        public String getName() {
            return task.getTaskName();
        }
    }

    @Override
    public CompletableFuture<HttpResponse<Task>> queueNewTasks(String taskName, String workerId, Long timeout, HttpRequest<?> request) {
        String wid = UUID.randomUUID().toString();
        request.setAttribute("wid", wid);
        CompletableFuture<IEvent> future = new CompletableFuture<>();

        Worker worker = new Worker(taskName, workerId, wid, future);
        if (timeout == null || timeout < 0 || timeout > 45) timeout = 30L;
        eventQueue.workerCall(worker, timeout * 1000L);
        return future.thenApply((e) -> (e instanceof TaskEvent te && te.task() != null) ? HttpResponse.ok(toTask(te.task())) : HttpResponse.noContent());
    }

    private Task toTask(TaskEntity task) {
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

    public record WorkflowEvent(Workflow workflow) implements IEvent {
        @Override
        public String getName() {
            return workflow.getWorkflowName();
        }
    }

    @Override
    public CompletableFuture<HttpResponse<Workflow>> queueFreeWorkflows(String workflowName, String workerId, Long timeout, HttpRequest<?> request) {
        String wid = UUID.randomUUID().toString();
        request.setAttribute("wid", wid);
        CompletableFuture<IEvent> future = new CompletableFuture<>();

        Worker worker = new Worker(workflowName, workerId, wid, future);
        if (timeout == null || timeout < 0 || timeout > 45) timeout = 30L;
        eventQueue.workerCall(worker, timeout * 1000L);
        return future.thenApply((e) -> (e instanceof WorkflowEvent we && we.workflow() != null) ? HttpResponse.ok(we.workflow()) : HttpResponse.noContent());
    }

    public record TaskFinishEvent(TaskFinish taskFinish) implements IEvent {
        @Override
        public String getName() {
            return taskFinish.getWorkerId();
        }
    }

    @Override
    public CompletableFuture<HttpResponse<TaskFinish>> queueFinishedTasks(String workerId, Long timeout, HttpRequest<?> request) {
        String wid = UUID.randomUUID().toString();
        request.setAttribute("wid", wid);
        CompletableFuture<IEvent> future = new CompletableFuture<>();

        Worker worker = new Worker(workerId, workerId, wid, future);
        if (timeout == null || timeout < 0 || timeout > 45) timeout = 30L;
        eventQueue.workerCall(worker, timeout * 1000L);
        return future.thenApply((e) -> (e instanceof TaskFinishEvent tfe && tfe.taskFinish() != null) ? HttpResponse.ok(tfe.taskFinish()) : HttpResponse.noContent());
    }

    @Override
    public Task changeTask(String id, TaskOutput taskOutput) {
        TaskEntity task = taskDao.loadById(id)
                .orElseThrow(() -> new UserDataException("task not found"));
        WorkflowEntity workflow = workflowDao.loadById(task.getWorkflowId())
                .orElseThrow(() -> new UserDataException("workflow not found"));
        if (workflow.getState() != State.RUNNING) {
            throw new UserDataException("not running workflow");
        }
        checkChangeState(task, taskOutput.getState());
        task.setOutput(taskOutput.getOutput());
        task.setState(taskOutput.getState());
        task.setModified(Instant.now());
        taskDao.updateState(task);

        if (task.getState() == State.FAILED && task.getRetries() < task.getMaxRetryCount()) {
            runTaskService.runTask(task, null);
        } else {
            if (WorkflowController.LOCAL_WORKER.equals(workflow.getWorker())) {
                //local workflow
                nextStepService.nextStep(workflow, task);
            } else {
                //external workflow
                TaskFinish finish = new TaskFinish();
                finish.setTaskId(task.getId());
                finish.setWorkerId(workflow.getWorker());
                finish.setState(task.getState());
                finish.setOutput(task.getOutput());
                eventQueue.addEvent(new TaskFinishEvent(finish));
            }
        }
        return toTask(task);
    }

    @Override
    public Task createTask(TaskCreate taskCreate) {

        WorkflowEntity workflow = workflowDao.loadById(taskCreate.getWorkflowId())
                .orElseThrow(() -> new UserDataException("workflow not found"));
        if (workflow.getState() != State.RUNNING) {
            throw new UserDataException("only running workflow");
        }
        if (WorkflowController.LOCAL_WORKER.equals(workflow.getWorker())) {
            throw new UserDataException("local workflow");
        }

        TaskEntity newTask = new TaskEntity();
        newTask.setId(UUID.randomUUID().toString());
        newTask.setWorkflowId(workflow.getId());
        newTask.setRef(taskCreate.getRef());
        newTask.setTaskName(taskCreate.getTaskName());
        newTask.setWorkflowName(workflow.getWorkflowName());
        newTask.setParams(taskCreate.getParams());
        newTask.setState(State.SCHEDULED);
        newTask.setCreated(Instant.now());
        newTask.setRetries(0);
        newTask.setRunningTimeout(taskCreate.getRunningTimeout());
        newTask.setMaxRetryCount(taskCreate.getMaxRetryCount());
        newTask.setRetryWait(taskCreate.getRetryWait());
        taskDao.insert(newTask);
        eventQueue.addEvent(new TaskEvent(newTask));
        return toTask(newTask);
    }

    private void checkChangeState(TaskEntity task, State newState) {
        if (in(task.getState(), State.FATAL_ERROR, State.COMPLETED)) {
            throw new UserDataException("no change final state " + task.getState());
        }
        if (in(newState, State.SCHEDULED, State.RUNNING)) {
            throw new UserDataException("no change state to " + newState.name());
        }
        if (task.getRetries() >= task.getMaxRetryCount()) {
            throw new UserDataException("no change state, max retries");
        }
        if (!in(task.getState(), State.SCHEDULED, State.RUNNING, State.FAILED)) {
            throw new UserDataException("no change state to " + newState.name() + ", old state is " + task.getState().name());
        }
    }

    private boolean in(State s1, State... states) {
        for (State s2 : states) if (s1 == s2) return true;
        return false;
    }
}
