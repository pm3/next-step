package io.aston.service;

import io.aston.dao.ITaskDao;
import io.aston.entity.TaskEntity;
import io.aston.model.State;
import io.aston.model.Task;
import io.aston.worker.EventStream;
import io.aston.worker.SimpleTimer;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;

@Singleton
public class TaskEventStream extends EventStream<TaskEntity> {

    private final ITaskDao taskDao;
    private Consumer<TaskEntity> handleFailedState;

    private static final Logger logger = LoggerFactory.getLogger(TaskEventStream.class);

    public TaskEventStream(SimpleTimer timer, ITaskDao taskDao) {
        super(timer);
        this.taskDao = taskDao;
    }

    public void setHandleFailedState(Consumer<TaskEntity> handleFailedState) {
        this.handleFailedState = handleFailedState;
    }

    @Override
    protected long eventRunTimeout(TaskEntity task) {
        return task.getRunningTimeout() * 1000L;
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
        } else if (task.getState() == State.RETRY) {
            long wait = task.getRetryWait();
            if (wait < 0) wait = 0L;
            Date expire = new Date(Date.from(task.getModified()).getTime() + wait * 1000);
            if (expire.after(new Date())) {
                timer.schedule(expire, task, this::callRetryReprocess);
            } else {
                callRetryReprocess(task);
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
        if (task0.getId().equals(task0.getWorkflowId())) {
            return;
        }
        Instant now = Instant.now();
        if (taskDao.updateState(task0.getId(), State.RUNNING, State.FAILED, Map.of("type", "timeout"), now) > 0) {
            task0.setState(State.FAILED);
            task0.setModified(now);
            task0.setRetries(task0.getRetries() + 1);
            if (handleFailedState != null) {
                handleFailedState.accept(task0);
            }
        }
    }

    private void callRetryReprocess(TaskEntity task) {
        Instant now = Instant.now();
        if (taskDao.updateStateAndRetry(task.getId(), State.RETRY, State.SCHEDULED, now) > 0) {
            task.setState(State.SCHEDULED);
            task.setModified(now);
            task.setRetries(task.getRetries() + 1);
            add(task, task.getTaskName());
        }
        Map<String, String> errValue = Map.of("type", "retry", "message", "max retry");
        if (taskDao.updateState(task.getId(), State.RETRY, State.FAILED, errValue, now) > 0) {
            task.setState(State.FAILED);
            task.setOutput(errValue);
            task.setModified(now);
            task.setRetries(task.getRetries() + 1);
            if (handleFailedState != null) {
                handleFailedState.accept(task);
            }
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
