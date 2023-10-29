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
import io.aston.service.Worker;
import io.aston.user.UserDataException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Controller("/v1")
public class RuntimeController implements RuntimeApi {

    private final ITaskDao taskDao;
    private final IWorkflowDao workflowDao;
    private final NextStepService nextStepService;

    public RuntimeController(ITaskDao taskDao, IWorkflowDao workflowDao, NextStepService nextStepService) {
        this.taskDao = taskDao;
        this.workflowDao = workflowDao;
        this.nextStepService = nextStepService;
    }

    @Override
    public CompletableFuture<HttpResponse<Task>> queue(String taskName, String workerId, Long timeout, HttpRequest<?> request) {
        String wid = UUID.randomUUID().toString();
        request.setAttribute("wid", wid);
        CompletableFuture<HttpResponse<Task>> future = new CompletableFuture<>();
        Worker worker = new Worker(taskName, workerId, wid, future);
        if (timeout == null || timeout < 0 || timeout > 45) timeout = 30L;
        nextStepService.getTaskQueue().workerCall(worker, timeout * 1000L);
        return future;
    }

    @Override
    public Task changeTask(String id, TaskOutput taskOutput) {
        TaskEntity task = taskDao.loadById(id)
                .orElseThrow(() -> new UserDataException("task not found"));
        WorkflowEntity workflow = workflowDao.loadById(task.getWorkflowId())
                .orElseThrow(() -> new UserDataException("workflow not found"));
        nextStepService.checkChangeState(task.getState(), taskOutput.getState());
        task.setOutput(taskOutput.getOutput());
        task.setState(taskOutput.getState());
        task.setFinished(Instant.now());
        taskDao.updateState(task);
        if (taskOutput.getState() == State.COMPLETED && task.getOutput() != null && task.getOutputVar() != null) {
            if (task.getOutputVar().equals("$.") && task.getOutput() instanceof Map) {
                workflowDao.updateParams(workflow.getId(), (Map<String, Object>) task.getOutput());
            } else {
                workflowDao.updateParams(workflow.getId(), Map.of(task.getOutputVar(), task.getOutput()));
            }
        }
        nextStepService.nextStep(workflow.getId(), task);
        return nextStepService.toTask(task, workflow);
    }
}
