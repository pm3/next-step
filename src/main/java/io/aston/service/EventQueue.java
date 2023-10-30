package io.aston.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class EventQueue implements ApplicationEventListener<HttpRequestTerminatedEvent> {

    private final Timer timer;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, BlockingQueue<IEvent>> eventQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<Worker>> workerQueueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Worker> workerMap = new ConcurrentHashMap<>();
    private Function<IEvent, Boolean> nextStepRunning = null;
    private static final Logger logger = LoggerFactory.getLogger(EventQueue.class);

    public EventQueue(ObjectMapper objectMapper) {
        this.timer = new Timer(true);
        this.objectMapper = objectMapper;
    }

    public void setNextStepRunning(Function<IEvent, Boolean> nextStepRunning) {
        this.nextStepRunning = nextStepRunning;
    }

    public void addEvent(IEvent event) {
        logger.debug("addEvent {}", toJson(event));
        BlockingQueue<IEvent> eventQueue = eventQueue(event.getName());
        boolean sent = false;
        if (eventQueue.size() == 0) {
            Worker worker = nextEventWorker(event.getName());
            if (worker != null) {
                sendEvent(worker.removeFuture(), event);
                sent = true;
            }
        }
        if (!sent) {
            eventQueue.add(event);
        }
    }

    public void workerCall(Worker worker, long timeout) {
        BlockingQueue<IEvent> eventQueue = eventQueue(worker.getEventName());
        BlockingQueue<Worker> workerQueue = workerQueue(worker.getEventName());
        IEvent event = nextEvent(eventQueue);
        if (event != null) {
            sendEvent(worker.removeFuture(), event);
        } else {
            workerMap.put(worker.getWid(), worker);
            timeoutEventWorkerResponse(worker, timeout);
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
                    logger.warn("timer event error " + m);
                }
            }
        }, time);
    }

    private BlockingQueue<IEvent> eventQueue(String eventName) {
        return eventQueueMap.computeIfAbsent(eventName, (k) -> new LinkedBlockingQueue<>());
    }

    private BlockingQueue<Worker> workerQueue(String eventName) {
        return workerQueueMap.computeIfAbsent(eventName, (k) -> new LinkedBlockingQueue<>());
    }

    private synchronized IEvent nextEvent(BlockingQueue<IEvent> eventQueue) {
        while (eventQueue.size() > 0) {
            IEvent event = eventQueue.poll();
            if (event != null) return event;
        }
        return null;
    }

    private Worker nextEventWorker(String eventName) {
        BlockingQueue<Worker> workerQueue = workerQueue(eventName);
        while (workerQueue.size() > 0) {
            Worker worker = workerQueue.poll();
            if (worker != null) {
                CompletableFuture<IEvent> future = worker.removeFuture();
                if (future != null)
                    return new Worker(worker.getEventName(), worker.getWorkerName(), worker.getWid(), future);
            }
        }
        return null;
    }

    public void sendEvent(CompletableFuture<IEvent> future, IEvent event) {
        if (event != null && nextStepRunning != null) {
            if (!nextStepRunning.apply(event)) {
                event = null;
            }
        }
        if (event != null) {
            logger.debug("event sent to worker {}", toJson(event));
            future.complete(event);
        } else {
            future.complete(null);
        }
    }

    private void timeoutEventWorkerResponse(Worker worker, long timeout) {
        schedule(new Date(System.currentTimeMillis() + timeout), () -> {
            workerMap.remove(worker.getWid());
            CompletableFuture<IEvent> future = worker.removeFuture();
            if (future != null) sendEvent(future, null);
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
