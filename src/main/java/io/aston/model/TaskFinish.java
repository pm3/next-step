package io.aston.model;

import io.aston.service.IEvent;
import io.micronaut.core.annotation.Nullable;

public class TaskFinish implements IEvent {
    private String taskId;
    private String workerId;
    @Nullable
    private Object output;
    private State state;

    @Override
    public String getName() {
        return workerId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public Object getOutput() {
        return output;
    }

    public void setOutput(Object output) {
        this.output = output;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
}
