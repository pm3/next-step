package io.aston.api;

import io.aston.model.Workflow;
import io.aston.model.WorkflowCreate;
import io.aston.model.WorkflowQuery;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.*;

import java.util.List;

public interface WorkflowApi {

    @Post("/workflows/")
    Workflow create(@Body WorkflowCreate workflowCreate);

    @Get("/workflows/")
    List<Workflow> search(@RequestBean WorkflowQuery query);

    @Get("/workflows/{id}")
    Workflow fetch(@PathVariable String id, @Nullable @QueryValue Boolean includeTasks);
}
