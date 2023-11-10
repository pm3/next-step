package io.aston.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Introspected
@Serdeable.Deserializable
@Serdeable.Serializable
public class EventStat {
    private String name;
    private int count;
    private Instant old;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public Instant getOld() {
        return old;
    }

    public void setOld(Instant old) {
        this.old = old;
    }
}
