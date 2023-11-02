package io.aston.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

@Introspected
@Serdeable.Deserializable
@Serdeable.Serializable
public class WorkflowCreate {
    private String name;
    private String uniqueCode;
    @Nullable
    private Map<String, Object> params;
    @Nullable
    private List<TaskDef> tasks;

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

    public List<TaskDef> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskDef> tasks) {
        this.tasks = tasks;
    }
}
