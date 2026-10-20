package telegram.files.bot;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.PoolOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bot API client. Long polling uses its own HTTP client so a 50s {@code getUpdates} cannot sit in front of a
 * reply. Chat messages go through a small queue so a 429 backs off instead of failing the verticle.
 * Command replies skip that queue's gap and are not held behind notifications.
 */
public final class BotApiClient {

    private static final Log log = LogFactory.get();

    private final Vertx vertx;

    private final WebClient polling;

    private final WebClient sending;

    private final String base;

    private final ConcurrentLinkedQueue<Pending> interactive = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<Pending> bulk = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean draining = new AtomicBoolean(false);

    private BotApiClient(Vertx vertx, WebClient polling, WebClient sending, String base) {
        this.vertx = vertx;
        this.polling = polling;
        this.sending = sending;
        this.base = base;
    }

    public static BotApiClient create(Vertx vertx, BotSettings settings) {
        // Separate HTTP/1.1 pools: one held getUpdates connection must not block sendMessage.
        WebClientOptions shared = options(settings);
        return new BotApiClient(vertx,
                WebClient.create(vertx, shared, new PoolOptions().setHttp1MaxSize(1)),
                WebClient.create(vertx, new WebClientOptions(shared), new PoolOptions().setHttp1MaxSize(4)),
                "https://api.telegram.org/bot" + settings.token() + "/");
    }

    private static WebClientOptions options(BotSettings settings) {
        WebClientOptions options = new WebClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_1_1)
                .setKeepAlive(true)
                .setConnectTimeout(15_000)
                .setIdleTimeout(120)
                .setIdleTimeoutUnit(TimeUnit.SECONDS);
        if (settings.proxy() != null) {
            options.setProxyOptions(settings.proxy().toOptions());
        }
        return options;
    }

    public Future<JsonObject> call(String method, JsonObject body) {
        return post(method, body == null ? new JsonObject() : body);
    }

    public Future<JsonObject> sendMessage(long chatId, String text, JsonObject replyMarkup) {
        return sendMessage(chatId, text, replyMarkup, false);
    }

    public Future<JsonObject> sendMessage(long chatId, String text, JsonObject replyMarkup, boolean interactive) {
        return enqueue("sendMessage", messageBody(chatId, text, replyMarkup), interactive);
    }

    public Future<JsonObject> editMessageText(long chatId, int messageId, String text, JsonObject replyMarkup) {
        JsonObject body = messageBody(chatId, text, replyMarkup).put("message_id", messageId);
        return enqueue("editMessageText", body, true);
    }

    public Future<JsonObject> deleteMessage(long chatId, int messageId) {
        JsonObject body = new JsonObject().put("chat_id", chatId).put("message_id", messageId);
        return enqueue("deleteMessage", body, true);
    }

    public Future<JsonObject> answerCallbackQuery(String callbackId, String text) {
        JsonObject body = new JsonObject().put("callback_query_id", callbackId);
        if (text != null && !text.isBlank()) {
            body.put("text", BotTexts.clip(text, 180)).put("show_alert", false);
        }
        return enqueue("answerCallbackQuery", body, true);
    }

    public void close() {
        closeQuietly(polling);
        closeQuietly(sending);
    }

    private static void closeQuietly(WebClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.debug("Bot API client close: %s".formatted(e.getMessage()));
        }
    }

    private Future<JsonObject> enqueue(String method, JsonObject body, boolean interactiveSend) {
        Promise<JsonObject> promise = Promise.promise();
        Pending pending = new Pending(method, body, promise, 0, interactiveSend);
        (interactiveSend ? interactive : bulk).add(pending);
        drain();
        return promise.future();
    }

    private void drain() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        Pending pending = pollQueue();
        if (pending == null) {
            draining.set(false);
            return;
        }
        post(pending.method, pending.body).onComplete(result -> {
            if (result.failed() && result.cause() instanceof BotApiException error
                    && error.retryAfterSeconds() > 0 && pending.attempt < 5) {
                log.warn("Bot API %s rate limited, retrying in %ds".formatted(pending.method, error.retryAfterSeconds()));
                vertx.setTimer(error.retryAfterSeconds() * 1000L, _ -> {
                    (pending.interactive ? interactive : bulk)
                            .add(new Pending(pending.method, pending.body, pending.promise, pending.attempt + 1, pending.interactive));
                    draining.set(false);
                    drain();
                });
                return;
            }
            if (result.succeeded()) {
                pending.promise.complete(result.result());
            } else {
                pending.promise.fail(result.cause());
            }
            // A command reply waiting behind a notification should not also wait out the spacing gap.
            if (!interactive.isEmpty() || bulk.isEmpty()) {
                draining.set(false);
                drain();
                return;
            }
            vertx.setTimer(40, _ -> {
                draining.set(false);
                drain();
            });
        });
    }

    private Pending pollQueue() {
        Pending next = interactive.poll();
        return next != null ? next : bulk.poll();
    }

    private Future<JsonObject> post(String method, JsonObject body) {
        Promise<JsonObject> promise = Promise.promise();
        long timeout = "getUpdates".equals(method) ? 70_000L : 30_000L;
        WebClient client = "getUpdates".equals(method) ? polling : sending;
        client.postAbs(base + method)
                .timeout(timeout)
                .sendJsonObject(body)
                .onSuccess(response -> complete(method, response, promise))
                .onFailure(err -> {
                    log.warn("Bot API %s request failed: %s".formatted(method, err.getMessage()));
                    promise.fail(err);
                });
        return promise.future();
    }

    private static void complete(String method, HttpResponse<io.vertx.core.buffer.Buffer> response, Promise<JsonObject> promise) {
        JsonObject json = null;
        try {
            if (response.body() != null && response.body().length() > 0) {
                json = response.bodyAsJsonObject();
            }
        } catch (Exception e) {
            promise.fail(new BotApiException(response.statusCode(), 0, "Bot API " + method + " returned a non-JSON body"));
            return;
        }
        if (json != null && json.getBoolean("ok", false)) {
            promise.complete(json);
            return;
        }
        int status = response.statusCode();
        int code = json == null ? status : json.getInteger("error_code", status);
        int retry = BotPoll.retryAfterSeconds(code == 429 || status == 429 ? 429 : status, json);
        String description = json == null ? "HTTP " + status : json.getString("description", "HTTP " + status);
        promise.fail(new BotApiException(code, retry, "Bot API " + method + " error " + code + ": " + description));
    }

    private static JsonObject messageBody(long chatId, String text, JsonObject replyMarkup) {
        String body = text == null ? "" : text;
        if (body.length() > 4000) {
            int cut = body.lastIndexOf('\n', 3900);
            if (cut < 500) {
                cut = 3900;
            }
            body = body.substring(0, cut) + "\n…";
        }
        JsonObject json = new JsonObject()
                .put("chat_id", chatId)
                .put("text", body)
                .put("parse_mode", "HTML")
                .put("link_preview_options", new JsonObject().put("is_disabled", true));
        if (replyMarkup != null) {
            json.put("reply_markup", replyMarkup);
        }
        return json;
    }

    private record Pending(String method, JsonObject body, Promise<JsonObject> promise, int attempt, boolean interactive) {
    }
}
