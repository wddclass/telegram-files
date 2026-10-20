package telegram.files.bot;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

/**
 * Turns Bot API updates into commands and download choices. Users outside the allowlist get no reply.
 */
public final class BotUpdateHandler {

    private static final Log log = LogFactory.get();

    private static final long DENY_LOG_WINDOW_MS = 10 * 60 * 1000L;

    private final BotAllowlist allowlist;

    private final BotTexts texts;

    private final String defaultAccount;

    private final BotBackend backend;

    private final BotGateway gateway;

    private final BotSessions sessions;

    private final LongConsumer rememberUser;

    private final ConcurrentHashMap<Long, Long> deniedAt = new ConcurrentHashMap<>();

    public BotUpdateHandler(BotAllowlist allowlist,
                            BotTexts texts,
                            String defaultAccount,
                            BotBackend backend,
                            BotGateway gateway,
                            BotSessions sessions,
                            LongConsumer rememberUser) {
        this.allowlist = allowlist;
        this.texts = texts;
        this.defaultAccount = defaultAccount;
        this.backend = backend;
        this.gateway = gateway;
        this.sessions = sessions;
        this.rememberUser = rememberUser == null ? ignored -> {
        } : rememberUser;
    }

    public void onUpdate(JsonObject update) {
        if (update == null) {
            return;
        }
        sessions.purge(System.currentTimeMillis());
        if (update.containsKey("callback_query")) {
            onCallback(update.getJsonObject("callback_query"));
            return;
        }
        if (update.containsKey("message")) {
            onMessage(update.getJsonObject("message"));
        }
    }

    private void onMessage(JsonObject message) {
        if (message == null) {
            return;
        }
        JsonObject from = message.getJsonObject("from");
        if (!allowed(from)) {
            return;
        }
        JsonObject chat = message.getJsonObject("chat");
        long userId = number(from, "id");
        long chatId = replyChatId(from, chat);
        if (chat != null && "private".equals(chat.getString("type"))) {
            rememberUser.accept(userId);
        }
        String text = message.getString("text");
        if (text == null) {
            text = message.getString("caption");
        }
        Optional<String> command = BotCommands.parse(text);
        if (command.isPresent()) {
            handleCommand(command.get(), chatId);
            return;
        }
        Optional<String> link = TelegramLinks.fromMessage(message);
        if (link.isPresent()) {
            beginLink(userId, chatId, link.get());
            return;
        }
        if (text != null && !text.isBlank()) {
            gateway.send(chatId, texts.unknown(), null);
        }
    }

    private void handleCommand(String command, long chatId) {
        switch (command) {
            case "start", "help" -> gateway.send(chatId, texts.help(), null);
            case "status" -> backend.accountSnapshots()
                    .onSuccess(accounts -> gateway.send(chatId, texts.status(accounts), null))
                    .onFailure(err -> gateway.send(chatId, texts.failed("status", err.getMessage()), null));
            case "downloads" -> backend.downloadReport()
                    .onSuccess(report -> gateway.send(chatId, texts.downloads(report), null))
                    .onFailure(err -> gateway.send(chatId, texts.failed("downloads", err.getMessage()), null));
            case "uploads" -> backend.uploadReport()
                    .onSuccess(report -> gateway.send(chatId, texts.uploads(report), null))
                    .onFailure(err -> gateway.send(chatId, texts.failed("uploads", err.getMessage()), null));
            default -> gateway.send(chatId, texts.unknown(), null);
        }
    }

    private void beginLink(long userId, long chatId, String link) {
        BotAccounts.Selection selection = BotAccounts.select(backend.accounts(), defaultAccount);
        if (selection.chosen() == null && !selection.needsChoice()) {
            gateway.send(chatId, texts.noAccount(), null);
            return;
        }
        BotSessions.Session session = sessions.create(userId, chatId, link);
        if (selection.needsChoice()) {
            gateway.send(chatId, texts.pickAccount(selection.defaultMissed()),
                    BotKeyboards.accounts(session.token(), selection.options(), texts));
            return;
        }
        resolve(session, selection.chosen().id(), chatId, null, false);
    }

    private void onCallback(JsonObject callback) {
        if (callback == null) {
            return;
        }
        JsonObject from = callback.getJsonObject("from");
        String callbackId = callback.getString("id");
        if (!allowed(from)) {
            if (callbackId != null) {
                gateway.answer(callbackId, null);
            }
            return;
        }
        long userId = number(from, "id");
        JsonObject message = callback.getJsonObject("message");
        JsonObject chat = message == null ? null : message.getJsonObject("chat");
        long chatId = replyChatId(from, chat);
        if (chat != null && "private".equals(chat.getString("type"))) {
            rememberUser.accept(userId);
        }
        Integer messageId = message == null ? null : message.getInteger("message_id");
        boolean canEdit = messageId != null && chat != null && "private".equals(chat.getString("type"))
                && chatId == number(chat, "id");
        Optional<BotCallbacks.Action> action = BotCallbacks.parse(callback.getString("data"));
        if (action.isEmpty()) {
            gateway.answer(callbackId, null);
            return;
        }
        switch (action.get()) {
            case BotCallbacks.AccountChoice choice -> onAccountChoice(choice, userId, chatId, callbackId, messageId, canEdit);
            case BotCallbacks.DownloadChoice choice -> onDownloadChoice(choice, userId, chatId, callbackId, messageChatId(chat, chatId), messageId);
        }
    }

    private void onAccountChoice(BotCallbacks.AccountChoice choice,
                                 long userId,
                                 long chatId,
                                 String callbackId,
                                 Integer messageId,
                                 boolean canEdit) {
        Optional<BotSessions.Session> session = owned(choice.token(), userId);
        if (session.isEmpty()) {
            gateway.answer(callbackId, null);
            gateway.send(chatId, texts.expired(), null);
            return;
        }
        if (backend.accounts().stream().noneMatch(account -> account.authorized() && account.id() == choice.accountId())) {
            gateway.answer(callbackId, null);
            gateway.send(chatId, texts.noAccount(), null);
            return;
        }
        gateway.answer(callbackId, texts.parsing());
        resolve(session.get(), choice.accountId(), chatId, messageId, canEdit);
    }

    private void resolve(BotSessions.Session session, long accountId, long chatId, Integer messageId, boolean canEdit) {
        backend.parseLink(accountId, session.link())
                .onSuccess(media -> {
                    BotSessions.Session updated = session.withMedia(accountId, media);
                    sessions.save(updated);
                    if (media.isEmpty()) {
                        show(chatId, messageId, canEdit, texts.noMedia(0), BotKeyboards.removed());
                        return;
                    }
                    show(chatId, messageId, canEdit, texts.mediaList(session.link(), media),
                            BotKeyboards.downloads(updated.token(), media, texts));
                })
                .onFailure(err -> show(chatId, messageId, canEdit, texts.parseFailed(err.getMessage()), BotKeyboards.removed()));
    }

    private void onDownloadChoice(BotCallbacks.DownloadChoice choice,
                                  long userId,
                                  long chatId,
                                  String callbackId,
                                  long messageChatId,
                                  Integer messageId) {
        Optional<BotSessions.Session> found = owned(choice.token(), userId);
        if (found.isEmpty()) {
            gateway.answer(callbackId, null);
            gateway.send(chatId, texts.expired(), null);
            return;
        }
        BotSessions.Session session = found.get();
        if (session.accountId() == null) {
            gateway.answer(callbackId, null);
            gateway.send(chatId, texts.expired(), null);
            return;
        }
        List<BotMedia> selected = BotMedia.select(session.media(), choice.mode());
        if (selected.isEmpty()) {
            gateway.answer(callbackId, texts.nothingMatched());
            return;
        }
        gateway.answer(callbackId, texts.starting());
        backend.startDownloads(session.accountId(), selected)
                .onSuccess(result -> clearSelection(chatId, messageChatId, messageId, texts.started(result)))
                .onFailure(err -> gateway.send(chatId, texts.failed(texts.chinese() ? "下载" : "Download", err.getMessage()), null));
    }

    /**
     * Drops the media list and its buttons once the download has started, then leaves a short confirmation.
     * Messages older than 48 hours cannot be deleted; those are edited into the confirmation with the keyboard removed.
     */
    private void clearSelection(long chatId, long messageChatId, Integer messageId, String confirmation) {
        if (messageId == null || messageId <= 0 || messageChatId == 0) {
            gateway.send(chatId, confirmation, null);
            return;
        }
        gateway.delete(messageChatId, messageId,
                () -> gateway.send(chatId, confirmation, null),
                () -> gateway.edit(messageChatId, messageId, confirmation, BotKeyboards.removed()));
    }

    private static long messageChatId(JsonObject chat, long fallback) {
        long id = number(chat, "id");
        return id == 0 ? fallback : id;
    }

    private Optional<BotSessions.Session> owned(String token, long userId) {
        return sessions.get(token, System.currentTimeMillis()).filter(session -> session.userId() == userId);
    }

    private void show(long chatId, Integer messageId, boolean canEdit, String text, JsonObject markup) {
        if (canEdit && messageId != null) {
            gateway.edit(chatId, messageId, text, markup);
        } else {
            gateway.send(chatId, text, markup);
        }
    }

    private boolean allowed(JsonObject from) {
        if (from == null || from.getBoolean("is_bot", false)) {
            return false;
        }
        long userId = number(from, "id");
        String username = from.getString("username");
        if (!allowlist.allows(userId, username)) {
            deny(userId, username);
            return false;
        }
        return true;
    }

    private void deny(long userId, String username) {
        long now = System.currentTimeMillis();
        Long previous = deniedAt.put(userId, now);
        if (previous != null && now - previous < DENY_LOG_WINDOW_MS) {
            return;
        }
        log.info("Ignored Telegram bot update from user id=%d username=%s; not in TG_BOT_ALLOWED_USERS"
                .formatted(userId, username == null ? "" : username));
    }

    static long replyChatId(JsonObject from, JsonObject chat) {
        if (chat != null && "private".equals(chat.getString("type"))) {
            long chatId = number(chat, "id");
            if (chatId != 0) {
                return chatId;
            }
        }
        return number(from, "id");
    }

    private static long number(JsonObject json, String key) {
        if (json == null) {
            return 0;
        }
        Object value = json.getValue(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0;
    }
}
