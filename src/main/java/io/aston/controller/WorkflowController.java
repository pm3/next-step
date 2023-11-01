package io.aston.controller;

import com.aston.micronaut.sql.where.Multi;
import io.aston.api.WorkflowApi;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.MetaTemplateEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.*;
import io.aston.service.MetaCacheService;
import io.aston.service.NewWorkflowEventStream;
import io.aston.service.NextStepService;
import io.aston.user.UserDataException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Controller("/v1")
public class WorkflowController implements WorkflowApi {

    public static final String LOCAL_WORKER = "#local";
    private final IWorkflowDao workflowDao;
    private final ITaskDao taskDao;
    private final NewWorkflowEventStream newWorkflowEventStream;
    private final NextStepService runtimeService;
    private final MetaCacheService metaCacheService;

    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);

    public WorkflowController(IWorkflowDao workflowDao, ITaskDao taskDao, NewWorkflowEventStream newWorkflowEventStream, NextStepService runtimeService, MetaCacheService metaCacheService) {
        this.workflowDao = workflowDao;
        this.taskDao = taskDao;
        this.newWorkflowEventStream = newWorkflowEventStream;
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
                        .orElse(null);
            } else {
                def = metaCacheService.searchLatestByName(workflowCreate.getName())
                        .orElse(null);
            }
            if (def != null) {
                workflowCreate.setName(def.getName());
                workflowCreate.setTasks(def.getData().getTasks());
            }
            if (def != null && workflowCreate.getUniqueCode() == null && def.getData().getUniqueCodeExpr() != null) {
                workflowCreate.setUniqueCode(createUniqueCode(def.getData().getUniqueCodeExpr(), now));
            }
        }
        if (workflowCreate.getTasks() == null) workflowCreate.setTasks(new ArrayList<>());

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
        if (!defTasks.isEmpty()) {
            workflow.setWorker(LOCAL_WORKER);
        }
        workflowDao.insert(workflow);

        if (LOCAL_WORKER.equals(workflow.getWorker())) {
            //local flow
            metaCacheService.saveWorkflowTasks(workflow.getId(), defTasks);
            runtimeService.nextStep(workflow, null);
        } else {
            //externalFlow
            newWorkflowEventStream.add(workflow, workflow.getWorkflowName());
        }
        return newWorkflowEventStream.toWorkflow(workflow);
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
