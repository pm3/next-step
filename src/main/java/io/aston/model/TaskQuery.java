package io.aston.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.QueryValue;

import java.time.Instant;
import java.util.List;

@Introspected
public class TaskQuery {
    @Nullable
    @QueryValue
    private List<String> names;
    @Nullable
    @QueryValue
    private List<State> states;
    @Nullable
    @QueryValue
    private List<String> workflowNames;
    @Nullable
    @QueryValue
    private Instant dateFrom;
    @Nullable
    @QueryValue
    private Instant dateTo;
    @Nullable
    @QueryValue
    private String workflowId;

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }

    public List<State> getStates() {
        return states;
    }

    public void setStates(List<State> states) {
        this.states = states;
    }

    public List<String> getWorkflowNames() {
        return workflowNames;
    }

    public void setWorkflowNames(List<String> workflowNames) {
        this.workflowNames = workflowNames;
    }

    public Instant getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(Instant dateFrom) {
        this.dateFrom = dateFrom;
    }

    public Instant getDateTo() {
        return dateTo;
    }

    public void setDateTo(Instant dateTo) {
        this.dateTo = dateTo;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }
}
