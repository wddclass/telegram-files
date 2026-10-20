package telegram.files.bot;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bot API client. Long polling uses {@link #call}; chat messages go through a small queue so a 429
 * backs off instead of failing the verticle.
 */
public final class BotApiClient {

    private static final Log log = LogFactory.get();

    private final Vertx vertx;

    private final WebClient client;

    private final String base;

    private final ConcurrentLinkedQueue<Pending> queue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean draining = new AtomicBoolean(false);

    private BotApiClient(Vertx vertx, WebClient client, String base) {
        this.vertx = vertx;
        this.client = client;
        this.base = base;
    }

    public static BotApiClient create(Vertx vertx, BotSettings settings) {
        WebClientOptions options = new WebClientOptions()
                .setKeepAlive(true)
                .setConnectTimeout(15_000)
                .setIdleTimeout(120)
                .setIdleTimeoutUnit(TimeUnit.SECONDS);
        if (settings.proxy() != null) {
            options.setProxyOptions(settings.proxy().toOptions());
        }
        return new BotApiClient(vertx, WebClient.create(vertx, options),
                "https://api.telegram.org/bot" + settings.token() + "/");
    }

    public Future<JsonObject> call(String method, JsonObject body) {
        return post(method, body == null ? new JsonObject() : body);
    }

    public Future<JsonObject> sendMessage(long chatId, String text, JsonObject replyMarkup) {
        return enqueue("sendMessage", messageBody(chatId, text, replyMarkup));
    }

    public Future<JsonObject> editMessageText(long chatId, int messageId, String text, JsonObject replyMarkup) {
        JsonObject body = messageBody(chatId, text, replyMarkup).put("message_id", messageId);
        return enqueue("editMessageText", body);
    }

    public Future<JsonObject> answerCallbackQuery(String callbackId, String text) {
        JsonObject body = new JsonObject().put("callback_query_id", callbackId);
        if (text != null && !text.isBlank()) {
            body.put("text", BotTexts.clip(text, 180)).put("show_alert", false);
        }
        return enqueue("answerCallbackQuery", body);
    }

    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            log.debug("Bot API client close: %s".formatted(e.getMessage()));
        }
    }

    private Future<JsonObject> enqueue(String method, JsonObject body) {
        Promise<JsonObject> promise = Promise.promise();
        queue.add(new Pending(method, body, promise, 0));
        drain();
        return promise.future();
    }

    private void drain() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        Pending pending = queue.poll();
        if (pending == null) {
            draining.set(false);
            return;
        }
        post(pending.method, pending.body).onComplete(result -> {
            if (result.failed() && result.cause() instanceof BotApiException error
                    && error.retryAfterSeconds() > 0 && pending.attempt < 5) {
                log.warn("Bot API %s rate limited, retrying in %ds".formatted(pending.method, error.retryAfterSeconds()));
                vertx.setTimer(error.retryAfterSeconds() * 1000L, _ -> {
                    queue.add(new Pending(pending.method, pending.body, pending.promise, pending.attempt + 1));
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
            vertx.setTimer(40, _ -> {
                draining.set(false);
                drain();
            });
        });
    }

    private Future<JsonObject> post(String method, JsonObject body) {
        Promise<JsonObject> promise = Promise.promise();
        long timeout = "getUpdates".equals(method) ? 70_000L : 30_000L;
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

    private record Pending(String method, JsonObject body, Promise<JsonObject> promise, int attempt) {
    }
}
