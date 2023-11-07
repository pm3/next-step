package io.aston.controller;

import com.aston.micronaut.sql.where.Multi;
import io.aston.api.WorkflowApi;
import io.aston.dao.IMetaDao;
import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.CronTemplateEntity;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.*;
import io.aston.service.TaskEventStream;
import io.aston.user.UserDataException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller("/v1")
public class WorkflowController implements WorkflowApi {

    private final IWorkflowDao workflowDao;
    private final ITaskDao taskDao;
    private final IMetaDao metaDao;
    private final TaskEventStream taskEventStream;

    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);

    public WorkflowController(IWorkflowDao workflowDao, ITaskDao taskDao, IMetaDao metaDao, TaskEventStream taskEventStream) {
        this.workflowDao = workflowDao;
        this.taskDao = taskDao;
        this.metaDao = metaDao;
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
    public Workflow createWorkflow(WorkflowCreate workflowCreate) {

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
        TaskEntity task = createWorkflowTask(workflow);
        taskEventStream.add(task, task.getTaskName());
        return toWorkflow(workflow);
    }

    private static TaskEntity createWorkflowTask(WorkflowEntity workflow) {
        TaskEntity task = new TaskEntity();
        task.setId(workflow.getId());
        task.setWorkflowId(workflow.getId());
        task.setWorkflowName(workflow.getWorkflowName());
        task.setRef(0);
        task.setTaskName("wf:" + workflow.getWorkflowName());
        task.setParams(Map.of(
                "uniqueCode", workflow.getUniqueCode(),
                "params", workflow.getParams()));
        task.setState(State.SCHEDULED);
        task.setCreated(workflow.getCreated());
        task.setRetries(0);
        task.setRunningTimeout(-1);
        return task;
    }

    private String createUniqueCode(String uniqueCodeExpr, Instant now) {
        //TODO createUniqueCode
        return uniqueCodeExpr + now;
    }

    @Override
    public Workflow finishWorkflow(String id, WorkflowFinish workflowFinish) {
        WorkflowEntity workflow = workflowDao.loadById(workflowFinish.getId())
                .orElseThrow(() -> new UserDataException("workflow not found"));

        if (!State.in(workflow.getState(), State.RUNNING, State.SCHEDULED)) {
            throw new UserDataException("finish only running workflow");
        }
        if (!State.in(workflowFinish.getState(), State.FAILED, State.FATAL_ERROR, State.COMPLETED)) {
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
