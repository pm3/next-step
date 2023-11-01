package io.aston.service;

import io.aston.model.TaskFinish;
import io.aston.worker.EventQueue;
import io.aston.worker.SimpleTimer;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

@Singleton
public class TaskFinishEventQueue extends EventQueue<TaskFinish> {
    public TaskFinishEventQueue(SimpleTimer timer) {
        super(timer);
    }

    public List<String> stat() {
        List<String> l = new ArrayList<>();
        for (Map.Entry<String, BlockingQueue<TaskFinish>> e : eventQueueMap.entrySet()) {
            String pref = e.getKey() + ":";
            for (TaskFinish t : e.getValue()) {
                l.add(pref + t.getTaskId());
            }
        }
        return l;
    }
}
