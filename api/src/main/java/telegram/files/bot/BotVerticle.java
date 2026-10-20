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
import java.util.Optional;

/**
 * Long-polls the Bot API. The bot is optional: network and API errors are logged and retried.
 */
public class BotVerticle extends AbstractVerticle {

    private static final Log log = LogFactory.get();

    private final BotSettings settings;

    private final BotTexts texts;

    private final BotRuntime runtime = new BotRuntime();

    private final BotSessions sessions = new BotSessions();

    private final NoticeDeduper deduper = new NoticeDeduper(2 * 60 * 1000L);

    private BotApiClient api;

    private BotSubscribers subscribers;

    private BotUpdateHandler handler;

    private MessageConsumer<Object> events;

    private volatile boolean stopped;

    private long offset;

    private long backoffMs = 1_000;

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
            offset = readOffset();
            handler = new BotUpdateHandler(
                    settings.allowlist(),
                    texts,
                    settings.defaultAccount(),
                    runtime,
                    gateway(),
                    sessions,
                    subscribers::remember
            );
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

    private BotGateway gateway() {
        return new BotGateway() {
            @Override
            public void send(long chatId, String text, JsonObject replyMarkup) {
                api.sendMessage(chatId, text, replyMarkup)
                        .onFailure(err -> log.warn("Bot sendMessage to %d failed: %s".formatted(chatId, err.getMessage())));
            }

            @Override
            public void edit(long chatId, int messageId, String text, JsonObject replyMarkup) {
                api.editMessageText(chatId, messageId, text, replyMarkup)
                        .onFailure(err -> log.warn("Bot editMessage to %d failed: %s".formatted(chatId, err.getMessage())));
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
        api.call("deleteWebhook", new JsonObject().put("drop_pending_updates", false))
                .onComplete(ignored -> api.call("getMe", new JsonObject()).onComplete(result -> {
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
        JsonObject body = new JsonObject()
                .put("timeout", 50)
                .put("offset", offset)
                .put("allowed_updates", new JsonArray().add("message").add("callback_query"));
        api.call("getUpdates", body).onComplete(result -> {
            if (stopped) {
                return;
            }
            if (result.failed()) {
                Throwable cause = result.cause();
                if (cause instanceof BotApiException error && error.errorCode() == 409) {
                    log.warn("getUpdates conflicted with a webhook; deleting the webhook and retrying");
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
                return;
            }
            backoffMs = 1_000;
            JsonArray updates = result.result().getJsonArray("result");
            long nextOffset = BotPoll.advanceOffset(offset, updates);
            if (updates != null) {
                for (int i = 0; i < updates.size(); i++) {
                    JsonObject update = updates.getJsonObject(i);
                    try {
                        handler.onUpdate(update);
                    } catch (Exception e) {
                        long id = update == null || update.getLong("update_id") == null ? -1 : update.getLong("update_id");
                        log.error("Failed to handle bot update %d: %s".formatted(id, e.getMessage()), e);
                    }
                }
            }
            if (nextOffset != offset) {
                offset = nextOffset;
                writeOffset();
            }
            schedule(updates == null || updates.isEmpty() ? 200 : 0, this::poll);
        });
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
        if (!deduper.first(event.uniqueId() + ":" + event.kind().name(), System.currentTimeMillis())) {
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
            api.sendMessage(chatId, text, null)
                    .onFailure(err -> log.warn("Bot notification to %d failed: %s".formatted(chatId, err.getMessage())));
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
            log.warn("Could not read bot offset: %s".formatted(e.getMessage()));
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
            Files.writeString(offsetFile, Long.toString(offset));
        } catch (Exception e) {
            log.warn("Could not store bot offset: %s".formatted(e.getMessage()));
        }
    }
}
