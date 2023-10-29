package io.aston.entity;

import com.aston.micronaut.sql.convert.JsonConverterFactory;
import com.aston.micronaut.sql.entity.Format;
import com.aston.micronaut.sql.entity.Table;
import io.aston.model.TaskDef;

import java.time.Instant;
import java.util.List;

@Table(name = "ns_meta_wf", id = "workflowId")
public class MetaWfEntity {
    String workflowId;
    Instant created;
    @Format(JsonConverterFactory.JSON)
    List<TaskDef> tasks;

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public List<TaskDef> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskDef> tasks) {
        this.tasks = tasks;
    }
}
