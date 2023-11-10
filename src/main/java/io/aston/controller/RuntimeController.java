package io.aston.controller;

import io.aston.api.RuntimeApi;
import io.aston.model.Event;
import io.aston.model.EventStat;
import io.aston.service.AllEventStream;
import io.aston.service.InternalEvent;
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

    private final AllEventStream eventStream;
    private final ConcurrentHashMap<String, Worker> workerMap = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(RuntimeController.class);

    public RuntimeController(AllEventStream eventStream) {
        this.eventStream = eventStream;
    }

    @Override
    public CompletableFuture<HttpResponse<Event>> queue(List<String> q, String workerId, Long timeout, HttpRequest<?> request) {
        String wid = UUID.randomUUID().toString();
        request.setAttribute("wid", wid);
        CompletableFuture<InternalEvent> future = new CompletableFuture<>();

        if (q == null || q.isEmpty()) throw new UserDataException("workflowName required");
        String[] arrQuery = q.toArray(new String[0]);
        Worker worker = new Worker(arrQuery, workerId, wid, future, workerMap);
        if (timeout == null || timeout < 0 || timeout > 45) timeout = 30L;
        eventStream.workerCall(worker, timeout * 1000L);
        return future.thenApply((e) -> e != null ? HttpResponse.ok(eventStream.toEvent(e)) : HttpResponse.noContent());
    }

    @Override
    public void onApplicationEvent(HttpRequestTerminatedEvent event) {
        Optional<Object> wid = event.getSource().getAttribute("wid");
        if (wid.isPresent()) {
            Worker worker = workerMap.remove((String) wid.get());
            if (worker != null) worker.removeFuture();
        }
    }

    @Override
    public List<EventStat> statEvents() {
        return eventStream.stat();
    }
}
