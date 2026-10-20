package telegram.files.bot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Caps outgoing chat messages at {@link #DEFAULT_LIMIT} per {@link #WINDOW_MS}. Further messages are counted
 * per chat so they can be replaced by one digest when a slot opens.
 */
public final class OutboundLimiter {

    public static final int DEFAULT_LIMIT = 20;

    public static final long WINDOW_MS = 60_000L;

    private final int limit;

    private final long windowMs;

    private final ArrayDeque<Long> sentAt = new ArrayDeque<>();

    private final LinkedHashMap<Long, Integer> held = new LinkedHashMap<>();

    private boolean flushArmed;

    public OutboundLimiter(int limit, long windowMs) {
        this.limit = Math.max(1, limit);
        this.windowMs = Math.max(1, windowMs);
    }

    public synchronized Decision decide(long chatId, long now) {
        evict(now);
        if (sentAt.size() < limit) {
            sentAt.addLast(now);
            return Decision.allow();
        }
        held.merge(chatId, 1, Integer::sum);
        boolean arm = !flushArmed;
        flushArmed = true;
        return Decision.hold(arm, waitMs(now));
    }

    public synchronized List<Digest> flush(long now) {
        evict(now);
        flushArmed = false;
        List<Digest> digests = new ArrayList<>();
        var iterator = held.entrySet().iterator();
        while (iterator.hasNext()) {
            if (sentAt.size() >= limit) {
                flushArmed = true;
                break;
            }
            var entry = iterator.next();
            digests.add(new Digest(entry.getKey(), entry.getValue()));
            sentAt.addLast(now);
            iterator.remove();
        }
        return digests;
    }

    public synchronized boolean pending() {
        return !held.isEmpty();
    }

    public synchronized long waitMs(long now) {
        evict(now);
        if (sentAt.isEmpty()) {
            return 1;
        }
        return Math.max(1, sentAt.peekFirst() + windowMs - now);
    }

    private void evict(long now) {
        while (!sentAt.isEmpty() && now - sentAt.peekFirst() >= windowMs) {
            sentAt.removeFirst();
        }
    }

    public record Decision(boolean allowed, boolean armFlush, long waitMs) {
        static Decision allow() {
            return new Decision(true, false, 0);
        }

        static Decision hold(boolean armFlush, long waitMs) {
            return new Decision(false, armFlush, waitMs);
        }
    }

    public record Digest(long chatId, int count) {
    }
}
