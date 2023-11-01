package io.aston.service;

import io.aston.dao.IWorkflowDao;
import io.aston.entity.WorkflowEntity;
import io.aston.model.State;
import io.aston.model.Workflow;
import io.aston.worker.EventStream;
import io.aston.worker.SimpleTimer;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

@Singleton
public class NewWorkflowEventStream extends EventStream<WorkflowEntity> {

    private final IWorkflowDao workflowDao;

    private static final Logger logger = LoggerFactory.getLogger(NewWorkflowEventStream.class);

    public NewWorkflowEventStream(SimpleTimer timer, IWorkflowDao workflowDao) {
        super(timer);
        this.workflowDao = workflowDao;
    }

    @Override
    protected long eventRunTimeout(WorkflowEntity workflow) {
        return 60_000;
    }

    @Override
    protected void callRunningExpireState(WorkflowEntity workflow0) {
        WorkflowEntity workflow = workflowDao.loadById(workflow0.getId()).orElse(null);
        if (workflow != null && workflow.getState() == State.SCHEDULED) {
            logger.info("reprocess expired scheduling workflow {}", workflow.getId());
            add(workflow, workflow.getWorkflowName());
        }
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

}
