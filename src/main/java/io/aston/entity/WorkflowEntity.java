package io.aston.entity;

import com.aston.micronaut.sql.convert.JsonConverterFactory;
import com.aston.micronaut.sql.entity.Format;
import com.aston.micronaut.sql.entity.Table;
import io.aston.model.State;
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
    private String worker;

    public String getId() {
        return id;
    }

    public WorkflowEntity setId(String id) {
        this.id = id;
        return this;
    }

    public String getUniqueCode() {
        return uniqueCode;
    }

    public WorkflowEntity setUniqueCode(String uniqueCode) {
        this.uniqueCode = uniqueCode;
        return this;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public WorkflowEntity setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
        return this;
    }

    public State getState() {
        return state;
    }

    public WorkflowEntity setState(State state) {
        this.state = state;
        return this;
    }

    public Instant getCreated() {
        return created;
    }

    public WorkflowEntity setCreated(Instant created) {
        this.created = created;
        return this;
    }

    public Instant getModified() {
        return modified;
    }

    public WorkflowEntity setModified(Instant modified) {
        this.modified = modified;
        return this;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public WorkflowEntity setParams(Map<String, Object> params) {
        this.params = params;
        return this;
    }

    public Object getOutput() {
        return output;
    }

    public WorkflowEntity setOutput(Object output) {
        this.output = output;
        return this;
    }

    public String getWorker() {
        return worker;
    }

    public WorkflowEntity setWorker(String worker) {
        this.worker = worker;
        return this;
    }

    @Override
    public String toString() {
        return "WorkflowEntity{" +
                "id='" + id + '\'' +
                '}';
    }
}
