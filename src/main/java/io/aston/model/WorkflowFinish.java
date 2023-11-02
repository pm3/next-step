package io.aston.model;

import com.aston.micronaut.sql.convert.JsonConverterFactory;
import com.aston.micronaut.sql.entity.Format;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Introspected
@Serdeable.Deserializable
@Serdeable.Serializable
public class WorkflowFinish {
    private String id;
    private String uniqueCode;
    private String workflowName;
    private Instant created;
    private Instant modified;
    private State state;
    @Format(JsonConverterFactory.JSON)
    private Map<String, Object> params;
    @Nullable
    @Format(JsonConverterFactory.JSON)
    private Object output;
    @Nullable
    private String workerId;
    @Format(JsonConverterFactory.JSON)
    private List<Task> tasks;

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

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
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

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public String toString() {
        return "Workflow{" +
                "id='" + id + '\'' +
                '}';
    }
}
