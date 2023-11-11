package io.aston.worker;

import io.aston.service.InternalEvent;
import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class BaseEventStream {
    protected final SimpleTimer timer;
    private final LinkedTree<String, InternalEvent> eventStream = new LinkedTree<>();
    private final LinkedTree<String[], Worker> workerStream = new LinkedTree<>() {
        @Override
        public boolean cleanFilterFn(String[] key, Worker value) {
            return value.getFuture() == null;
        }
    };

    private static final Logger logger = LoggerFactory.getLogger(BaseEventStream.class);

    public BaseEventStream(SimpleTimer timer) {
        this.timer = timer;
    }

    public void add(InternalEvent event) {
        logger.debug("add {}", event);
        Worker worker = nextWorker(event.name());
        if (worker != null) {
            sendEvent(worker.removeFuture(), event);
            return;
        }
        eventStream.add(event.name(), event);
    }

    public void workerCall(Worker worker, long timeout) {
        InternalEvent event = nextEvent(worker.getQuery(), worker.getWorkerName());
        if (event != null) {
            sendEvent(worker.removeFuture(), event);
        } else {
            timeoutWorkerResponse(worker, timeout);
            workerStream.add(worker.getQuery(), worker);
        }
    }

    private Worker nextWorker(String eventName) {
        return workerStream.filterAndSearch((k, v) -> whereWorker(k, v, eventName));
    }

    private Worker whereWorker(String[] query, Worker worker, String eventName) {
        return Arrays.binarySearch(query, eventName) >= 0 ? worker.copyAndRemoveFuture() : null;
    }

    private InternalEvent nextEvent(String[] query, String workerId) {
        return eventStream.filterAndSearch((k, v) -> whereEvent(k, v, query, workerId));
    }

    private InternalEvent whereEvent(String eventName, InternalEvent event, String[] query, String workerId) {
        return Arrays.binarySearch(query, eventName) >= 0 && callRunningState(event, workerId)
                ? event : null;
    }

    private void sendEvent(CompletableFuture<InternalEvent> future, @Nullable InternalEvent event) {
        if (event != null) {
            logger.debug("event sent to worker {}", event);
            future.complete(event);
            if (event.timeout() > 0) {
                timer.schedule(event.timeout(), event, this::callRunningExpireState);
            }
        } else {
            future.complete(null);
        }
    }

    private void timeoutWorkerResponse(Worker worker, long timeout) {
        timer.schedule(timeout, () -> {
            CompletableFuture<InternalEvent> future = worker.removeFuture();
            if (future != null) sendEvent(future, null);
        });
    }

    protected boolean callRunningState(InternalEvent event, String workerId) {
        return true;
    }

    protected void callRunningExpireState(InternalEvent event) {
    }
}
