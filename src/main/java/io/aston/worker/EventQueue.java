package io.aston.worker;

import jakarta.inject.Singleton;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Singleton
public class EventQueue<T> extends ABaseEvent<T, String> {

    protected final ConcurrentHashMap<String, BlockingQueue<T>> eventQueueMap = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<String, BlockingQueue<Worker<T>>> workerQueueMap = new ConcurrentHashMap<>();

    public EventQueue(SimpleTimer timer) {
        super(timer);
    }

    private BlockingQueue<T> eventQueue(String eventName) {
        return eventQueueMap.computeIfAbsent(eventName, (k) -> new LinkedBlockingQueue<>());
    }

    @Override
    protected void addEvent(String eventName, T event) {
        BlockingQueue<T> eventQueue = eventQueue(eventName);
        eventQueue.add(event);
    }

    @Override
    protected T nextEvent(String query) {
        BlockingQueue<T> eventQueue = eventQueue(query);
        while (!eventQueue.isEmpty()) {
            T event = eventQueue.poll();
            if (event != null && callRunningState(event)) {
                return event;
            }
        }
        return null;
    }

    private BlockingQueue<Worker<T>> workerQueue(String eventName) {
        return workerQueueMap.computeIfAbsent(eventName, (k) -> new LinkedBlockingQueue<>());
    }

    @Override
    protected void addWorker(String query, Worker<T> worker) {
        BlockingQueue<Worker<T>> workerQueue = workerQueue(query);
        workerQueue.add(worker);
    }

    @Override
    protected Worker<T> nextWorker(String eventName) {
        BlockingQueue<Worker<T>> workerQueue = workerQueue(eventName);
        while (!workerQueue.isEmpty()) {
            Worker<T> worker = workerQueue.poll();
            if (worker != null) {
                return worker.copyAndRemoveFuture();
            }
        }
        return null;
    }
}
