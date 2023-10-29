package io.aston.controller;

import com.aston.micronaut.sql.where.Multi;
import io.aston.api.WorkflowApi;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.MetaTemplateEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.*;
import io.aston.service.MetaCacheService;
import io.aston.service.NextStepService;
import io.aston.user.UserDataException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Controller("/v1")
public class WorkflowController implements WorkflowApi {

    private final IWorkflowDao workflowDao;
    private final ITaskDao taskDao;
    private final NextStepService runtimeService;
    private final MetaCacheService metaCacheService;

    public WorkflowController(IWorkflowDao workflowDao, ITaskDao taskDao, NextStepService runtimeService, MetaCacheService metaCacheService) {
        this.workflowDao = workflowDao;
        this.taskDao = taskDao;
        this.runtimeService = runtimeService;
        this.metaCacheService = metaCacheService;
    }

    @Override
    public Workflow create(WorkflowCreate workflowCreate) {

        Instant now = Instant.now();
        if (workflowCreate.getTasks() == null || workflowCreate.getTasks().isEmpty()) {
            MetaTemplateEntity def;
            if (workflowCreate.getName().contains("/")) {
                def = metaCacheService.loadTemplateByName(workflowCreate.getName())
                        .orElseThrow(() -> new UserDataException("invalid workflow name"));
            } else {
                def = metaCacheService.searchLatestByName(workflowCreate.getName())
                        .orElseThrow(() -> new UserDataException("invalid workflow name"));
            }
            workflowCreate.setName(def.getName());
            workflowCreate.setTasks(def.getData().getTasks());
            if (workflowCreate.getUniqueCode() == null && def.getData().getUniqueCodeExpr() != null) {
                workflowCreate.setUniqueCode(createUniqueCode(def.getData().getUniqueCodeExpr(), now));
            }
        }

        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setId(UUID.randomUUID().toString());
        workflow.setUniqueCode(workflowCreate.getUniqueCode());
        workflow.setWorkflowName(workflowCreate.getName());
        workflow.setCreated(now);
        workflow.setState(State.SCHEDULED);
        workflow.setParams(workflowCreate.getParams() != null ? workflowCreate.getParams() : new HashMap<>());
        List<TaskDef> defTasks = new ArrayList<>();
        int ref = 0;
        for (TaskDef def : workflowCreate.getTasks()) {
            def.setRef(++ref);
            defTasks.add(def);
        }
        workflowDao.insert(workflow);
        metaCacheService.saveWorkflowTasks(workflow.getId(), defTasks);
        runtimeService.nextStep(workflow, null);

        Workflow w2 = new Workflow();
        w2.setId(workflow.getId());
        w2.setUniqueCode(workflow.getUniqueCode());
        w2.setWorkflowName(workflow.getWorkflowName());
        w2.setCreated(workflow.getCreated());
        w2.setState(workflow.getState());
        w2.setParams(workflow.getParams());
        w2.setTasks(new ArrayList<>());
        return w2;
    }

    private String createUniqueCode(String uniqueCodeExpr, Instant now) {
        return uniqueCodeExpr;
    }

    @Override
    public List<Workflow> search(WorkflowQuery query) {
        return workflowDao.selectByQuery(
                Multi.of(query.getWorkflowNames()),
                query.getStates() != null ? Multi.of(query.getStates().stream().map(State::name).toList()) : null,
                query.getDateFrom(),
                query.getDateTo()
        );
    }

    @Override
    public Workflow fetch(String id, @Nullable Boolean includeTasks) {
        Workflow workflow = workflowDao.selectById(id)
                .orElseThrow(() -> new UserDataException("not found"));
        if (includeTasks != null && includeTasks) {
            workflow.setTasks(taskDao.selectByWorkflow(workflow.getId()));
        }
        return workflow;
    }
}
