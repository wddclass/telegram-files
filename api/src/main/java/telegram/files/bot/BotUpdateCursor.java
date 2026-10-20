package telegram.files.bot;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Confirms Bot API updates. {@code offset} is always {@code last update_id + 1}.
 * A repeated delivery of an id already confirmed, or already handled in this process, is not returned again.
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
        long floor = offset;
        long next = BotPoll.advanceOffset(floor, updates);
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
            if (id < floor || !seen.add(id)) {
                lastDuplicates++;
                continue;
            }
            fresh.add(update);
        }
        if (next > offset) {
            offset = next;
        }
        if (seen.size() > 1024) {
            seen.removeIf(id -> id < offset);
        }
        return fresh;
    }
}
