package telegram.files.bot;

import java.util.Locale;

/**
 * Bot control is configured only through environment variables and stays off when {@code TG_BOT_TOKEN} is unset.
 */
public record BotSettings(
        String token,
        BotAllowlist allowlist,
        BotProxy proxy,
        String configError,
        String defaultAccount,
        boolean notifyEnabled,
        boolean chinese
) {

    public boolean enabled() {
        return token != null && !token.isBlank();
    }

    public static BotSettings fromEnvironment() {
        return from(
                System.getenv("TG_BOT_TOKEN"),
                System.getenv("TG_BOT_ALLOWED_USERS"),
                System.getenv("TG_BOT_PROXY"),
                System.getenv("TG_BOT_DEFAULT_ACCOUNT"),
                System.getenv("TG_BOT_NOTIFY"),
                System.getenv("TG_BOT_LANG")
        );
    }

    public static BotSettings from(String token,
                                   String allowedUsers,
                                   String proxy,
                                   String defaultAccount,
                                   String notify,
                                   String lang) {
        String trimmedToken = token == null ? "" : token.trim();
        String configError = null;
        if (!trimmedToken.isEmpty() && !trimmedToken.matches("\\d+:[A-Za-z0-9_-]+")) {
            configError = "TG_BOT_TOKEN format is invalid";
            trimmedToken = "";
        }
        BotProxy parsedProxy = null;
        if (configError == null && proxy != null && !proxy.isBlank()) {
            try {
                parsedProxy = BotProxy.parse(proxy);
            } catch (IllegalArgumentException e) {
                configError = e.getMessage();
            }
        }
        String account = defaultAccount == null || defaultAccount.isBlank() ? null : defaultAccount.trim();
        return new BotSettings(
                trimmedToken,
                BotAllowlist.parse(allowedUsers),
                parsedProxy,
                configError,
                account,
                parseNotify(notify),
                parseChinese(lang)
        );
    }

    static boolean parseNotify(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }

    static boolean parseChinese(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return !(value.equals("en") || value.equals("english"));
    }
}
