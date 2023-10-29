package io.aston.controller;

import com.aston.micronaut.sql.where.Multi;
import io.aston.api.WorkflowApi;
import io.aston.dao.IMetaDao;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.WorkflowDefEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.*;
import io.aston.service.NextStepService;
import io.aston.user.UserDataException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;

import java.time.Instant;
import java.util.*;

@Controller("/v1")
public class WorkflowController implements WorkflowApi {

    private final IMetaDao metaDao;
    private final IWorkflowDao workflowDao;
    private final ITaskDao taskDao;

    private final NextStepService runtimeService;

    public WorkflowController(IMetaDao metaDao, IWorkflowDao workflowDao, ITaskDao taskDao, NextStepService runtimeService) {
        this.metaDao = metaDao;
        this.workflowDao = workflowDao;
        this.taskDao = taskDao;
        this.runtimeService = runtimeService;
    }

    @Override
    public Workflow create(WorkflowCreate workflowCreate) {

        Instant now = Instant.now();
        if (workflowCreate.getTasks() == null || workflowCreate.getTasks().isEmpty()) {
            WorkflowDefEntity def;
            if (workflowCreate.getName().contains("/")) {
                def = metaDao.loadById(workflowCreate.getName())
                        .orElseThrow(() -> new UserDataException("invalid workflow name"));
            } else {
                def = metaDao.searchLatestByName(workflowCreate.getName())
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
        workflow.setScope(workflow.getParams());
        Map<Integer, TaskDef> defMap = new HashMap<>();
        int ref = 0;
        for (TaskDef def : workflowCreate.getTasks()) {
            def.setRef(++ref);
            defMap.put(ref, def);
        }
        workflow.setDefTasks(defMap);
        workflowDao.insert(workflow);
        runtimeService.nextStep(workflow, null);

        Workflow w2 = new Workflow();
        w2.setId(workflow.getId());
        w2.setUniqueCode(workflow.getUniqueCode());
        w2.setWorkflowName(workflow.getWorkflowName());
        w2.setCreated(workflow.getCreated());
        w2.setState(workflow.getState());
        w2.setParams(workflow.getParams());
        w2.setScope(workflow.getScope());
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
