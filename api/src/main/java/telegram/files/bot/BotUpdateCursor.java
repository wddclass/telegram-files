package telegram.files.bot;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Confirms Bot API updates. The next offset is {@code max(update_id) + 1} of what Telegram just delivered.
 * An update is skipped only when this process already handled that {@code update_id}. A stored offset that is
 * higher than the delivered id (another bot token, or a stale file) must not hide it.
 */
public final class BotUpdateCursor {

    private long offset;

    private final Set<Long> seen = new HashSet<>();

    private int lastDuplicates;

    private int lastUnreadable;

    public BotUpdateCursor(long offset) {
        this.offset = Math.max(0, offset);
    }

    public synchronized long offset() {
        return offset;
    }

    public synchronized int lastDuplicates() {
        return lastDuplicates;
    }

    public synchronized int lastUnreadable() {
        return lastUnreadable;
    }

    public synchronized List<JsonObject> accept(JsonArray updates) {
        lastDuplicates = 0;
        lastUnreadable = 0;
        long maxId = -1;
        List<JsonObject> fresh = new ArrayList<>();
        int size = updates == null ? 0 : updates.size();
        for (int i = 0; i < size; i++) {
            JsonObject update;
            try {
                update = updates.getJsonObject(i);
            } catch (RuntimeException ignored) {
                lastUnreadable++;
                continue;
            }
            long id = BotPoll.updateId(update);
            if (id < 0) {
                lastUnreadable++;
                continue;
            }
            if (id > maxId) {
                maxId = id;
            }
            // Trust Telegram: it only returns updates that still need confirmation.
            // Do not drop them because a stored offset from another bot is higher.
            if (!seen.add(id)) {
                lastDuplicates++;
                continue;
            }
            fresh.add(update);
        }
        if (maxId >= 0) {
            long confirmed = maxId + 1;
            if (confirmed > offset || maxId < offset) {
                offset = confirmed;
            }
        }
        if (seen.size() > 1024) {
            seen.removeIf(id -> id < offset);
        }
        return fresh;
    }
}
