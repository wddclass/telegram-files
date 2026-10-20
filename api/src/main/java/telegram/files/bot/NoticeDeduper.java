package telegram.files.bot;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drops repeated notices for the same file and outcome inside a time window.
 */
public final class NoticeDeduper {

    private final long windowMs;

    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();

    public NoticeDeduper(long windowMs) {
        this.windowMs = windowMs;
    }

    public boolean first(String key, long now) {
        AtomicBoolean fresh = new AtomicBoolean(false);
        seen.compute(key, (ignored, previous) -> {
            if (previous == null || now - previous >= windowMs) {
                fresh.set(true);
                return now;
            }
            return previous;
        });
        return fresh.get();
    }
}
