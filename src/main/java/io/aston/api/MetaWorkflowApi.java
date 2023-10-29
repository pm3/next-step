package io.aston.api;

import io.aston.model.WorkflowDef;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.*;

import java.util.List;


public interface MetaWorkflowApi {

    @Post("/meta/workflows/")
    WorkflowDef create(@Body WorkflowDef workflowDef);

    @Get("/meta/workflows/")
    List<WorkflowDef> search(@Nullable @QueryValue Boolean latest, @Nullable @QueryValue String name);

    @Get("/meta/workflows/{name}")
    WorkflowDef fetch(@PathVariable String name);

    @Delete("/meta/workflows/{name}")
    WorkflowDef delete(@PathVariable String name);
}
