package io.aston.service;

import io.aston.model.TaskFinish;
import io.aston.worker.EventQueue;
import io.aston.worker.SimpleTimer;
import jakarta.inject.Singleton;

@Singleton
public class TaskFinishEventQueue extends EventQueue<TaskFinish> {
    public TaskFinishEventQueue(SimpleTimer timer) {
        super(timer);
    }
}
