package telegram.files.bot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists {@code getUpdates} offset together with the bot id (the number before {@code :} in the token).
 * A file written by another bot, or by an older version that stored only the offset, is not applied:
 * that offset can be larger than the new bot's update ids and would hide every update.
 */
public final class BotOffsetFile {

    public record Loaded(long offset, boolean reset, String reason) {
    }

    private BotOffsetFile() {
    }

    public static Loaded load(Path file, long botId) {
        if (file == null || !Files.exists(file)) {
            return new Loaded(0, false, null);
        }
        try {
            String text = Files.readString(file).trim();
            if (text.isEmpty()) {
                return new Loaded(0, false, null);
            }
            return parse(text, botId);
        } catch (IOException e) {
            return new Loaded(0, true, "Could not read bot offset (" + e.getMessage() + "). Resetting.");
        }
    }

    public static Loaded parse(String text, long botId) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return new Loaded(0, false, null);
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) {
            return new Loaded(0, true, "Bot offset file has no bot id (stored " + parts[0]
                    + "). Resetting offset for bot " + botId + ".");
        }
        try {
            long storedBot = Long.parseLong(parts[0]);
            long offset = Long.parseLong(parts[1]);
            if (storedBot != botId) {
                return new Loaded(0, true, "Bot offset file belongs to bot " + storedBot
                        + ", current bot is " + botId + ". Resetting offset.");
            }
            return new Loaded(Math.max(0, offset), false, null);
        } catch (NumberFormatException e) {
            return new Loaded(0, true, "Bot offset file is unreadable. Resetting offset for bot " + botId + ".");
        }
    }

    public static String encode(long botId, long offset) {
        return botId + " " + Math.max(0, offset);
    }
}
