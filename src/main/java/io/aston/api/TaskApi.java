package io.aston.api;

import io.aston.model.Task;
import io.aston.model.TaskCreate;
import io.aston.model.TaskQuery;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;

public interface TaskApi {

    @Operation(tags = {"tasks"})
    @Get("/tasks/")
    List<Task> search(@RequestBean TaskQuery query);

    @Operation(tags = {"tasks"})
    @Get("/tasks/{id}")
    Task fetch(@PathVariable String id);

    @Operation(tags = {"tasks"})
    @Post("/tasks/")
    Task createTask(@Body TaskCreate taskCreate);

    @Operation(tags = {"tasks"})
    @Put("/tasks/{id}")
    Task changeTask(@PathVariable String id, @Body Task taskOutput);
}
