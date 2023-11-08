package io.aston.service;

import io.aston.entity.WorkflowEntity;
import io.aston.worker.SimpleTimer;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class WorkerFinishWait {

    private final SimpleTimer timer;

    public WorkerFinishWait(SimpleTimer timer) {
        this.timer = timer;
    }

    record WorkflowTimeout(WorkflowEntity workflow, long timeout, CompletableFuture<WorkflowEntity> future) {
    }

    private final Map<String, WorkflowTimeout> workflowMap = new ConcurrentHashMap<>();

    public void add(WorkflowEntity workflow, long timeout, CompletableFuture<WorkflowEntity> future) {
        workflowMap.put(workflow.getId(), new WorkflowTimeout(workflow, timeout, future));
        timer.schedule(timeout, workflow.getId(), this::requestTimeout);
    }

    private void requestTimeout(String workflowId) {
        WorkflowTimeout workflowTimeout = workflowMap.remove(workflowId);
        if (workflowTimeout != null) {
            workflowTimeout.future.complete(workflowTimeout.workflow());
        }
    }

    public void finished(WorkflowEntity workflow) {
        WorkflowTimeout workflowTimeout = workflowMap.remove(workflow.getId());
        if (workflowTimeout != null) {
            workflowTimeout.future.complete(workflow);
        }
    }
}
