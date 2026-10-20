package telegram.files.bot;

import org.drinkless.tdlib.TdApi;
import telegram.files.bot.BotModels.BotAccount;

import java.util.List;

public final class BotAccounts {

    private BotAccounts() {
    }

    public record Selection(BotAccount chosen, List<BotAccount> options, boolean defaultMissed) {
        public boolean needsChoice() {
            return chosen == null && options != null && !options.isEmpty();
        }

        public static Selection none() {
            return new Selection(null, List.of(), false);
        }
    }

    public static Selection select(List<BotAccount> accounts, String defaultAccount) {
        List<BotAccount> authorized = accounts == null ? List.of() : accounts.stream()
                .filter(account -> account.authorized() && account.id() > 0)
                .toList();
        if (authorized.isEmpty()) {
            return Selection.none();
        }
        if (authorized.size() == 1) {
            return new Selection(authorized.getFirst(), List.of(), false);
        }
        if (defaultAccount != null && !defaultAccount.isBlank()) {
            String needle = defaultAccount.trim();
            String nameNeedle = needle.startsWith("@") ? needle.substring(1) : needle;
            for (BotAccount account : authorized) {
                if (needle.equals(Long.toString(account.id()))) {
                    return new Selection(account, List.of(), false);
                }
            }
            for (BotAccount account : authorized) {
                if (account.name() != null && account.name().equalsIgnoreCase(nameNeedle)) {
                    return new Selection(account, List.of(), false);
                }
            }
            return new Selection(null, authorized, true);
        }
        return new Selection(null, authorized, false);
    }

    public static String stateKey(TdApi.AuthorizationState state, boolean authorized) {
        if (authorized || (state != null && state.getConstructor() == TdApi.AuthorizationStateReady.CONSTRUCTOR)) {
            return "ready";
        }
        if (state == null) {
            return "unknown";
        }
        return switch (state.getConstructor()) {
            case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> "wait_phone";
            case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> "wait_code";
            case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> "wait_password";
            case TdApi.AuthorizationStateWaitEmailAddress.CONSTRUCTOR -> "wait_email";
            case TdApi.AuthorizationStateWaitEmailCode.CONSTRUCTOR -> "wait_email_code";
            case TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR -> "wait_registration";
            case TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR -> "wait_other_device";
            case TdApi.AuthorizationStateClosed.CONSTRUCTOR -> "closed";
            case TdApi.AuthorizationStateClosing.CONSTRUCTOR -> "closing";
            case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR -> "logging_out";
            case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> "initializing";
            default -> "other";
        };
    }
}
