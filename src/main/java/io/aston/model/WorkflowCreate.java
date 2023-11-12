package io.aston.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Introspected
@Serdeable.Deserializable
@Serdeable.Serializable
public class WorkflowCreate {
    private String name;
    private String uniqueCode;
    @Nullable
    private Map<String, Object> params;
    int scheduledTimeout;
    int runningTimeout;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUniqueCode() {
        return uniqueCode;
    }

    public void setUniqueCode(String uniqueCode) {
        this.uniqueCode = uniqueCode;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public int getScheduledTimeout() {
        return scheduledTimeout;
    }

    public WorkflowCreate setScheduledTimeout(int scheduledTimeout) {
        this.scheduledTimeout = scheduledTimeout;
        return this;
    }

    public int getRunningTimeout() {
        return runningTimeout;
    }

    public WorkflowCreate setRunningTimeout(int runningTimeout) {
        this.runningTimeout = runningTimeout;
        return this;
    }
}
