package telegram.files.bot;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import io.vertx.core.json.JsonArray;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Private chats of allowlisted users who have talked to the bot. Notifications are sent only here.
 */
public final class BotSubscribers {

    private static final Log log = LogFactory.get();

    private final Path file;

    private final Set<Long> ids = ConcurrentHashMap.newKeySet();

    public BotSubscribers(Path file) {
        this.file = file;
        load();
    }

    public void remember(long chatId) {
        if (chatId == 0) {
            return;
        }
        if (ids.add(chatId)) {
            save();
        }
    }

    public Set<Long> chatIds() {
        return Set.copyOf(ids);
    }

    private void load() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            String text = Files.readString(file).trim();
            if (text.isEmpty()) {
                return;
            }
            JsonArray array = new JsonArray(text);
            for (int i = 0; i < array.size(); i++) {
                Object value = array.getValue(i);
                if (value instanceof Number number && number.longValue() != 0) {
                    ids.add(number.longValue());
                }
            }
        } catch (Exception e) {
            log.warn("Could not read bot subscribers: %s".formatted(e.getMessage()));
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonArray array = new JsonArray();
            ids.forEach(array::add);
            Files.writeString(file, array.encode());
        } catch (IOException e) {
            log.warn("Could not store bot subscribers: %s".formatted(e.getMessage()));
        }
    }
}
