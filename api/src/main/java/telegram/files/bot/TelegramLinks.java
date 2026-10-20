package telegram.files.bot;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls a Telegram message link out of a bot update. Album posts use the same link shape as a
 * single message; {@code TelegramVerticle.parseLink} expands the album.
 */
public final class TelegramLinks {

    /**
     * Public {@code t.me/<user>/<message>} and private {@code t.me/c/<id>/<message>}, plus an
     * optional thread/comment id and query such as {@code ?single}.
     */
    private static final Pattern MESSAGE_LINK = Pattern.compile(
            "(?i)(?:https?://)?(?:t\\.me|telegram\\.me)/((?:c/\\d+/\\d+(?:/\\d+)?)|(?:[A-Za-z0-9_]{3,}/\\d+(?:/\\d+)?))(\\?[^\\s#]*)?"
    );

    private TelegramLinks() {
    }

    public static Optional<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = MESSAGE_LINK.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        if (!startsAtBoundary(text, matcher.start())) {
            return Optional.empty();
        }
        return Optional.of(normalize(matcher.group(1), matcher.group(2)));
    }

    public static Optional<String> fromMessage(JsonObject message) {
        if (message == null) {
            return Optional.empty();
        }
        String text = message.getString("text");
        JsonArray entities = message.getJsonArray("entities");
        if (text == null) {
            text = message.getString("caption");
            entities = message.getJsonArray("caption_entities");
        }
        Optional<String> fromEntity = fromEntities(text, entities);
        if (fromEntity.isPresent()) {
            return fromEntity;
        }
        return extract(text);
    }

    static Optional<String> fromEntities(String text, JsonArray entities) {
        if (entities == null) {
            return Optional.empty();
        }
        for (int i = 0; i < entities.size(); i++) {
            JsonObject entity = entities.getJsonObject(i);
            if (entity == null) {
                continue;
            }
            String type = entity.getString("type");
            if ("text_link".equals(type)) {
                Optional<String> link = extract(entity.getString("url"));
                if (link.isPresent()) {
                    return link;
                }
            } else if ("url".equals(type) && text != null) {
                int offset = entity.getInteger("offset", -1);
                int length = entity.getInteger("length", 0);
                if (offset >= 0 && length > 0 && offset + length <= text.length()) {
                    Optional<String> link = extract(text.substring(offset, offset + length));
                    if (link.isPresent()) {
                        return link;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean startsAtBoundary(String text, int start) {
        if (start <= 0) {
            return true;
        }
        char previous = text.charAt(start - 1);
        return Character.isWhitespace(previous) || previous == '(' || previous == '[' || previous == '"' || previous == '\'';
    }

    static String normalize(String path, String query) {
        String cleaned = path;
        while (cleaned.endsWith("/") || cleaned.endsWith(")") || cleaned.endsWith("]")
                || cleaned.endsWith(",") || cleaned.endsWith(".") || cleaned.endsWith("。") || cleaned.endsWith("，")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        String link = "https://t.me/" + cleaned;
        if (query != null && !query.isBlank()) {
            String q = query;
            while (q.length() > 1 && (q.endsWith(")") || q.endsWith("]") || q.endsWith(",") || q.endsWith(".")
                    || q.endsWith("。") || q.endsWith("，"))) {
                q = q.substring(0, q.length() - 1);
            }
            link += q;
        }
        return link;
    }
}
