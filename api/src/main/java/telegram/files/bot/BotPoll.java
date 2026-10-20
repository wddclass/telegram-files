package telegram.files.bot;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Advances the Bot API {@code getUpdates} offset and reads {@code retry_after}.
 * The next request must use {@code last update_id + 1}; anything lower is delivered again.
 */
public final class BotPoll {

    private BotPoll() {
    }

    /**
     * @return the update id, or {@code -1} when it is missing or not numeric
     */
    public static long updateId(JsonObject update) {
        if (update == null) {
            return -1L;
        }
        Object value = update.getValue("update_id");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return -1L;
            }
        }
        return -1L;
    }

    public static long advanceOffset(long current, JsonArray updates) {
        long offset = current;
        if (updates == null) {
            return offset;
        }
        for (int i = 0; i < updates.size(); i++) {
            long id;
            try {
                id = updateId(updates.getJsonObject(i));
            } catch (RuntimeException ignored) {
                continue;
            }
            if (id < 0) {
                continue;
            }
            long next = id + 1;
            if (next > offset) {
                offset = next;
            }
        }
        return offset;
    }

    public static int retryAfterSeconds(int statusCode, io.vertx.core.json.JsonObject body) {
        if (body != null) {
            var parameters = body.getJsonObject("parameters");
            if (parameters != null && parameters.getInteger("retry_after") != null) {
                return Math.max(1, parameters.getInteger("retry_after"));
            }
        }
        return statusCode == 429 ? 5 : 0;
    }
}
