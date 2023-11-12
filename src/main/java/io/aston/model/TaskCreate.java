package io.aston.model;

import com.aston.micronaut.sql.convert.JsonConverterFactory;
import com.aston.micronaut.sql.entity.Format;
import io.aston.entity.Timeout;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Introspected
@Serdeable.Deserializable
@Serdeable.Serializable
public class TaskCreate {
    private String workflowId;
    private String taskName;
    @Nullable
    @Format(JsonConverterFactory.JSON)
    private Map<String, Object> params;
    private Timeout timeout;

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
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

    public Timeout getTimeout() {
        return timeout;
    }

    public TaskCreate setTimeout(Timeout timeout) {
        this.timeout = timeout;
        return this;
    }
}
