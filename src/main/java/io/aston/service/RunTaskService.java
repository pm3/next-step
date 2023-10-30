package io.aston.service;

import io.aston.controller.RuntimeController;
import io.aston.dao.ITaskDao;
import io.aston.entity.TaskEntity;
import io.aston.model.State;
import io.aston.user.UserDataException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Singleton
public class RunTaskService {
    private final ITaskDao taskDao;
    private final EventQueue eventQueue;
    private final Map<String, RunTask> runTaskMap = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(RunTaskService.class);

    public record RunTask(TaskEntity task, Consumer<TaskEntity> changeState) {
    }

    public RunTaskService(ITaskDao taskDao, EventQueue eventQueue) {
        this.taskDao = taskDao;
        this.eventQueue = eventQueue;
        eventQueue.setNextStepRunning(this::nextStepRunning);
    }

    public void runTask(TaskEntity task, Consumer<TaskEntity> changeState) {
        if (changeState != null) {
            runTaskMap.put(task.getId(), new RunTask(task, changeState));
        }
        if (task.getState() == State.SCHEDULED) {
            eventQueue.addEvent(new RuntimeController.TaskEvent(task));
        } else if (task.getState() == State.RUNNING) {
            long timeout = task.getRunningTimeout();
            if (timeout < 0) timeout = 0L;
            Date expire = new Date(Date.from(task.getModified()).getTime() + timeout * 1000);
            if (expire.after(new Date())) {
                scheduleTaskJob(expire, task.getId(), this::timeoutTaskRunning);
            } else {
                timeoutTaskRunning(task.getId());
            }
        } else if (task.getState() == State.FAILED) {
            if (task.getMaxRetryCount() > task.getRetries()) {
                long retryDelay = task.getRetryWait();
                if (retryDelay < 0) retryDelay = 0L;
                Date expire = new Date(Date.from(task.getModified()).getTime() + retryDelay * 1000L);
                if (expire.after(new Date())) {
                    scheduleTaskJob(expire, task.getId(), this::retryFailedTask);
                } else {
                    retryFailedTask(task.getId());
                }
            }
        }
    }

    private boolean nextStepRunning(IEvent event) {
        if (event instanceof RuntimeController.TaskEvent taskEvent) {
            TaskEntity task = taskEvent.task();
            Instant now = Instant.now();
            if (taskDao.updateState(task.getId(), State.SCHEDULED, State.RUNNING, null, now) == 0) {
                logger.debug("stop running, task not scheduled " + task.getId());
                return false;
            }
            task.setState(State.RUNNING);
            task.setModified(now);
            changeState(task);
            long timeout = task.getRunningTimeout();
            if (timeout < 0) timeout = 0;
            scheduleTaskJob(new Date(System.currentTimeMillis() + timeout * 1000L), task.getId(), this::timeoutTaskRunning);
        }
        return true;
    }

    private void timeoutTaskRunning(String taskId) {
        if (taskDao.updateState(taskId, State.RUNNING, State.FAILED, "timeout", Instant.now()) > 0) {
            TaskEntity task = taskDao.loadById(taskId).orElseThrow(() -> new UserDataException("task not found"));
            changeState(task);
            if (task.getMaxRetryCount() > task.getRetries()) {
                long retryDelay = task.getRetryWait();
                if (retryDelay <= 0) retryDelay = 60L;
                scheduleTaskJob(new Date(System.currentTimeMillis() + retryDelay * 1000), taskId, this::retryFailedTask);
            }
        }
    }

    private void retryFailedTask(String taskId) {
        if (taskDao.updateStateAndRetry(taskId, State.FAILED, State.SCHEDULED, Instant.now()) > 0) {
            TaskEntity task = taskDao.loadById(taskId).orElseThrow(() -> new UserDataException("task not found"));
            changeState(task);
            eventQueue.addEvent(new RuntimeController.TaskEvent(task));
        }
    }

    private void changeState(TaskEntity task) {
        RunTask runTask = runTaskMap.get(task.getId());
        if (runTask != null && runTask.changeState() != null) {
            runTask.changeState().accept(task);
        }
    }

    private void scheduleTaskJob(Date expire, String taskId, Consumer<String> job) {
        eventQueue.schedule(expire, () -> job.accept(taskId));
    }
}
