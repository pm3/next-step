package io.aston.api;

import io.aston.model.Event;
import io.aston.model.EventStat;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface RuntimeApi {

    @Operation(tags = {"runtime"})
    @Get("/runtime/queues/events")
    @ApiResponse(content = @Content(schema = @Schema(ref = "Task")), description = "200 response")
    @ApiResponse(responseCode = "204", description = "204 empty response")
    CompletableFuture<HttpResponse<Event>> queue(
            @QueryValue List<String> q,
            @QueryValue String workerId,
            @Nullable @QueryValue Long timeout,
            @Parameter(hidden = true) HttpRequest<?> request);

    @Operation(tags = {"runtime"})
    @Get("/runtime/stat/events")
    List<EventStat> statEvents();
}
