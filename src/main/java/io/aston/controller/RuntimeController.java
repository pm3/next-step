package io.aston.controller;

import io.aston.api.RuntimeApi;
import io.aston.entity.TaskEntity;
import io.aston.model.Task;
import io.aston.model.TaskFinish;
import io.aston.service.TaskEventStream;
import io.aston.service.TaskFinishEventQueue;
import io.aston.user.UserDataException;
import io.aston.worker.Worker;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Controller("/v1")
public class RuntimeController implements RuntimeApi, ApplicationEventListener<HttpRequestTerminatedEvent> {

    private final TaskEventStream taskEventStream;
    private final TaskFinishEventQueue taskFinishEventQueue;
    private final ConcurrentHashMap<String, Worker<?>> workerMap = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(RuntimeController.class);

    public RuntimeController(TaskEventStream taskEventStream, TaskFinishEventQueue taskFinishEventQueue) {
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

    @Override
    public List<String> statFinished() {
        return taskFinishEventQueue.stat();
    }
}
