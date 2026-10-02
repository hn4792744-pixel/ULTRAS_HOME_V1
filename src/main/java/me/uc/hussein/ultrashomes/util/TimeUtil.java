package me.uc.hussein.ultrashomes.util;

/** Small duration formatter used by cooldown / countdown messages. */
public final class TimeUtil {
    private TimeUtil() {
    }

    public static String seconds(long millis) {
        long s = Math.max(0, (millis + 999) / 1000);
        return String.valueOf(s);
    }

    public static String humanize(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
