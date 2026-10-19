package telegram.files;

import cn.hutool.core.util.StrUtil;
import telegram.files.repository.SettingRclone;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds an rclone argv list. Arguments are never passed through a shell.
 */
public final class RcloneCommand {

    public static final Duration TIMEOUT = Duration.ofHours(12);

    private RcloneCommand() {
    }

    public static List<String> build(SettingRclone settings, String appRoot, Path source, long chatId, long messageId) {
        String fileName = source == null || source.getFileName() == null ? null : source.getFileName().toString();
        return build(settings, appRoot, source, new RclonePaths.Values(0, null, null, chatId, null, messageId, null, fileName, null));
    }

    public static List<String> build(SettingRclone settings, String appRoot, Path source, RclonePaths.Values values) {
        if (settings == null || StrUtil.isBlank(settings.remote)) {
            throw new IllegalArgumentException("rclone remote is empty");
        }
        if (source == null || source.getFileName() == null) {
            throw new IllegalArgumentException("local file is missing");
        }
        String fileName = sanitizeFileName(source.getFileName().toString());
        RclonePaths.Values named = new RclonePaths.Values(
                values.accountId(),
                values.accountName(),
                values.accountUsername(),
                values.chatId(),
                values.chatTitle(),
                values.messageId(),
                values.type(),
                fileName,
                values.completionEpochMs());
        List<String> command = new ArrayList<>();
        command.add("rclone");
        command.add(settings.move() ? "moveto" : "copyto");
        command.add("--config");
        command.add(settings.resolvedConfigPath(appRoot));
        command.add(source.toString());
        command.add(destination(settings, named));
        command.addAll(parseFlags(settings.extraFlags));
        return command;
    }

    public static String destination(SettingRclone settings, RclonePaths.Values values) {
        if (settings == null || StrUtil.isBlank(settings.remote)) {
            throw new IllegalArgumentException("rclone remote is empty");
        }
        return joinRemote(settings.remote, RclonePaths.render(settings.pathTemplate, RclonePaths.resolveZone(settings.timeZone), values));
    }

    public static String destination(String remote, long chatId, long messageId, String fileName) {
        SettingRclone settings = SettingRclone.defaults();
        settings.remote = remote;
        return destination(settings, new RclonePaths.Values(0, null, null, chatId, null, messageId, null, fileName, null));
    }

    public static String joinRemote(String remote, String relative) {
        String base = remote == null ? "" : remote.trim();
        if (base.isEmpty()) {
            throw new IllegalArgumentException("rclone remote is empty");
        }
        if (!base.endsWith("/") && !base.endsWith(":")) {
            base = base + "/";
        }
        return base + relative;
    }

    public static String sanitizeFileName(String fileName) {
        String name = fileName == null ? "" : fileName.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace("\r", "").replace("\n", "").trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException("invalid file name");
        }
        return name;
    }

    public static List<String> parseFlags(String flags) {
        List<String> parsed = new ArrayList<>();
        if (flags == null || flags.isBlank()) {
            return parsed;
        }
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < flags.length(); i++) {
            char ch = flags.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    parsed.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (quote != 0) {
            throw new IllegalArgumentException("unclosed quote in rclone flags");
        }
        if (!current.isEmpty()) {
            parsed.add(current.toString());
        }
        return parsed;
    }

    /**
     * {@code schedule} is either a number of seconds or a 5-field cron expression
     * (minute hour day-of-month month day-of-week).
     */
    public static boolean isDue(String schedule, long lastRunMs, long nowMs) {
        if (schedule == null || schedule.isBlank() || nowMs < lastRunMs) {
            return false;
        }
        String trimmed = schedule.trim();
        if (trimmed.matches("\\d+")) {
            long seconds = Long.parseLong(trimmed);
            if (seconds < 30) {
                seconds = 30;
            }
            return nowMs - lastRunMs >= seconds * 1000L;
        }
        String[] fields = trimmed.split("\\s+");
        if (fields.length != 5) {
            return false;
        }
        ZonedDateTime now = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault());
        if (!cronMatches(fields, now)) {
            return false;
        }
        if (lastRunMs <= 0) {
            return true;
        }
        ZonedDateTime last = Instant.ofEpochMilli(lastRunMs).atZone(ZoneId.systemDefault());
        return last.getYear() != now.getYear()
                || last.getDayOfYear() != now.getDayOfYear()
                || last.getHour() != now.getHour()
                || last.getMinute() != now.getMinute();
    }

    private static boolean cronMatches(String[] fields, ZonedDateTime now) {
        try {
            if (!fieldMatches(fields[0], now.getMinute(), 0)) {
                return false;
            }
            if (!fieldMatches(fields[1], now.getHour(), 0)) {
                return false;
            }
            if (!fieldMatches(fields[3], now.getMonthValue(), 1)) {
                return false;
            }
            boolean domRestricted = !"*".equals(fields[2]);
            boolean dowRestricted = !"*".equals(fields[4]);
            int cronDow = now.getDayOfWeek().getValue() % 7;
            boolean dom = fieldMatches(fields[2], now.getDayOfMonth(), 1);
            boolean dow = fieldMatches(fields[4], cronDow, 0) || (cronDow == 0 && fieldMatches(fields[4], 7, 0));
            if (domRestricted && dowRestricted) {
                return dom || dow;
            }
            return dom && dow;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean fieldMatches(String expr, int value, int minimum) {
        for (String part : expr.split(",")) {
            if (partMatches(part.trim(), value, minimum)) {
                return true;
            }
        }
        return false;
    }

    private static boolean partMatches(String part, int value, int minimum) {
        if (part.isEmpty()) {
            return false;
        }
        String base = part;
        int step = 1;
        int slash = part.indexOf('/');
        if (slash >= 0) {
            base = part.substring(0, slash);
            step = Integer.parseInt(part.substring(slash + 1));
            if (step <= 0) {
                return false;
            }
        }
        if ("*".equals(base) || base.isEmpty()) {
            return Math.floorMod(value - minimum, step) == 0;
        }
        int dash = base.indexOf('-');
        if (dash >= 0) {
            int start = Integer.parseInt(base.substring(0, dash));
            int end = Integer.parseInt(base.substring(dash + 1));
            if (value < start || value > end) {
                return false;
            }
            return Math.floorMod(value - start, step) == 0;
        }
        return Integer.parseInt(base) == value;
    }
}
