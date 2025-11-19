package eu.kotori.justRTP.utils;

public final class TimeUtils {

    private TimeUtils() {}

    public static String formatDuration(long seconds) {
        if (seconds <= 0) return "0s";

        if (seconds < 60) {
            return seconds + "s";
        }

        if (seconds < 3600) {
            long mins = seconds / 60;
            long rem = seconds % 60;
            if (rem > 0) {
                return mins + "m " + rem + "s";
            }
            return mins + "m";
        }

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        sb.append(hours).append("h");
        if (minutes > 0) sb.append(" ").append(minutes).append("m");
        if (secs > 0) sb.append(" ").append(secs).append("s");
        return sb.toString();
    }
}
