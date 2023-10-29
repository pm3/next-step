package io.aston.service;

import io.aston.model.Task;
import io.micronaut.http.HttpResponse;

import java.util.concurrent.CompletableFuture;

public class Worker {
    private final String taskName;
    private final String workerName;
    private final String wid;
    private CompletableFuture<HttpResponse<Task>> future;

    public Worker(String taskName, String workerName, String wid, CompletableFuture<HttpResponse<Task>> future) {
        this.taskName = taskName;
        this.workerName = workerName;
        this.wid = wid;
        this.future = future;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getWorkerName() {
        return workerName;
    }

    public String getWid() {
        return wid;
    }

    public synchronized CompletableFuture<HttpResponse<Task>> removeFuture() {
        CompletableFuture<HttpResponse<Task>> f2 = future;
        this.future = null;
        return f2;
    }

}
