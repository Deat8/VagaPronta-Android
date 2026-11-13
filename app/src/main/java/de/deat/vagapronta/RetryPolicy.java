package de.deat.vagapronta;

/** Agendamento sem relógio de parede: o sistema informa tempo monotônico. */
public final class RetryPolicy {
    public static final int MINUTES_MIN = 1;
    public static final int MINUTES_MAX = 30;
    private RetryPolicy() {}
    public static int clamp(int value) {
        return Math.max(MINUTES_MIN, Math.min(MINUTES_MAX, value));
    }
    public static long milliseconds(int minutes) {
        return clamp(minutes) * 60_000L;
    }
    public static long deadlineAfter(long nowElapsedMillis, int minutes) {
        return nowElapsedMillis + milliseconds(minutes);
    }
    public static long secondsRemaining(long nowElapsedMillis, long deadlineElapsedMillis) {
        return Math.max(0, (deadlineElapsedMillis - nowElapsedMillis + 999L) / 1000L);
    }
    public static String countdown(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", m, s);
    }
}
