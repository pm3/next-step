package io.aston.service;

import java.util.concurrent.CompletableFuture;

public class Worker {
    private final String eventName;
    private final String workerName;
    private final String wid;
    private CompletableFuture<IEvent> future;

    public Worker(String eventName, String workerName, String wid, CompletableFuture<IEvent> future) {
        this.eventName = eventName;
        this.workerName = workerName;
        this.wid = wid;
        this.future = future;
    }

    public String getEventName() {
        return eventName;
    }

    public String getWorkerName() {
        return workerName;
    }

    public String getWid() {
        return wid;
    }

    public synchronized CompletableFuture<IEvent> removeFuture() {
        CompletableFuture<IEvent> f2 = future;
        this.future = null;
        return f2;
    }

}
