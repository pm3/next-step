package io.aston.controller;

import io.aston.api.RuntimeApi;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.*;
import io.aston.service.NewWorkflowEventStream;
import io.aston.service.NextStepService;
import io.aston.service.TaskEventStream;
import io.aston.service.TaskFinishEventQueue;
import io.aston.user.UserDataException;
import io.aston.worker.Worker;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Controller("/v1")
public class RuntimeController implements RuntimeApi, ApplicationEventListener<HttpRequestTerminatedEvent> {

    private final ITaskDao taskDao;
    private final IWorkflowDao workflowDao;
    private final NextStepService nextStepService;
    private final NewWorkflowEventStream newWorkflowEventStream;
    private final TaskEventStream taskEventStream;
    private final TaskFinishEventQueue taskFinishEventQueue;
    private final ConcurrentHashMap<String, Worker<?>> workerMap = new ConcurrentHashMap<>();

    public RuntimeController(ITaskDao taskDao, IWorkflowDao workflowDao, NextStepService nextStepService, NewWorkflowEventStream newWorkflowEventStream, TaskEventStream taskEventStream, TaskFinishEventQueue taskFinishEventQueue) {
        this.taskDao = taskDao;
        this.workflowDao = workflowDao;
        this.nextStepService = nextStepService;
        this.newWorkflowEventStream = newWorkflowEventStream;
        this.taskEventStream = taskEventStream;
        this.taskFinishEventQueue = taskFinishEventQueue;
    }

    @Override
    public CompletableFuture<HttpResponse<Task>> queueNewTasks(List<String> taskName, String workerId, Long timeout, HttpRequest<?> request) {
        String wid = UUID.randomUUID().toString();
        request.setAttribute("wid", wid);
        CompletableFuture<TaskEntity> future = new CompletableFuture<>();

        if (taskName == null || taskName.isEmpty()) throw new UserDataException("workflowName required");
        String[] arrTaskNames = taskName.toArray(new String[0]);
        Worker<TaskEntity> worker = new Worker<>(arrTaskNames, workerId, wid, future, workerMap);
        if (timeout == null || timeout < 0 || timeout > 45) timeout = 30L;
        taskEventStream.workerCall(worker, worker.getQuery(), timeout * 1000L);
        return future.thenApply((t) -> t != null ? HttpResponse.ok(taskEventStream.toTask(t)) : HttpResponse.noContent());
    }

    @Override
    public CompletableFuture<HttpResponse<Workflow>> queueFreeWorkflows(List<String> workflowName, String workerId, Long timeout, HttpRequest<?> request) {
        String wid = UUID.randomUUID().toString();
        request.setAttribute("wid", wid);
        CompletableFuture<WorkflowEntity> future = new CompletableFuture<>();
        if (workflowName == null || workflowName.isEmpty()) throw new UserDataException("workflowName required");
        String[] arrWorkflowNames = workflowName.toArray(new String[0]);
        Worker<WorkflowEntity> worker = new Worker<>(arrWorkflowNames, workerId, wid, future, workerMap);
        if (timeout == null || timeout < 0 || timeout > 45) timeout = 30L;
        newWorkflowEventStream.workerCall(worker, worker.getQuery(), timeout * 1000L);
        return future.thenApply((w) ->
                w != null ? HttpResponse.ok(newWorkflowEventStream.toWorkflow(w)) : HttpResponse.noContent());
    }

    @Override
    public CompletableFuture<HttpResponse<TaskFinish>> queueFinishedTasks(String workerId, Long timeout, HttpRequest<?> request) {
        String wid = UUID.randomUUID().toString();
        request.setAttribute("wid", wid);
        CompletableFuture<TaskFinish> future = new CompletableFuture<>();

        Worker<TaskFinish> worker = new Worker<>(workerId, workerId, wid, future, workerMap);
        if (timeout == null || timeout < 0 || timeout > 45) timeout = 30L;
        taskFinishEventQueue.workerCall(worker, worker.getQuery0(), timeout * 1000L);
        return future.thenApply((ft) -> ft != null ? HttpResponse.ok(ft) : HttpResponse.noContent());
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
            taskEventStream.runTask(task);
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
                taskFinishEventQueue.add(finish, finish.getWorkerId());
            }
        }
        return taskEventStream.toTask(task);
    }

    @Override
    public Task createTask(TaskCreate taskCreate) {

        WorkflowEntity workflow = workflowDao.loadById(taskCreate.getWorkflowId())
                .orElseThrow(() -> new UserDataException("workflow not found"));

        if (WorkflowController.LOCAL_WORKER.equals(workflow.getWorker())) {
            throw new UserDataException("local workflow");
        }
        if (workflow.getWorker() == null) {
            workflow.setWorker(taskCreate.getWorkerId());
            workflow.setState(State.RUNNING);
            workflow.setModified(Instant.now());
            workflowDao.updateWorker(workflow.getId(), State.RUNNING, workflow.getWorker(), workflow.getModified());
        }
        if (!workflow.getWorker().equals(taskCreate.getWorkerId())) {
            throw new UserDataException("workflow run in different worker");
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
        newTask.setState(State.SCHEDULED);
        newTask.setCreated(Instant.now());
        newTask.setRetries(0);
        newTask.setRunningTimeout(taskCreate.getRunningTimeout());
        newTask.setMaxRetryCount(taskCreate.getMaxRetryCount());
        newTask.setRetryWait(taskCreate.getRetryWait());
        taskDao.insert(newTask);
        taskEventStream.add(newTask, newTask.getTaskName());
        return taskEventStream.toTask(newTask);
    }

    private void checkChangeState(TaskEntity task, State newState) {
        if (in(task.getState(), State.FATAL_ERROR, State.COMPLETED)) {
            throw new UserDataException("no change final state " + task.getState());
        }
        if (in(newState, State.SCHEDULED, State.RUNNING)) {
            throw new UserDataException("no change state to " + newState.name());
        }
        if (task.getMaxRetryCount() > 0 && task.getRetries() >= task.getMaxRetryCount()) {
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

    @Override
    public void onApplicationEvent(HttpRequestTerminatedEvent event) {
        Optional<Object> wid = event.getSource().getAttribute("wid");
        if (wid.isPresent()) {
            Worker<?> worker = workerMap.remove((String) wid.get());
            if (worker != null) worker.removeFuture();
        }
    }

    @Override
    public List<String> statTasks() {
        return taskEventStream.values().stream().map(TaskEntity::getId).toList();
    }
}
