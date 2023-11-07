package io.aston.api;

import io.aston.model.WorkflowTemplate;
import io.micronaut.http.annotation.*;

import java.util.List;


public interface MetaWorkflowApi {

    @Post("/meta/template/")
    void create(@Body WorkflowTemplate workflowDef);

    @Get("/meta/template/")
    List<WorkflowTemplate> selectAll();

    @Get("/meta/template/{name}")
    WorkflowTemplate fetch(@PathVariable String name);

    @Delete("/meta/workflows/{name}")
    WorkflowTemplate delete(@PathVariable String name);
}
