package io.aston.worker;

import java.util.Arrays;
import java.util.List;

public class EventStream<T> extends ABaseEvent<T, String[]> {

    private final LinkedStream<String, T> eventStream = new LinkedStream<>();
    private final LinkedStream<String[], Worker<T>> workerStream = new LinkedStream<>() {
        @Override
        public boolean cleanFilterFn(String[] key, Worker<T> value) {
            return value.getFuture() == null;
        }
    };

    public EventStream(SimpleTimer timer) {
        super(timer);
    }

    @Override
    protected void addWorker(String[] query, Worker<T> worker) {
        workerStream.add(query, worker);
    }

    @Override
    protected Worker<T> nextWorker(String eventName) {
        return workerStream.filterAndSearch((k, v) -> whereWorker(k, v, eventName));
    }

    protected Worker<T> whereWorker(String[] query, Worker<T> worker, String eventName) {
        return Arrays.binarySearch(query, eventName) >= 0 ? worker.copyAndRemoveFuture() : null;
    }

    @Override
    protected void addEvent(String eventName, T event) {
        eventStream.add(eventName, event);
    }

    @Override
    protected T nextEvent(String[] query) {
        return eventStream.filterAndSearch((k, v) -> whereEvent(k, v, query));
    }

    protected T whereEvent(String eventName, T event, String[] query) {
        return Arrays.binarySearch(query, eventName) >= 0 && callRunningState(event)
                ? event : null;
    }

    public List<T> values() {
        return eventStream.values();
    }
}
