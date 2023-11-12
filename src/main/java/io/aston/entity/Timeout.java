package io.aston.entity;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Introspected
@Serdeable.Deserializable
@Serdeable.Serializable
public class Timeout {
    int scheduledTimeout;
    int runningTimeout;
    int awaitTimeout;
    int maxRetry;
    int retryWait;
    int maxRetryWait;

    public int getScheduledTimeout() {
        return scheduledTimeout;
    }

    public Timeout setScheduledTimeout(int scheduledTimeout) {
        this.scheduledTimeout = scheduledTimeout;
        return this;
    }

    public int getRunningTimeout() {
        return runningTimeout;
    }

    public Timeout setRunningTimeout(int runningTimeout) {
        this.runningTimeout = runningTimeout;
        return this;
    }

    public int getAwaitTimeout() {
        return awaitTimeout;
    }

    public Timeout setAwaitTimeout(int awaitTimeout) {
        this.awaitTimeout = awaitTimeout;
        return this;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public Timeout setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
        return this;
    }

    public int getRetryWait() {
        return retryWait;
    }

    public Timeout setRetryWait(int retryWait) {
        this.retryWait = retryWait;
        return this;
    }

    public int getMaxRetryWait() {
        return maxRetryWait;
    }

    public Timeout setMaxRetryWait(int maxRetryWait) {
        this.maxRetryWait = maxRetryWait;
        return this;
    }
}
