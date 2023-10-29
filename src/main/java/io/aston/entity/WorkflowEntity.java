package io.aston.entity;

import com.aston.micronaut.sql.convert.JsonConverterFactory;
import com.aston.micronaut.sql.entity.Format;
import com.aston.micronaut.sql.entity.Table;
import io.aston.model.State;
import io.aston.model.TaskDef;
import io.micronaut.core.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Table(name = "ns_workflow")
public class WorkflowEntity {
    private String id;
    private String uniqueCode;
    private String workflowName;
    private Instant created;
    @Nullable
    private Instant finished;
    private State state;
    @Nullable
    @Format(JsonConverterFactory.JSON)
    private Map<String, Object> params;
    @Nullable
    @Format(JsonConverterFactory.JSON)
    private List<TaskDef> defTasks;

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

    public Instant getFinished() {
        return finished;
    }

    public void setFinished(Instant finished) {
        this.finished = finished;
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

    public List<TaskDef> getDefTasks() {
        return defTasks;
    }

    public void setDefTasks(List<TaskDef> defTasks) {
        this.defTasks = defTasks;
    }
}
