package io.aston.entity;

import com.aston.micronaut.sql.convert.JsonConverterFactory;
import com.aston.micronaut.sql.entity.Format;
import com.aston.micronaut.sql.entity.Table;
import io.aston.model.State;
import io.aston.service.IEvent;
import io.micronaut.core.annotation.Nullable;

import java.time.Instant;
import java.util.Map;

@Table(name = "ns_task")
public class TaskEntity implements IEvent {
    private String id;
    private String workflowId;
    private int ref;
    private String name;
    private String workflowName;
    @Nullable
    @Format(JsonConverterFactory.JSON)
    private Map<String, Object> params;
    @Nullable
    @Format(JsonConverterFactory.JSON)
    private Object output;
    private State state;
    private Instant created;
    @Nullable
    private Instant modified;
    private int retries;
    private long runningTimeout;
    private int maxRetryCount;
    private long retryWait;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public int getRef() {
        return ref;
    }

    public void setRef(int ref) {
        this.ref = ref;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
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

    public Instant getModified() {
        return modified;
    }

    public void setModified(Instant modified) {
        this.modified = modified;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public long getRunningTimeout() {
        return runningTimeout;
    }

    public void setRunningTimeout(long runningTimeout) {
        this.runningTimeout = runningTimeout;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public long getRetryWait() {
        return retryWait;
    }

    public void setRetryWait(long retryWait) {
        this.retryWait = retryWait;
    }
}
