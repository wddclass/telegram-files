package telegram.files.bot;

import java.util.Optional;

public final class BotCallbacks {

    private BotCallbacks() {
    }

    public sealed interface Action permits AccountChoice, DownloadChoice {
    }

    public record AccountChoice(String token, long accountId) implements Action {
    }

    public record DownloadChoice(String token, String mode) implements Action {
    }

    public static Optional<Action> parse(String data) {
        if (data == null) {
            return Optional.empty();
        }
        String[] parts = data.split(":", 3);
        if (parts.length != 3 || !parts[1].matches("[0-9a-zA-Z]{4,16}")) {
            return Optional.empty();
        }
        if ("a".equals(parts[0])) {
            try {
                long accountId = Long.parseLong(parts[2]);
                if (accountId <= 0) {
                    return Optional.empty();
                }
                return Optional.of(new AccountChoice(parts[1], accountId));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        if ("d".equals(parts[0]) && parts[2].matches("all|video|photo|\\d{1,4}")) {
            return Optional.of(new DownloadChoice(parts[1], parts[2]));
        }
        return Optional.empty();
    }
}
