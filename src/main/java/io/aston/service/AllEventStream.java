package io.aston.service;

import io.aston.dao.ITaskDao;
import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.*;
import io.aston.worker.BaseEventStream;
import io.aston.worker.SimpleTimer;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Singleton
public class AllEventStream extends BaseEventStream {

    private final ITaskDao taskDao;
    private Consumer<TaskEntity> handleFailedStateTask;
    private static final Logger logger = LoggerFactory.getLogger(AllEventStream.class);

    public AllEventStream(SimpleTimer timer, ITaskDao taskDao) {
        super(timer);
        this.taskDao = taskDao;
    }

    public void setHandleFailedStateTask(Consumer<TaskEntity> handleFailedStateTask) {
        this.handleFailedStateTask = handleFailedStateTask;
    }

    public void runTask(TaskEntity task) {
        if (task.getState() == State.SCHEDULED) {
            add(new InternalEvent(EventType.NEW_TASK, task.getTaskName(), null, task, task.getRunningTimeout() * 1000L));
        } else if (task.getState() == State.RUNNING) {
            long timeout = task.getRunningTimeout();
            if (timeout < 0) timeout = 0L;
            Date expire = new Date(Date.from(task.getModified()).getTime() + timeout * 1000);
            if (expire.after(new Date())) {
                timer.schedule(expire, task, this::callRunningExpireStateTask);
            } else {
                callRunningExpireStateTask(task);
            }
        } else if (task.getState() == State.RETRY) {
            long wait = task.getRetryWait();
            if (wait < 0) wait = 0L;
            Date expire = new Date(Date.from(task.getModified()).getTime() + wait * 1000);
            if (expire.after(new Date())) {
                timer.schedule(expire, task, this::callRetryReprocessTask);
            } else {
                callRetryReprocessTask(task);
            }
        }
    }

    @Override
    protected boolean callRunningState(InternalEvent event) {
        if (event.type() == EventType.NEW_TASK) {
            return callRunningStateTask(event.task());
        }
        return true;
    }

    @Override
    protected void callRunningExpireState(InternalEvent event) {
        if (event.type() == EventType.NEW_TASK) {
            callRunningExpireStateTask(event.task());
        }
    }

    protected boolean callRunningStateTask(TaskEntity task) {
        Instant now = Instant.now();
        if (taskDao.updateState(task.getId(), State.SCHEDULED, State.RUNNING, null, now) == 0) {
            logger.debug("stop running, task not scheduled " + task.getId());
            return false;
        }
        task.setState(State.RUNNING);
        task.setModified(now);
        if (task.getRunningTimeout() > 0) {
            timer.schedule(task.getRunningTimeout() * 1000L, task, this::callRunningExpireStateTask);
        }
        return true;
    }

    protected void callRunningExpireStateTask(TaskEntity task) {
        Instant now = Instant.now();
        if (task.getRetries() + 1 < task.getMaxRetryCount()) {
            if (taskDao.updateStateAndRetry(task.getId(), State.RUNNING, State.RETRY, now) > 0) {
                task.setState(State.RETRY);
                task.setOutput(Map.of("message", "timeout"));
                task.setModified(now);
                task.setRetries(task.getRetries() + 1);
                runTask(task);
            }
        } else {
            Map<String, String> errValue = Map.of("type", "retry", "message", "max retry");
            if (taskDao.updateState(task.getId(), State.RUNNING, State.FAILED, errValue, now) > 0) {
                task.setState(State.FAILED);
                task.setModified(now);
                task.setRetries(task.getRetries() + 1);
                if (handleFailedStateTask != null) {
                    handleFailedStateTask.accept(task);
                }
            }
        }
    }

    private void callRetryReprocessTask(TaskEntity task) {
        Instant now = Instant.now();
        if (taskDao.updateState(task.getId(), State.RETRY, State.SCHEDULED, null, now) > 0) {
            task.setState(State.SCHEDULED);
            task.setModified(now);
            task.setOutput(null);
            runTask(task);
        }
    }

    public List<EventStat> stat() {
        return null;
    }

    public Event toEvent(InternalEvent e) {
        return new Event(e.type(), e.name(), toWorkflow(e.workflow()), toTask(e.task()));
    }

    public Task toTask(TaskEntity task) {
        if (task == null) return null;
        Task t2 = new Task();
        t2.setId(task.getId());
        t2.setWorkflowId(task.getWorkflowId());
        t2.setWorkerId(task.getWorkerId());
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

    public Workflow toWorkflow(WorkflowEntity workflow) {
        if (workflow == null) return null;
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
