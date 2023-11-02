package io.aston.controller;

import com.aston.micronaut.sql.where.Multi;
import io.aston.api.WorkflowApi;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.MetaTemplateEntity;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.*;
import io.aston.service.MetaCacheService;
import io.aston.service.NextStepService;
import io.aston.service.TaskEventStream;
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
    private final NextStepService runtimeService;
    private final MetaCacheService metaCacheService;
    private final TaskEventStream taskEventStream;

    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);

    public WorkflowController(IWorkflowDao workflowDao, ITaskDao taskDao, NextStepService runtimeService, MetaCacheService metaCacheService, TaskEventStream taskEventStream) {
        this.workflowDao = workflowDao;
        this.taskDao = taskDao;
        this.runtimeService = runtimeService;
        this.metaCacheService = metaCacheService;
        this.taskEventStream = taskEventStream;
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
            workflow.setWorkerId(LOCAL_WORKER);
        }
        workflowDao.insert(workflow);

        if (LOCAL_WORKER.equals(workflow.getWorkerId())) {
            //local flow
            metaCacheService.saveWorkflowTasks(workflow.getId(), defTasks);
            runtimeService.nextStep(workflow, null);
        } else {
            //externalFlow
            TaskEntity task = new TaskEntity();
            task.setId(workflow.getId());
            task.setWorkflowId(workflow.getId());
            task.setRef(0);
            task.setTaskName("wf:" + workflow.getWorkflowName());
            task.setWorkflowName(workflow.getWorkflowName());
            task.setParams(workflow.getParams());
            task.setState(State.SCHEDULED);
            task.setCreated(workflow.getCreated());
            task.setRetries(0);
            task.setRunningTimeout(60L);
            taskEventStream.add(task, task.getTaskName());
        }
        return toWorkflow(workflow);
    }

    private String createUniqueCode(String uniqueCodeExpr, Instant now) {
        return uniqueCodeExpr;
    }

    public Workflow toWorkflow(WorkflowEntity workflow) {
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

    @Override
    public Workflow finish(String id, Workflow workflowOutput) {
        WorkflowEntity workflow = workflowDao.loadById(workflowOutput.getId())
                .orElseThrow(() -> new UserDataException("workflow not found"));

        if (!State.in(workflow.getState(), State.RUNNING, State.SCHEDULED)) {
            throw new UserDataException("finish only open workflow");
        }
        if (!State.in(workflowOutput.getState(), State.FAILED, State.FATAL_ERROR, State.COMPLETED)) {
            throw new UserDataException("change to only closed state");
        }

        workflow.setOutput(workflowOutput.getOutput());
        workflow.setState(workflowOutput.getState());
        workflow.setModified(Instant.now());
        workflowDao.update(workflow);

        return toWorkflow(workflow);
    }
}
