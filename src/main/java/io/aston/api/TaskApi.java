package io.aston.api;

import io.aston.model.Task;
import io.aston.model.TaskQuery;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.RequestBean;

import java.util.List;

public interface TaskApi {

    @Get("/tasks/")
    List<Task> search(@RequestBean TaskQuery query);

    @Get("/tasks/{id}")
    Task fetch(@PathVariable String id);
}
