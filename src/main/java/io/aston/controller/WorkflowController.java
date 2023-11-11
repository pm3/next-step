package io.aston.controller;

import com.aston.micronaut.sql.where.Multi;
import io.aston.api.WorkflowApi;
import io.aston.dao.IMetaDao;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.CronTemplateEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.*;
import io.aston.service.AllEventStream;
import io.aston.service.InternalEvent;
import io.aston.service.WorkerFinishWait;
import io.aston.user.UserDataException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.QueryValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Controller("/v1")
public class WorkflowController implements WorkflowApi {

    private final IWorkflowDao workflowDao;
    private final ITaskDao taskDao;
    private final IMetaDao metaDao;
    private final AllEventStream eventStream;
    private final WorkerFinishWait workerFinishWait;

    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);

    public WorkflowController(IWorkflowDao workflowDao, ITaskDao taskDao, IMetaDao metaDao, AllEventStream eventStream, WorkerFinishWait workerFinishWait) {
        this.workflowDao = workflowDao;
        this.taskDao = taskDao;
        this.metaDao = metaDao;
        this.eventStream = eventStream;
        this.workerFinishWait = workerFinishWait;
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
    public CompletableFuture<Workflow> createWorkflow(WorkflowCreate workflowCreate, @Nullable @QueryValue Integer timeout) {

        Instant now = Instant.now();
        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setId(UUID.randomUUID().toString());
        workflow.setUniqueCode(workflowCreate.getUniqueCode());
        workflow.setWorkflowName(workflowCreate.getName());
        workflow.setCreated(now);
        workflow.setState(State.SCHEDULED);
        workflow.setParams(workflowCreate.getParams() != null ? workflowCreate.getParams() : new HashMap<>());
        if (workflow.getUniqueCode() == null) {
            CronTemplateEntity template = metaDao.loadById(workflow.getWorkflowName()).orElse(null);
            if (template != null) {
                workflow.setUniqueCode(createUniqueCode(template.getUniqueCodeExpr(), workflow.getCreated()));
            } else {
                workflow.setUniqueCode(workflow.getWorkflowName() + DateTimeFormatter.ISO_INSTANT.format(workflow.getCreated()));
            }
        }
        workflowDao.insert(workflow);
        eventStream.add(new InternalEvent(EventType.NEW_WORKFLOW, "wf_" + workflow.getWorkflowName(), workflow, null, -1L));
        if (timeout != null && timeout > 0 && timeout <= 55) {
            CompletableFuture<WorkflowEntity> future = new CompletableFuture<>();
            workerFinishWait.add(workflow, timeout * 1000L, future);
            return future.thenApply(this::toWorkflow);
        }
        return CompletableFuture.completedFuture(toWorkflow(workflow));
    }

    private String createUniqueCode(String uniqueCodeExpr, Instant now) {
        //TODO createUniqueCode
        return uniqueCodeExpr + now;
    }

    @Override
    public Workflow finishWorkflow(String id, WorkflowFinish workflowFinish) {
        WorkflowEntity workflow = workflowDao.loadById(id)
                .orElseThrow(() -> new UserDataException("workflow not found"));

        if (workflow.getState() == workflowFinish.getState()) {
            return toWorkflow(workflow);
        }

        if (!State.in(workflow.getState(), State.RUNNING, State.SCHEDULED)) {
            throw new UserDataException("finish only running workflow");
        }
        if (!State.in(workflowFinish.getState(), State.FAILED, State.COMPLETED)) {
            throw new UserDataException("change to only closed state");
        }
        workflow.setOutput(workflowFinish.getOutput());
        workflow.setState(workflowFinish.getState());
        workflow.setModified(Instant.now());
        workflowDao.update(workflow);
        taskDao.updateWorkflowAll(workflow.getId(),
                Multi.of(List.of(State.SCHEDULED, State.RUNNING)),
                State.FAILED,
                "workflow finish " + workflow.getState().name(),
                workflow.getModified());
        workerFinishWait.finished(workflow);
        return toWorkflow(workflow);
    }

    private Workflow toWorkflow(WorkflowEntity workflow) {
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

}
