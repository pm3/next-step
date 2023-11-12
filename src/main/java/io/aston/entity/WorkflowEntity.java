package io.aston.entity;

import com.aston.micronaut.sql.convert.JsonConverterFactory;
import com.aston.micronaut.sql.entity.Format;
import com.aston.micronaut.sql.entity.Table;
import io.micronaut.core.annotation.Nullable;

import java.time.Instant;
import java.util.Map;

@Table(name = "ns_workflow")
public class WorkflowEntity {
    private String id;
    private String uniqueCode;
    private String workflowName;
    private State state;
    private Instant created;
    @Nullable
    private Instant modified;
    @Nullable
    @Format(JsonConverterFactory.JSON)
    private Map<String, Object> params;
    @Nullable
    @Format(JsonConverterFactory.JSON)
    private Object output;
    @Nullable
    private String workerId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUniqueCode() {
        return uniqueCode;
    }

    public void setUniqueCode(String uniqueCode) {
        this.uniqueCode = uniqueCode;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
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

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    @Override
    public String toString() {
        return "WorkflowEntity{" +
                "id='" + id + '\'' +
                '}';
    }
}
