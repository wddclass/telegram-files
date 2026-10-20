package telegram.files.bot;

import java.util.Locale;
import java.util.Optional;

public final class BotCommands {

    private BotCommands() {
    }

    public static Optional<String> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("/")) {
            return Optional.empty();
        }
        int end = 1;
        while (end < trimmed.length()) {
            char ch = trimmed.charAt(end);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                break;
            }
            end++;
        }
        if (end == 1) {
            return Optional.empty();
        }
        return Optional.of(trimmed.substring(1, end).toLowerCase(Locale.ROOT));
    }
}
