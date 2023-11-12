package io.aston.entity;

public enum State {
    SCHEDULED,
    RUNNING,
    RETRY,
    AWAIT,
    COMPLETED,
    FAILED;

    public static boolean in(State s1, State... states) {
        for (State s2 : states) if (s1 == s2) return true;
        return false;
    }
}
