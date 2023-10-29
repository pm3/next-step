package io.aston.api;

import io.aston.model.Task;
import io.aston.model.TaskOutput;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.util.concurrent.CompletableFuture;

public interface RuntimeApi {

    @Operation(tags = {"internal"})
    @Get("/runtime/queues/")
    @ApiResponse(content = @Content(schema = @Schema(ref = "Task")), description = "200 response")
    @ApiResponse(responseCode = "204", description = "204 empty response")
    CompletableFuture<HttpResponse<Task>> queue(
            @QueryValue String taskName,
            @QueryValue String workerId,
            @Nullable @QueryValue Long timeout,
            @Parameter(hidden = true) HttpRequest<?> request);

    @Operation(tags = {"internal"})
    @Put("/runtime/tasks/{id}")
    Task changeTask(@PathVariable String id, @Body TaskOutput taskOutput);
}
