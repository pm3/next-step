package io.aston.worker;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Worker<T> {
    private final String[] query;
    private final String workerName;
    private final String wid;
    private CompletableFuture<T> future;
    private final Map<String, Worker<?>> workerMap;

    public Worker(String query1, String workerName, String wid, CompletableFuture<T> future, Map<String, Worker<?>> workerMap) {
        this(new String[]{query1}, workerName, wid, future, workerMap);
    }

    public Worker(String[] query, String workerName, String wid, CompletableFuture<T> future, Map<String, Worker<?>> workerMap) {
        this.query = query;
        Arrays.sort(query);
        this.workerName = workerName;
        this.wid = wid;
        this.future = future;
        this.workerMap = workerMap;
        this.workerMap.put(wid, this);
    }

    public String[] getQuery() {
        return query;
    }

    public String getQuery0() {
        return query[0];
    }

    public String getWorkerName() {
        return workerName;
    }

    public String getWid() {
        return wid;
    }

    public CompletableFuture<T> getFuture() {
        return future;
    }

    public synchronized CompletableFuture<T> removeFuture() {
        CompletableFuture<T> f2 = future;
        this.future = null;
        workerMap.remove(wid);
        return f2;
    }

    public Worker<T> copyAndRemoveFuture() {
        CompletableFuture<T> f2 = removeFuture();
        return f2 != null ? new Worker<>(query, workerName, wid, f2, workerMap) : null;
    }
}
