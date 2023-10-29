package io.aston.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;

@Introspected
@Serdeable.Deserializable
@Serdeable.Serializable
public class WorkflowDef {
    private Instant created;
    private String name;
    private String description;
    private boolean latest;
    private String uniqueCodeExpr;
    private List<String> cronExpressions;
    private List<TaskDef> tasks;

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isLatest() {
        return latest;
    }

    public void setLatest(boolean latest) {
        this.latest = latest;
    }

    public String getUniqueCodeExpr() {
        return uniqueCodeExpr;
    }

    public void setUniqueCodeExpr(String uniqueCodeExpr) {
        this.uniqueCodeExpr = uniqueCodeExpr;
    }

    public List<String> getCronExpressions() {
        return cronExpressions;
    }

    public void setCronExpressions(List<String> cronExpressions) {
        this.cronExpressions = cronExpressions;
    }

    public List<TaskDef> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskDef> tasks) {
        this.tasks = tasks;
    }
}
