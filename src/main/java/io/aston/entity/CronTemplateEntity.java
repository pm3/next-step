package io.aston.entity;

import com.aston.micronaut.sql.entity.Table;

import java.time.Instant;
import java.util.List;

@Table(name = "ns_cron_template", id = "name")
public class CronTemplateEntity {
    private String name;
    private String uniqueCodeExpr;
    private List<String> cronExpressions;
    private Instant created;
    private Instant modified;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
}
