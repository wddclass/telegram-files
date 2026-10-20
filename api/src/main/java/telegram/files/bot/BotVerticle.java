package telegram.files.bot;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import telegram.files.Config;
import telegram.files.DataVerticle;
import telegram.files.EventEnum;
import telegram.files.EventPayload;
import telegram.files.repository.FileRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Long-polls the Bot API. The bot is optional: network and API errors are logged and retried.
 */
public class BotVerticle extends AbstractVerticle {

    private static final Log log = LogFactory.get();

    private final BotSettings settings;

    private final BotTexts texts;

    private final BotRuntime runtime = new BotRuntime();

    private final BotSessions sessions = new BotSessions();

    private BotApiClient api;

    private BotSubscribers subscribers;

    private BotUpdateHandler handler;

    private BotUpdateCursor cursor;

    private BotOutbound outbound;

    private NoticeLedger notices;

    private MessageConsumer<Object> events;

    private final AtomicBoolean pollInFlight = new AtomicBoolean(false);

    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private volatile boolean stopped;

    private long backoffMs = 1_000;

    private long lastDuplicateLog;

    private long lastInactiveLog;

    private Path offsetFile;

    public BotVerticle(BotSettings settings) {
        this.settings = settings;
        this.texts = new BotTexts(settings.chinese());
    }

    @Override
    public void start(Promise<Void> startPromise) {
        try {
            api = BotApiClient.create(vertx, settings);
            Path root = Config.APP_ROOT == null ? Path.of("data") : Path.of(Config.APP_ROOT);
            subscribers = new BotSubscribers(root.resolve("bot-subscribers.json"));
            offsetFile = root.resolve("bot-offset.txt");
            cursor = new BotUpdateCursor(readOffset());
            notices = new NoticeLedger(root.resolve("bot-notices.log"));
            outbound = new BotOutbound(
                    texts,
                    transport(),
                    (delay, action) -> schedule(delay, action),
                    new OutboundLimiter(OutboundLimiter.DEFAULT_LIMIT, OutboundLimiter.WINDOW_MS),
                    System::currentTimeMillis
            );
            handler = new BotUpdateHandler(
                    settings.allowlist(),
                    texts,
                    settings.defaultAccount(),
                    runtime,
                    gateway(),
                    sessions,
                    subscribers::remember
            );
            log.info("Telegram bot getUpdates offset starts at %d".formatted(cursor.offset()));
            if (settings.allowlist().isEmpty()) {
                log.warn("TG_BOT_TOKEN is set but TG_BOT_ALLOWED_USERS is empty. The bot will ignore every update.");
            } else {
                log.info("Telegram bot allowlist has %d id(s) and %d username(s)"
                        .formatted(settings.allowlist().idCount(), settings.allowlist().usernameCount()));
            }
            if (settings.notifyEnabled()) {
                events = vertx.eventBus().consumer(EventEnum.TELEGRAM_EVENT.address(), message -> {
                    if (message.body() instanceof JsonObject body) {
                        onFileEvent(body);
                    }
                });
                log.info("Telegram bot notifications are enabled");
            } else {
                log.info("Telegram bot notifications are off (set TG_BOT_NOTIFY=true to enable)");
            }
            connect();
            startPromise.complete();
        } catch (Exception e) {
            log.error("Telegram bot setup failed: %s".formatted(e.getMessage()), e);
            startPromise.complete();
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        stopped = true;
        if (events != null) {
            events.unregister();
        }
        if (api != null) {
            api.close();
        }
        stopPromise.complete();
    }

    private BotOutbound.Transport transport() {
        return new BotOutbound.Transport() {
            @Override
            public void send(long chatId, String text, JsonObject markup) {
                api.sendMessage(chatId, text, markup)
                        .onFailure(err -> log.warn("Bot sendMessage to %d failed: %s".formatted(chatId, err.getMessage())));
            }

            @Override
            public void edit(long chatId, int messageId, String text, JsonObject markup) {
                api.editMessageText(chatId, messageId, text, markup)
                        .onFailure(err -> log.warn("Bot editMessage to %d failed: %s".formatted(chatId, err.getMessage())));
            }
        };
    }

    private BotGateway gateway() {
        return new BotGateway() {
            @Override
            public void send(long chatId, String text, JsonObject replyMarkup) {
                outbound.send(chatId, text, replyMarkup);
            }

            @Override
            public void edit(long chatId, int messageId, String text, JsonObject replyMarkup) {
                outbound.edit(chatId, messageId, text, replyMarkup);
            }

            @Override
            public void answer(String callbackId, String text) {
                if (callbackId == null) {
                    return;
                }
                api.answerCallbackQuery(callbackId, text)
                        .onFailure(err -> log.debug("Bot answerCallbackQuery failed: %s".formatted(err.getMessage())));
            }
        };
    }

    private void connect() {
        if (stopped || api == null) {
            return;
        }
        if (!connecting.compareAndSet(false, true)) {
            return;
        }
        api.call("deleteWebhook", new JsonObject().put("drop_pending_updates", false))
                .onComplete(ignored -> api.call("getMe", new JsonObject()).onComplete(result -> {
                    connecting.set(false);
                    if (stopped) {
                        return;
                    }
                    if (result.succeeded()) {
                        JsonObject me = result.result().getJsonObject("result");
                        String username = me == null ? "" : me.getString("username", "");
                        log.info("Telegram bot @%s is polling".formatted(username));
                        backoffMs = 1_000;
                        api.call("setMyCommands", commands())
                                .onFailure(err -> log.warn("setMyCommands failed: %s".formatted(err.getMessage())))
                                .onComplete(ignored2 -> poll());
                        return;
                    }
                    if (result.cause() instanceof BotApiException error && (error.errorCode() == 401 || error.errorCode() == 404)) {
                        log.error("Telegram bot token was rejected. Bot control stopped. %s".formatted(error.getMessage()));
                        return;
                    }
                    log.warn("Telegram bot getMe failed, retrying: %s".formatted(result.cause().getMessage()));
                    schedule(backoffMs, this::connect);
                    backoffMs = Math.min(backoffMs * 2, 30_000);
                }));
    }

    private void poll() {
        if (stopped || api == null) {
            return;
        }
        // One long poll at a time. A second getUpdates with the same offset is what redelivers /start.
        if (!pollInFlight.compareAndSet(false, true)) {
            return;
        }
        long requestOffset = cursor.offset();
        JsonObject body = new JsonObject()
                .put("timeout", 50)
                .put("offset", requestOffset)
                .put("allowed_updates", new JsonArray().add("message").add("callback_query"));
        api.call("getUpdates", body).onComplete(result -> {
            pollInFlight.set(false);
            if (stopped) {
                return;
            }
            try {
                if (result.failed()) {
                    onPollFailure(result.cause());
                    return;
                }
                onPollSuccess(result.result());
            } catch (RuntimeException e) {
                log.error("Bot poll handling failed: %s".formatted(e.getMessage()), e);
                backoffMs = Math.min(Math.max(backoffMs * 2, 1_000), 30_000);
                schedule(backoffMs, this::poll);
            }
        });
    }

    private void onPollFailure(Throwable cause) {
        if (cause instanceof BotApiException error && error.errorCode() == 409) {
            log.warn("getUpdates conflicted with another poll or webhook; deleting the webhook and retrying");
            schedule(1_000, this::connect);
            return;
        }
        long wait = backoffMs;
        if (cause instanceof BotApiException error && error.retryAfterSeconds() > 0) {
            wait = error.retryAfterSeconds() * 1000L;
        }
        log.warn("getUpdates failed: %s".formatted(cause.getMessage()));
        backoffMs = Math.min(Math.max(wait * 2, 1_000), 30_000);
        schedule(wait, this::poll);
    }

    private void onPollSuccess(JsonObject body) {
        JsonArray updates = body == null ? null : body.getJsonArray("result");
        int received = updates == null ? 0 : updates.size();
        long previous = cursor.offset();
        // Confirm the offset before replying. A crash while sending must not make Telegram deliver /start again.
        List<JsonObject> fresh = cursor.accept(updates);
        if (cursor.offset() != previous) {
            writeOffset();
            log.info("Telegram bot confirmed getUpdates through offset %d".formatted(cursor.offset()));
        }
        if (received > 0 && cursor.offset() == previous) {
            if (cursor.lastUnreadable() > 0) {
                log.error("getUpdates returned %d update(s) without a readable update_id; offset stays %d. Backing off so the same update is not answered on every poll."
                        .formatted(received, previous));
                backoffMs = Math.min(Math.max(backoffMs * 2, 5_000), 30_000);
                schedule(Math.max(backoffMs, 5_000), this::poll);
                return;
            }
            // Ids were already confirmed. Replying again is the /start flood; polling immediately would spin.
            logDuplicate();
            schedule(1_000, this::poll);
            return;
        }
        if (cursor.lastDuplicates() > 0) {
            logDuplicate();
        }
        for (JsonObject update : fresh) {
            try {
                handler.onUpdate(update);
            } catch (Exception e) {
                log.error("Failed to handle bot update %d: %s".formatted(BotPoll.updateId(update), e.getMessage()), e);
            }
        }
        backoffMs = 1_000;
        schedule(received == 0 ? 200 : 0, this::poll);
    }

    private void logDuplicate() {
        long now = System.currentTimeMillis();
        if (now - lastDuplicateLog <= 30_000) {
            return;
        }
        lastDuplicateLog = now;
        log.warn("Ignored %d redelivered bot update(s); next getUpdates offset is %d"
                .formatted(cursor.lastDuplicates(), cursor.offset()));
    }

    private void onFileEvent(JsonObject body) {
        if (!settings.notifyEnabled() || subscribers == null || subscribers.chatIds().isEmpty()) {
            return;
        }
        JsonObject payload = body.getJsonObject("payload");
        if (payload == null || payload.getInteger("type", 0) != EventPayload.TYPE_FILE_STATUS) {
            return;
        }
        Optional<BotNotifications.Notice> notice = BotNotifications.from(payload.getJsonObject("data"));
        if (notice.isEmpty()) {
            return;
        }
        BotNotifications.Notice event = notice.get();
        if (notices == null || !notices.first(event.uniqueId() + ":" + event.kind().name())) {
            return;
        }
        if (BotNotifications.inactiveAccount(event.error())) {
            long now = System.currentTimeMillis();
            if (now - lastInactiveLog > 10 * 60 * 1000L) {
                lastInactiveLog = now;
                log.info("Skipped bot notifications for an unauthorized or logged-out account");
            }
            return;
        }
        if (DataVerticle.fileRepository == null) {
            return;
        }
        DataVerticle.fileRepository.getByUniqueId(event.uniqueId())
                .onSuccess(record -> deliver(event, record))
                .onFailure(err -> log.warn("Bot notification lookup failed: %s".formatted(err.getMessage())));
    }

    private void deliver(BotNotifications.Notice event, FileRecord record) {
        if (record == null || "thumbnail".equals(record.type())) {
            return;
        }
        String text = texts.notice(event.kind(), record.fileName(), record.size(), event.error());
        for (long chatId : subscribers.chatIds()) {
            outbound.send(chatId, text, null);
        }
    }

    private JsonObject commands() {
        boolean chinese = settings.chinese();
        return new JsonObject().put("commands", new JsonArray()
                .add(command("status", chinese ? "账号授权状态" : "Account status"))
                .add(command("downloads", chinese ? "下载进度" : "Download progress"))
                .add(command("uploads", chinese ? "上传状态" : "Upload status"))
                .add(command("help", chinese ? "帮助" : "Help")));
    }

    private static JsonObject command(String command, String description) {
        return new JsonObject().put("command", command).put("description", description);
    }

    private void schedule(long delayMs, Runnable action) {
        if (stopped) {
            return;
        }
        if (delayMs <= 0) {
            vertx.runOnContext(_ -> action.run());
            return;
        }
        vertx.setTimer(delayMs, _ -> action.run());
    }

    private long readOffset() {
        try {
            if (offsetFile == null || !Files.exists(offsetFile)) {
                return 0;
            }
            String text = Files.readString(offsetFile).trim();
            return text.isEmpty() ? 0 : Long.parseLong(text);
        } catch (Exception e) {
            log.error("Could not read bot offset, starting at 0. Pending updates may be delivered again: %s".formatted(e.getMessage()));
            return 0;
        }
    }

    private void writeOffset() {
        if (offsetFile == null) {
            return;
        }
        try {
            Path parent = offsetFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            BotFiles.writeAtomically(offsetFile, Long.toString(cursor.offset()));
        } catch (Exception e) {
            log.warn("Could not store bot offset: %s".formatted(e.getMessage()));
        }
    }
}
