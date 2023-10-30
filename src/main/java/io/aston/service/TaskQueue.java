package io.aston.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aston.entity.TaskEntity;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

@Singleton
public class TaskQueue implements ApplicationEventListener<HttpRequestTerminatedEvent> {

    private final Timer timer;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, BlockingQueue<TaskEntity>> taskQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<Worker>> workerQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Worker> workerMap = new ConcurrentHashMap<>();
    private Function<TaskEntity, Boolean> nextStepRunning = null;
    private static final Logger logger = LoggerFactory.getLogger(TaskQueue.class);

    public TaskQueue(ObjectMapper objectMapper) {
        this.timer = new Timer(true);
        this.objectMapper = objectMapper;
    }

    public void setNextStepRunning(Function<TaskEntity, Boolean> nextStepRunning) {
        this.nextStepRunning = nextStepRunning;
    }

    public void addTask(TaskEntity task) {
        logger.debug("addTask {}", toJson(task));
        BlockingQueue<TaskEntity> taskQueue = taskQueue(task.getTaskName());
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
        BlockingQueue<TaskEntity> taskQueue = taskQueue(worker.getTaskName());
        BlockingQueue<Worker> workerQueue = workerQueue(worker.getTaskName());
        TaskEntity task = nextTask(taskQueue);
        if (task != null) {
            sendTask(worker.removeFuture(), task);
        } else {
            workerMap.put(worker.getWid(), worker);
            timeoutWorkerResponse(worker, timeout);
            workerQueue.add(worker);
        }
    }

    public void schedule(Date time, Runnable r) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    r.run();
                } catch (Throwable thr) {
                    String m = thr.getMessage();
                    try {
                        StackTraceElement[] arr = thr.getStackTrace();
                        if (arr != null && arr.length > 0) m = m + " " + arr[0].toString();
                    } catch (Exception ignore) {
                    }
                    logger.warn("timer task error " + m);
                }
            }
        }, time);
    }

    private BlockingQueue<TaskEntity> taskQueue(String taskName) {
        return taskQueueMap.computeIfAbsent(taskName, (k) -> new LinkedBlockingQueue<>());
    }

    private BlockingQueue<Worker> workerQueue(String taskName) {
        return workerQueueMap.computeIfAbsent(taskName, (k) -> new LinkedBlockingQueue<>());
    }

    private synchronized TaskEntity nextTask(BlockingQueue<TaskEntity> taskQueue) {
        while (taskQueue.size() > 0) {
            TaskEntity task = taskQueue.poll();
            if (task != null) return task;
        }
        return null;
    }

    private Worker nextWorker(String taskName) {
        BlockingQueue<Worker> workerQueue = workerQueue(taskName);
        while (workerQueue.size() > 0) {
            Worker worker = workerQueue.poll();
            if (worker != null) {
                CompletableFuture<TaskEntity> future = worker.removeFuture();
                if (future != null)
                    return new Worker(worker.getTaskName(), worker.getWorkerName(), worker.getWid(), future);
            }
        }
        return null;
    }

    public void sendTask(CompletableFuture<TaskEntity> future, TaskEntity task) {
        if (task != null && nextStepRunning != null) {
            if (!nextStepRunning.apply(task)) {
                task = null;
            }
        }
        if (task != null) {
            logger.debug("task sent to worker {}", toJson(task));
            future.complete(task);
        } else {
            future.complete(null);
        }
    }

    private void timeoutWorkerResponse(Worker worker, long timeout) {
        schedule(new Date(System.currentTimeMillis() + timeout), () -> {
            workerMap.remove(worker.getWid());
            CompletableFuture<TaskEntity> future = worker.removeFuture();
            if (future != null) sendTask(future, null);
        });
    }

    @Override
    public void onApplicationEvent(HttpRequestTerminatedEvent event) {
        Optional<Object> wid = event.getSource().getAttribute("wid");
        if (wid.isPresent()) {
            Worker worker = workerMap.remove((String) wid.get());
            if (worker != null) worker.removeFuture();
        }
    }

    private String toJson(Object val) {
        try {
            return objectMapper.writeValueAsString(val);
        } catch (Exception e) {
            return val.toString() + " " + e.getMessage();
        }
    }
}
