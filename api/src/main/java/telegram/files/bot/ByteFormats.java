package telegram.files.bot;

import java.util.Locale;

public final class ByteFormats {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private ByteFormats() {
    }

    public static String bytes(long value) {
        long bytes = Math.max(0, value);
        double size = bytes;
        int unit = 0;
        while (size >= 1024 && unit < UNITS.length - 1) {
            size /= 1024;
            unit++;
        }
        if (unit == 0) {
            return bytes + " B";
        }
        return String.format(Locale.ROOT, "%.1f %s", size, UNITS[unit]);
    }

    public static String speed(long bytesPerSecond) {
        return bytes(bytesPerSecond) + "/s";
    }

    public static int percent(long downloaded, long total) {
        if (total <= 0) {
            return 0;
        }
        long value = downloaded * 100 / total;
        if (value < 0) {
            return 0;
        }
        return (int) Math.min(100, value);
    }
}
