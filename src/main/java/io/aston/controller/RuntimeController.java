package io.aston.controller;

import io.aston.api.RuntimeApi;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.State;
import io.aston.model.Task;
import io.aston.model.TaskOutput;
import io.aston.service.NextStepService;
import io.aston.service.TaskQueue;
import io.aston.service.Worker;
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
    private final TaskQueue taskQueue;

    public RuntimeController(ITaskDao taskDao, IWorkflowDao workflowDao, NextStepService nextStepService, TaskQueue taskQueue) {
        this.taskDao = taskDao;
        this.workflowDao = workflowDao;
        this.nextStepService = nextStepService;
        this.taskQueue = taskQueue;
    }

    @Override
    public CompletableFuture<HttpResponse<Task>> queue(String taskName, String workerId, Long timeout, HttpRequest<?> request) {
        String wid = UUID.randomUUID().toString();
        request.setAttribute("wid", wid);
        CompletableFuture<TaskEntity> future = new CompletableFuture<>();

        Worker worker = new Worker(taskName, workerId, wid, future);
        if (timeout == null || timeout < 0 || timeout > 45) timeout = 30L;
        taskQueue.workerCall(worker, timeout * 1000L);
        return future.thenApply((t) -> (t != null) ? HttpResponse.ok(toTask(t)) : HttpResponse.noContent());
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

    @Override
    public Task changeTask(String id, TaskOutput taskOutput) {
        TaskEntity task = taskDao.loadById(id)
                .orElseThrow(() -> new UserDataException("task not found"));
        WorkflowEntity workflow = workflowDao.loadById(task.getWorkflowId())
                .orElseThrow(() -> new UserDataException("workflow not found"));
        if (workflow.getState() != State.RUNNING) {
            throw new UserDataException("not running workflow");
        }
        nextStepService.checkChangeState(task.getState(), taskOutput.getState());
        task.setOutput(taskOutput.getOutput());
        task.setState(taskOutput.getState());
        task.setModified(Instant.now());
        taskDao.updateState(task);
        nextStepService.nextStep(workflow, task);
        return toTask(task);
    }
}
