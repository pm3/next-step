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
public class Workflow {
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
    @Format(JsonConverterFactory.JSON)
    private List<Task> tasks;

    public String getId() {
        return id;
    }

    public Workflow setId(String id) {
        this.id = id;
        return this;
    }

    public String getUniqueCode() {
        return uniqueCode;
    }

    public Workflow setUniqueCode(String uniqueCode) {
        this.uniqueCode = uniqueCode;
        return this;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public Workflow setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
        return this;
    }

    public Instant getCreated() {
        return created;
    }

    public Workflow setCreated(Instant created) {
        this.created = created;
        return this;
    }

    public Instant getModified() {
        return modified;
    }

    public Workflow setModified(Instant modified) {
        this.modified = modified;
        return this;
    }

    public State getState() {
        return state;
    }

    public Workflow setState(State state) {
        this.state = state;
        return this;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Workflow setParams(Map<String, Object> params) {
        this.params = params;
        return this;
    }

    public Object getOutput() {
        return output;
    }

    public Workflow setOutput(Object output) {
        this.output = output;
        return this;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public Workflow setTasks(List<Task> tasks) {
        this.tasks = tasks;
        return this;
    }

    public String toString() {
        return "Workflow{" +
                "id='" + id + '\'' +
                '}';
    }
}
