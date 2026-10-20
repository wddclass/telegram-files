package telegram.files.bot;

import io.vertx.core.json.JsonArray;

/**
 * Advances the Bot API {@code getUpdates} offset and reads {@code retry_after}.
 */
public final class BotPoll {

    private BotPoll() {
    }

    public static long advanceOffset(long current, JsonArray updates) {
        long offset = current;
        if (updates == null) {
            return offset;
        }
        for (int i = 0; i < updates.size(); i++) {
            var update = updates.getJsonObject(i);
            if (update == null || update.getLong("update_id") == null) {
                continue;
            }
            long next = update.getLong("update_id") + 1;
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
