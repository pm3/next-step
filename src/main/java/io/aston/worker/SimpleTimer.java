package io.aston.worker;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

@Singleton
public class SimpleTimer {
    private final Timer timer;
    private static final Logger logger = LoggerFactory.getLogger(SimpleTimer.class);

    public SimpleTimer() {
        this.timer = new Timer("next-step-timeout", true);
    }

    public void schedule(long add, Runnable r) {
        schedule(new Date(System.currentTimeMillis() + add), r);
    }

    public void schedule(Date date, Runnable r) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    r.run();
                } catch (Throwable thr) {
                    logException(thr);
                }
            }
        }, date);
    }

    public <T> void schedule(long add, T id, Consumer<T> consumer) {
        schedule(new Date(System.currentTimeMillis() + add), id, consumer);
    }

    public <T> void schedule(Date date, T id, Consumer<T> consumer) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    consumer.accept(id);
                } catch (Throwable thr) {
                    logException(thr);
                }
            }
        }, date);
    }

    private static void logException(Throwable thr) {
        String m = thr.getMessage();
        try {
            StackTraceElement[] arr = thr.getStackTrace();
            if (arr != null && arr.length > 0) m = m + " " + arr[0].toString();
        } catch (Exception ignore) {
        }
        logger.warn("timer task error " + m);
    }
}
