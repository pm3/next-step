package io.aston.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class RunTimerTask {

    private static final Logger logger = LoggerFactory.getLogger(RunTimerTask.class);

    public static void schedule(Timer timer, Runnable r, Date time) {
        timer.schedule(timerTask(r), time);
    }

    public static TimerTask timerTask(Runnable r) {
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    r.run();
                } catch (Throwable thr) {
                    String m = thr.getMessage();
                    try {
                        StackTraceElement[] arr = thr.getStackTrace();
                        if (arr != null && arr.length > 0) m = m + " " + arr[0].toString();
                    } catch (Exception ignore) {
                    }
                    logger.warn("timer task error " + m);
                }
            }
        };
    }
}

