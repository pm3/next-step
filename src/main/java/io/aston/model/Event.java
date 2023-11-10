package io.aston.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Introspected
@Serdeable.Deserializable
@Serdeable.Serializable
public record Event(EventType type, String name, @Nullable Workflow workflow, @Nullable Task task) {
}
