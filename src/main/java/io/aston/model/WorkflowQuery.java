package io.aston.model;

import io.aston.entity.State;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.QueryValue;

import java.time.Instant;
import java.util.List;

@Introspected
public class WorkflowQuery {
    @Nullable
    @QueryValue
    private List<String> workflowNames;
    @Nullable
    @QueryValue
    private List<State> states;
    @Nullable
    @QueryValue
    private Instant dateFrom;
    @Nullable
    @QueryValue
    private Instant dateTo;

    public List<String> getWorkflowNames() {
        return workflowNames;
    }

    public void setWorkflowNames(List<String> workflowNames) {
        this.workflowNames = workflowNames;
    }

    public List<State> getStates() {
        return states;
    }

    public void setStates(List<State> states) {
        this.states = states;
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
}
