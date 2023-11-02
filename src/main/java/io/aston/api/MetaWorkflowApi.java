package io.aston.api;

import io.aston.model.TaskDef;
import io.aston.model.WorkflowTemplate;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.*;

import java.util.List;


public interface MetaWorkflowApi {

    @Post("/meta/template/")
    WorkflowTemplate create(@Body WorkflowTemplate workflowDef);

    @Get("/meta/template/")
    List<WorkflowTemplate> search(@Nullable @QueryValue Boolean latest, @Nullable @QueryValue String name);

    @Get("/meta/template/{name}")
    WorkflowTemplate fetch(@PathVariable String name);

    @Delete("/meta/workflows/{name}")
    WorkflowTemplate delete(@PathVariable String name);

    @Get("/meta/workflow=tasks/{id}")
    List<TaskDef> fetchWorkflowTasks(@PathVariable String id);
}
