package io.aston.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aston.model.State;
import io.aston.model.Task;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Optional;
import java.util.Timer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

@Singleton
public class TaskQueue implements ApplicationEventListener<HttpRequestTerminatedEvent> {

    private final Timer timer;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, BlockingQueue<Task>> taskQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<Worker>> workerQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Worker> workerMap = new ConcurrentHashMap<>();
    private Consumer<Task> changeTaskState = null;
    private static final Logger logger = LoggerFactory.getLogger(TaskQueue.class);

    public TaskQueue(ObjectMapper objectMapper) {
        this.timer = new Timer(true);
        this.objectMapper = objectMapper;
    }

    public void setChangeTaskState(Consumer<Task> changeTaskState) {
        this.changeTaskState = changeTaskState;
    }

    public void addTask(Task task) {
        logger.debug("addTask {}", toJson(task));
        BlockingQueue<Task> taskQueue = taskQueue(task.getTaskName());
        boolean sended = false;
        if (taskQueue.size() == 0) {
            Worker worker = nextWorker(task.getTaskName());
            if (worker != null) {
                sendTask(worker.removeFuture(), task);
                sended = true;
            }
        }
        if (!sended) {
            taskQueue.add(task);
        }
    }

    public void workerCall(Worker worker, long timeout) {
        BlockingQueue<Task> taskQueue = taskQueue(worker.getTaskName());
        BlockingQueue<Worker> workerQueue = workerQueue(worker.getTaskName());
        Task task = nextTask(taskQueue);
        if (task != null) {
            sendTask(worker.removeFuture(), task);
        } else {
            workerMap.put(worker.getWid(), worker);
            timeoutWorkerResponse(worker, timeout);
            workerQueue.add(worker);
        }
    }

    private BlockingQueue<Task> taskQueue(String taskName) {
        return taskQueueMap.computeIfAbsent(taskName, (k) -> new LinkedBlockingQueue<>());
    }

    private BlockingQueue<Worker> workerQueue(String taskName) {
        return workerQueueMap.computeIfAbsent(taskName, (k) -> new LinkedBlockingQueue<>());
    }

    private synchronized Task nextTask(BlockingQueue<Task> taskQueue) {
        while (taskQueue.size() > 0) {
            Task task = taskQueue.poll();
            if (task != null) return task;
        }
        return null;
    }

    private Worker nextWorker(String taskName) {
        BlockingQueue<Worker> workerQueue = workerQueue(taskName);
        while (workerQueue.size() > 0) {
            Worker worker = workerQueue.poll();
            if (worker != null) {
                CompletableFuture<HttpResponse<Task>> future = worker.removeFuture();
                if (future != null)
                    return new Worker(worker.getTaskName(), worker.getWorkerName(), worker.getWid(), future);
            }
        }
        return null;
    }

    public void sendTask(CompletableFuture<HttpResponse<Task>> future, Task task) {
        if (task != null) {
            task.setState(State.RUNNING);
            logger.debug("task sent to worker {}", toJson(task));
            future.complete(HttpResponse.ok(task));
            if (changeTaskState != null) {
                changeTaskState.accept(task);
            }
        } else {
            future.complete(HttpResponse.status(HttpStatus.NO_CONTENT));
        }
    }

    private void timeoutWorkerResponse(Worker worker, long timeout) {
        RunTimerTask.schedule(timer, () -> {
            workerMap.remove(worker.getWid());
            CompletableFuture<HttpResponse<Task>> future = worker.removeFuture();
            if (future != null) sendTask(future, null);
        }, new Date(System.currentTimeMillis() + timeout));
    }

    @Override
    public void onApplicationEvent(HttpRequestTerminatedEvent event) {
        Optional<Object> wid = event.getSource().getAttribute("wid");
        if (wid.isPresent()) {
            Worker worker = workerMap.remove((String) wid.get());
            if (worker != null) worker.removeFuture();
        }
    }

    String toJson(Object val) {
        try {
            return objectMapper.writeValueAsString(val);
        } catch (Exception e) {
            return val.toString() + " " + e.getMessage();
        }
    }
}
