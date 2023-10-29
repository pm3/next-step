package io.aston.entity;

import com.aston.micronaut.sql.convert.JsonConverterFactory;
import com.aston.micronaut.sql.entity.Format;
import com.aston.micronaut.sql.entity.Table;
import io.aston.model.WorkflowDef;

import java.time.Instant;

@Table(name = "ns_workflow_def", id = "name")
public class WorkflowDefEntity {
    private String name;
    private boolean latest;
    private Instant created;
    @Format(JsonConverterFactory.JSON)
    private WorkflowDef data;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public boolean isLatest() {
        return latest;
    }

    public void setLatest(boolean latest) {
        this.latest = latest;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public WorkflowDef getData() {
        return data;
    }

    public void setData(WorkflowDef data) {
        this.data = data;
    }
}
