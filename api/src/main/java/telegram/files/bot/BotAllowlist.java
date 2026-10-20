package telegram.files.bot;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Users allowed to talk to the control bot. Empty means nobody: the bot must not answer.
 * Entries are Telegram numeric user ids and/or {@code @usernames}.
 */
public final class BotAllowlist {

    private final Set<Long> userIds;

    private final Set<String> usernames;

    private BotAllowlist(Set<Long> userIds, Set<String> usernames) {
        this.userIds = userIds;
        this.usernames = usernames;
    }

    public static BotAllowlist parse(String raw) {
        Set<Long> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String token = part.trim();
                if (token.isEmpty()) {
                    continue;
                }
                if (token.startsWith("@")) {
                    token = token.substring(1).trim();
                }
                if (token.isEmpty()) {
                    continue;
                }
                if (token.chars().allMatch(Character::isDigit)) {
                    try {
                        long id = Long.parseLong(token);
                        if (id > 0) {
                            ids.add(id);
                        }
                    } catch (NumberFormatException ignored) {
                        // A huge numeric token is not a user id; ignore it.
                    }
                    continue;
                }
                if (token.matches("[A-Za-z0-9_]{4,32}")) {
                    names.add(token.toLowerCase(Locale.ROOT));
                }
            }
        }
        return new BotAllowlist(Set.copyOf(ids), Set.copyOf(names));
    }

    public boolean isEmpty() {
        return userIds.isEmpty() && usernames.isEmpty();
    }

    public int idCount() {
        return userIds.size();
    }

    public int usernameCount() {
        return usernames.size();
    }

    /**
     * @param userId   sender id ({@code message.from.id}), never a group chat id
     * @param username sender username without {@code @}, or null
     */
    public boolean allows(long userId, String username) {
        if (isEmpty()) {
            return false;
        }
        if (userId > 0 && userIds.contains(userId)) {
            return true;
        }
        if (username == null || username.isBlank()) {
            return false;
        }
        String normalized = username.startsWith("@") ? username.substring(1) : username;
        return usernames.contains(normalized.toLowerCase(Locale.ROOT));
    }

    public Set<Long> userIds() {
        return Collections.unmodifiableSet(userIds);
    }
}
