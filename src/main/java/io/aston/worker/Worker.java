package io.aston.worker;

import io.aston.service.InternalEvent;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Worker {
    private final String[] query;
    private final String workerName;
    private final String wid;
    private CompletableFuture<InternalEvent> future;
    private final Map<String, Worker> workerMap;

    public Worker(String[] query, String workerName, String wid, CompletableFuture<InternalEvent> future, Map<String, Worker> workerMap) {
        this.query = query;
        Arrays.sort(this.query);
        this.workerName = workerName;
        this.wid = wid;
        this.future = future;
        this.workerMap = workerMap;
        this.workerMap.put(wid, this);
    }

    public String[] getQuery() {
        return query;
    }

    public String getWorkerName() {
        return workerName;
    }

    public String getWid() {
        return wid;
    }

    public CompletableFuture<InternalEvent> getFuture() {
        return future;
    }

    public synchronized CompletableFuture<InternalEvent> removeFuture() {
        CompletableFuture<InternalEvent> f2 = future;
        this.future = null;
        workerMap.remove(wid);
        return f2;
    }

    public Worker copyAndRemoveFuture() {
        CompletableFuture<InternalEvent> f2 = removeFuture();
        return f2 != null ? new Worker(query, workerName, wid, f2, workerMap) : null;
    }
}
