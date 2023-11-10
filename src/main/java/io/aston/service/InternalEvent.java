package io.aston.service;

import io.aston.entity.TaskEntity;
import io.aston.entity.WorkflowEntity;
import io.aston.model.EventType;
import io.micronaut.core.annotation.Nullable;

public record InternalEvent(EventType type,
                            String name,
                            @Nullable WorkflowEntity workflow,
                            @Nullable TaskEntity task,
                            long timeout) {
}
