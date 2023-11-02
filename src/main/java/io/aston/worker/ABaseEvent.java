package io.aston.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public abstract class ABaseEvent<T, Q> {
    protected final SimpleTimer timer;
    private static final Logger logger = LoggerFactory.getLogger(ABaseEvent.class);

    public ABaseEvent(SimpleTimer timer) {
        this.timer = timer;
    }

    public void add(T event, String eventName) {
        logger.debug("add {}", event);
        Worker<T> worker = nextWorker(eventName);
        if (worker != null) {
            sendEvent(worker.removeFuture(), event);
            return;
        }
        addEvent(eventName, event);
    }

    public void workerCall(Worker<T> worker, Q query, long timeout) {
        T event = nextEvent(query);
        if (event != null) {
            sendEvent(worker.removeFuture(), event);
        } else {
            timeoutWorkerResponse(worker, timeout);
            addWorker(query, worker);
        }
    }

    protected abstract void addWorker(Q query, Worker<T> worker);

    protected abstract Worker<T> nextWorker(String eventName);

    protected abstract void addEvent(String eventName, T event);

    protected abstract T nextEvent(Q query);

    protected long eventRunTimeout(T event) {
        return -1;
    }

    public void sendEvent(CompletableFuture<T> future, T event) {
        if (event != null) {
            logger.debug("event sent to worker {}", event);
            future.complete(event);
            long runTimeout = eventRunTimeout(event);
            if (runTimeout > 0) {
                timer.schedule(runTimeout, event, this::callRunningExpireState);
            }
        } else {
            future.complete(null);
        }
    }

    private void timeoutWorkerResponse(Worker<T> worker, long timeout) {
        timer.schedule(timeout, () -> {
            CompletableFuture<T> future = worker.removeFuture();
            if (future != null) sendEvent(future, null);
        });
    }

    protected boolean callRunningState(T event) {
        return true;
    }

    protected void callRunningExpireState(T event) {
    }
}
