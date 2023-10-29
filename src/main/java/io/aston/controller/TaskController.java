package io.aston.controller;

import com.aston.micronaut.sql.where.Multi;
import io.aston.api.TaskApi;
import io.aston.dao.ITaskDao;
import io.aston.model.State;
import io.aston.model.Task;
import io.aston.model.TaskQuery;
import io.aston.user.UserDataException;
import io.micronaut.http.annotation.Controller;

import java.util.List;

@Controller("/v1")
public class TaskController implements TaskApi {

    private final ITaskDao taskDao;

    public TaskController(ITaskDao taskDao) {
        this.taskDao = taskDao;
    }

    @Override
    public List<Task> search(TaskQuery query) {
        return taskDao.selectByQuery(Multi.of(query.getNames()),
                query.getStates() != null ? Multi.of(query.getStates().stream().map(State::name).toList()) : null,
                Multi.of(query.getWorkflowNames()),
                query.getDateFrom(),
                query.getDateTo());
    }

    @Override
    public Task fetch(String id) {
        return taskDao.loadTaskById(id)
                .orElseThrow(() -> new UserDataException("not found"));
    }
}
