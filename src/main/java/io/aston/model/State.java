package io.aston.model;

public enum State {
    SCHEDULED,
    RUNNING,
    RETRY,
    COMPLETED,
    FAILED,
    FATAL_ERROR;

    public static boolean in(State s1, State... states) {
        for (State s2 : states) if (s1 == s2) return true;
        return false;
    }
}
