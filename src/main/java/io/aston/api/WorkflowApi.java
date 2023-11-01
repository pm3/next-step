package io.aston.api;

import io.aston.model.Workflow;
import io.aston.model.WorkflowCreate;
import io.aston.model.WorkflowQuery;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;

public interface WorkflowApi {

    @Operation(tags = {"workflow"})
    @Get("/workflows/")
    List<Workflow> search(@RequestBean WorkflowQuery query);

    @Operation(tags = {"workflow"})
    @Get("/workflows/{id}")
    Workflow fetch(@PathVariable String id, @Nullable @QueryValue Boolean includeTasks);

    @Operation(tags = {"workflow"})
    @Post("/workflows/")
    Workflow create(@Body WorkflowCreate workflowCreate);

    @Operation(tags = {"workflow"})
    @Put("/workflows/{id}")
    Workflow finish(@PathVariable String id, @Body Workflow workflowOutput);
}
