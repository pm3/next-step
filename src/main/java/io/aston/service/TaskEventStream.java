package io.aston.service;

import io.aston.dao.ITaskDao;
import io.aston.dao.IWorkflowDao;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.State;
import io.aston.model.Task;
import io.aston.worker.EventStream;
import io.aston.worker.SimpleTimer;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Date;

@Singleton
public class TaskEventStream extends EventStream<TaskEntity> {

    private final ITaskDao taskDao;
    private final IWorkflowDao workflowDao;

    private static final Logger logger = LoggerFactory.getLogger(TaskEventStream.class);

    public TaskEventStream(SimpleTimer timer, ITaskDao taskDao, IWorkflowDao workflowDao) {
        super(timer);
        this.taskDao = taskDao;
        this.workflowDao = workflowDao;
    }

    @Override
    protected long eventRunTimeout(TaskEntity task) {
        return task.getRunningTimeout() * 1000;
    }

    public void runTask(TaskEntity task) {
        if (task.getState() == State.SCHEDULED) {
            add(task, task.getTaskName());
        } else if (task.getState() == State.RUNNING) {
            long timeout = task.getRunningTimeout();
            if (timeout < 0) timeout = 0L;
            Date expire = new Date(Date.from(task.getModified()).getTime() + timeout * 1000);
            if (expire.after(new Date())) {
                timer.schedule(expire, task, this::callRunningExpireState);
            } else {
                callRunningExpireState(task);
            }
        }
    }

    @Override
    protected boolean callRunningState(TaskEntity task) {
        Instant now = Instant.now();
        if (task.getId().equals(task.getWorkflowId())) {
            //virtual task start workflow
            task.setState(State.RUNNING);
            task.setModified(now);
            return true;
        }
        if (taskDao.updateState(task.getId(), State.SCHEDULED, State.RUNNING, null, now) == 0) {
            logger.debug("stop running, task not scheduled " + task.getId());
            return false;
        }
        task.setState(State.RUNNING);
        task.setModified(now);
        return true;
    }

    @Override
    protected void callRunningExpireState(TaskEntity task0) {
        Instant now = Instant.now();
        if (task0.getId().equals(task0.getWorkflowId())) {
            //virtual task start workflow
            WorkflowEntity workflow = workflowDao.loadById(task0.getWorkflowId())
                    .orElse(null);
            if (workflow != null && workflow.getState() == State.SCHEDULED) {
                task0.setState(State.SCHEDULED);
                task0.setModified(now);
                add(task0, task0.getTaskName());
            }
            return;
        }
        String taskId = task0.getId();
        if (taskDao.updateStateAndRetry(taskId, State.RUNNING, State.SCHEDULED, now) > 0) {
            task0.setState(State.SCHEDULED);
            task0.setModified(now);
            task0.setRetries(task0.getRetries() + 1);
            add(task0, task0.getTaskName());
        }
    }

    public Task toTask(TaskEntity task) {
        Task t2 = new Task();
        t2.setId(task.getId());
        t2.setWorkflowId(task.getWorkflowId());
        t2.setWorkerId(task.getWorkerId());
        t2.setRef(task.getRef());
        t2.setTaskName(task.getTaskName());
        t2.setWorkflowName(task.getWorkflowName());
        t2.setParams(task.getParams());
        t2.setOutput(task.getOutput());
        t2.setState(task.getState());
        t2.setCreated(task.getCreated());
        t2.setModified(task.getModified());
        t2.setRetries(task.getRetries());
        t2.setRunningTimeout(task.getRunningTimeout());
        t2.setMaxRetryCount(task.getMaxRetryCount());
        return t2;
    }
}
