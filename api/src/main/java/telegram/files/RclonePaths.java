package telegram.files;

import cn.hutool.core.util.StrUtil;
import org.drinkless.tdlib.TdApi;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the relative path under an rclone remote.
 * The default template matches the previous layout {@code chatId/messageId/fileName}.
 */
public final class RclonePaths {

    public static final String DEFAULT_TEMPLATE = "{chatId}/{messageId}/{fileName}";

    public static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Shanghai");

    private static final Pattern DATE_TOKEN = Pattern.compile("\\{date:([^{}]*)\\}");

    private static final Pattern TOKEN = Pattern.compile(
            "\\{(chatId|chatTitle|messageId|accountId|accountName|accountUsername|type|fileName)\\}");

    private static final int MAX_TEMPLATE_LENGTH = 500;

    private static final int MAX_SEGMENT_LENGTH = 180;

    private RclonePaths() {
    }

    public record Values(
            long accountId,
            String accountName,
            String accountUsername,
            long chatId,
            String chatTitle,
            long messageId,
            String type,
            String fileName,
            Long completionEpochMs
    ) {
    }

    public record AccountLabel(long id, String name, String username) {
    }

    /**
     * Blank uses the {@code TZ} environment variable when it is a valid zone id,
     * then {@link #FALLBACK_ZONE}. A non-blank value must itself be a valid zone id.
     */
    public static ZoneId resolveZone(String configured) {
        if (StrUtil.isNotBlank(configured)) {
            try {
                return ZoneId.of(configured.trim());
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("invalid timezone: " + configured.trim());
            }
        }
        String env = System.getenv("TZ");
        if (StrUtil.isNotBlank(env)) {
            try {
                return ZoneId.of(env.trim());
            } catch (DateTimeException ignored) {
                // TZ is set but not a zone id Java understands.
            }
        }
        return FALLBACK_ZONE;
    }

    public static String displayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        if (first.isEmpty()) {
            return last;
        }
        if (last.isEmpty()) {
            return first;
        }
        return first + " " + last;
    }

    public static String primaryUsername(TdApi.Usernames usernames) {
        if (usernames == null) {
            return "";
        }
        if (usernames.activeUsernames != null) {
            for (String name : usernames.activeUsernames) {
                if (StrUtil.isNotBlank(name)) {
                    return name.trim();
                }
            }
        }
        return StrUtil.blankToDefault(usernames.editableUsername, "").trim();
    }

    /**
     * One path segment. Unicode is kept, including styled letters such as {@code 𝘄𝗱𝗱𝘇𝘇𝘇}.
     * Characters that split or break a path are replaced with {@code _}.
     */
    public static String sanitizeSegment(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        value.codePoints().forEach(cp -> {
            if (cp < 32 || cp == 127) {
                return;
            }
            if (cp == '/' || cp == '\\' || cp == ':' || cp == '*' || cp == '?'
                    || cp == '"' || cp == '<' || cp == '>' || cp == '|') {
                out.append('_');
                return;
            }
            out.appendCodePoint(cp);
        });
        String trimmed = trimEdges(out.toString());
        int count = trimmed.codePointCount(0, trimmed.length());
        if (count > MAX_SEGMENT_LENGTH) {
            trimmed = trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_SEGMENT_LENGTH));
            trimmed = trimEdges(trimmed);
        }
        return trimmed;
    }

    public static String usernameOrId(String username, long accountId) {
        String raw = username == null ? "" : username.trim();
        if (raw.startsWith("@")) {
            raw = raw.substring(1);
        }
        StringBuilder out = new StringBuilder();
        raw.codePoints().forEach(cp -> {
            if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z') || (cp >= '0' && cp <= '9') || cp == '_') {
                out.appendCodePoint(cp);
            }
        });
        if (out.isEmpty()) {
            return Long.toString(accountId);
        }
        return out.toString();
    }

    public static String render(String template, ZoneId zone, Values values) {
        String source = StrUtil.blankToDefault(template, DEFAULT_TEMPLATE).trim();
        if (source.length() > MAX_TEMPLATE_LENGTH) {
            throw new IllegalArgumentException("path template is too long");
        }
        ZoneId useZone = zone == null ? FALLBACK_ZONE : zone;
        long epoch = values.completionEpochMs == null || values.completionEpochMs <= 0
                ? System.currentTimeMillis()
                : values.completionEpochMs;
        ZonedDateTime when = Instant.ofEpochMilli(epoch).atZone(useZone);

        Matcher dates = DATE_TOKEN.matcher(source);
        StringBuilder dated = new StringBuilder();
        while (dates.find()) {
            String pattern = dates.group(1);
            String formatted;
            try {
                formatted = DateTimeFormatter.ofPattern(pattern, Locale.ROOT).format(when);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid date pattern: " + pattern);
            }
            dates.appendReplacement(dated, Matcher.quoteReplacement(formatted));
        }
        dates.appendTail(dated);

        String replaced = TOKEN.matcher(dated.toString()).replaceAll(match -> switch (match.group(1)) {
            case "chatId" -> Long.toString(values.chatId());
            case "chatTitle" -> orSegment(sanitizeSegment(values.chatTitle()), "chat-" + values.chatId());
            case "messageId" -> Long.toString(values.messageId());
            case "accountId" -> Long.toString(values.accountId());
            case "accountName" -> orSegment(sanitizeSegment(values.accountName()), Long.toString(values.accountId()));
            case "accountUsername" -> usernameOrId(values.accountUsername(), values.accountId());
            case "type" -> orSegment(sanitizeSegment(values.type()), "file");
            case "fileName" -> RcloneCommand.sanitizeFileName(values.fileName());
            default -> match.group();
        });

        String[] parts = replaced.split("[/\\\\]+", -1);
        List<String> clean = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (".".equals(part) || "..".equals(part) || part.chars().anyMatch(ch -> ch < 32 || ch == 127)) {
                throw new IllegalArgumentException("path template produced an unsafe segment");
            }
            clean.add(part);
        }
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("path template is empty");
        }
        return String.join("/", clean);
    }

    public static Values sample(AccountLabel account) {
        long now = System.currentTimeMillis();
        long chatId = -1001234567890L;
        if (account == null) {
            return new Values(10001, "Sample Account", "sample", chatId, "Sample Chat", 42, "video", "clip.mp4", now);
        }
        long accountId = account.id == 0 ? 10001 : account.id;
        return new Values(accountId, account.name, account.username, chatId, "Sample Chat", 42, "video", "clip.mp4", now);
    }

    private static String orSegment(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String trimEdges(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && (value.charAt(start) == ' ' || value.charAt(start) == '.')) {
            start++;
        }
        while (end > start && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '.')) {
            end--;
        }
        return value.substring(start, end);
    }
}
