package io.aston.service;

import io.aston.dao.ITaskDao;
import io.aston.entity.TaskEntity;
import io.aston.model.State;
import io.aston.model.Task;
import io.aston.user.UserDataException;
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

    private static final Logger logger = LoggerFactory.getLogger(TaskEventStream.class);

    public TaskEventStream(SimpleTimer timer, ITaskDao taskDao) {
        super(timer);
        this.taskDao = taskDao;
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
        } else if (task.getState() == State.FAILED) {
            if (task.getMaxRetryCount() > task.getRetries()) {
                long retryDelay = task.getRetryWait();
                if (retryDelay < 0) retryDelay = 0L;
                Date expire = new Date(Date.from(task.getModified()).getTime() + retryDelay * 1000L);
                if (expire.after(new Date())) {
                    timer.schedule(expire, task.getId(), this::retryFailedTask);
                } else {
                    retryFailedTask(task.getId());
                }
            }
        }
    }

    @Override
    protected boolean callRunningState(TaskEntity task) {
        Instant now = Instant.now();
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
        String taskId = task0.getId();
        if (taskDao.updateState(taskId, State.RUNNING, State.FAILED, "timeout", Instant.now()) > 0) {
            TaskEntity task = taskDao.loadById(taskId).orElseThrow(() -> new UserDataException("task not found"));
            if (task.getMaxRetryCount() > task.getRetries()) {
                long retryDelay = task.getRetryWait();
                if (retryDelay <= 0) retryDelay = 60L;
                timer.schedule(retryDelay * 1000L, taskId, this::retryFailedTask);
            }
        }
    }

    private void retryFailedTask(String taskId) {
        if (taskDao.updateStateAndRetry(taskId, State.FAILED, State.SCHEDULED, Instant.now()) > 0) {
            TaskEntity task = taskDao.loadById(taskId).orElseThrow(() -> new UserDataException("task not found"));
            add(task, task.getTaskName());
        }
    }

    public Task toTask(TaskEntity task) {
        Task t2 = new Task();
        t2.setId(task.getId());
        t2.setWorkflowId(task.getWorkflowId());
        t2.setRef(task.getRef());
        t2.setTaskName(task.getTaskName());
        t2.setWorkflowName(task.getWorkflowName());
        t2.setParams(task.getParams());
        t2.setOutput(task.getOutput());
        t2.setState(task.getState());
        t2.setCreated(task.getCreated());
        t2.setModified(task.getModified());
        t2.setRetries(task.getRetries());
        return t2;
    }


}
