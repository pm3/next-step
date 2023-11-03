package io.aston.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Map;

@Introspected
@Serdeable.Deserializable
@Serdeable.Serializable
public class TaskCreate {
    private String workflowId;
    @Nullable
    private String workerId;
    private int ref;
    private String taskName;
    @Nullable
    private Map<String, Object> params;
    @Nullable
    private Object output;
    @Nullable
    private State state;
    @Nullable
    private Instant created;
    private int runningTimeout;
    private int maxRetryCount;
    private int retryWait;

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public int getRef() {
        return ref;
    }

    public void setRef(int ref) {
        this.ref = ref;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
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

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public int getRunningTimeout() {
        return runningTimeout;
    }

    public void setRunningTimeout(int runningTimeout) {
        this.runningTimeout = runningTimeout;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public int getRetryWait() {
        return retryWait;
    }

    public void setRetryWait(int retryWait) {
        this.retryWait = retryWait;
    }
}
