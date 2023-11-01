package io.aston.entity;

import com.aston.micronaut.sql.convert.JsonConverterFactory;
import com.aston.micronaut.sql.entity.Format;
import com.aston.micronaut.sql.entity.Table;
import io.aston.model.State;
import io.micronaut.core.annotation.Nullable;

import java.time.Instant;
import java.util.Map;

@Table(name = "ns_task")
public class TaskEntity {
    private String id;
    private String workflowId;
    private int ref;
    private String taskName;
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

    public TaskEntity setId(String id) {
        this.id = id;
        return this;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public TaskEntity setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
        return this;
    }

    public int getRef() {
        return ref;
    }

    public TaskEntity setRef(int ref) {
        this.ref = ref;
        return this;
    }

    public String getTaskName() {
        return taskName;
    }

    public TaskEntity setTaskName(String taskName) {
        this.taskName = taskName;
        return this;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public TaskEntity setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
        return this;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public TaskEntity setParams(Map<String, Object> params) {
        this.params = params;
        return this;
    }

    public Object getOutput() {
        return output;
    }

    public TaskEntity setOutput(Object output) {
        this.output = output;
        return this;
    }

    public State getState() {
        return state;
    }

    public TaskEntity setState(State state) {
        this.state = state;
        return this;
    }

    public Instant getCreated() {
        return created;
    }

    public TaskEntity setCreated(Instant created) {
        this.created = created;
        return this;
    }

    public Instant getModified() {
        return modified;
    }

    public TaskEntity setModified(Instant modified) {
        this.modified = modified;
        return this;
    }

    public int getRetries() {
        return retries;
    }

    public TaskEntity setRetries(int retries) {
        this.retries = retries;
        return this;
    }

    public long getRunningTimeout() {
        return runningTimeout;
    }

    public TaskEntity setRunningTimeout(long runningTimeout) {
        this.runningTimeout = runningTimeout;
        return this;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public TaskEntity setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
        return this;
    }

    public long getRetryWait() {
        return retryWait;
    }

    public TaskEntity setRetryWait(long retryWait) {
        this.retryWait = retryWait;
        return this;
    }

    @Override
    public String toString() {
        return "TaskEntity{" +
                "id='" + id + '\'' +
                '}';
    }
}
