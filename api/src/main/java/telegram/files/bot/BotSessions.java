package telegram.files.bot;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class BotSessions {

    public static final long TTL_MS = 30 * 60 * 1000L;

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public record Session(
            String token,
            long userId,
            long chatId,
            String link,
            Long accountId,
            List<BotMedia> media,
            long createdAt
    ) {
        public Session withMedia(long accountId, List<BotMedia> media) {
            return new Session(token, userId, chatId, link, accountId, List.copyOf(media), createdAt);
        }
    }

    public Session create(long userId, long chatId, String link) {
        purge(System.currentTimeMillis());
        String token;
        do {
            token = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
            if (token.length() > 8) {
                token = token.substring(0, 8);
            }
            while (token.length() < 6) {
                token = token + "0";
            }
        } while (sessions.containsKey(token));
        Session session = new Session(token, userId, chatId, link, null, List.of(), System.currentTimeMillis());
        sessions.put(token, session);
        return session;
    }

    public Optional<Session> get(String token, long now) {
        if (token == null) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (now - session.createdAt() > TTL_MS) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void save(Session session) {
        sessions.put(session.token(), session);
    }

    public void purge(long now) {
        sessions.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > TTL_MS);
        if (sessions.size() <= 500) {
            return;
        }
        sessions.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().createdAt()))
                .limit(sessions.size() - 400L)
                .map(entry -> entry.getKey())
                .toList()
                .forEach(sessions::remove);
    }
}
