package io.aston.api;

import io.aston.model.Workflow;
import io.aston.model.WorkflowCreate;
import io.aston.model.WorkflowFinish;
import io.aston.model.WorkflowQuery;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface WorkflowApi {

    @Operation(tags = {"workflow"})
    @Get("/workflows/")
    List<Workflow> search(@RequestBean WorkflowQuery query);

    @Operation(tags = {"workflow"})
    @Get("/workflows/{id}")
    Workflow fetch(@PathVariable String id, @Nullable @QueryValue Boolean includeTasks);

    @Operation(tags = {"workflow"})
    @Post("/workflows/")
    CompletableFuture<Workflow> createWorkflow(@Body WorkflowCreate workflowCreate, @Nullable @QueryValue Integer timeout);

    @Operation(tags = {"workflow"})
    @Put("/workflows/{id}")
    Workflow finishWorkflow(@PathVariable String id, @Body WorkflowFinish workflowFinish);
}
