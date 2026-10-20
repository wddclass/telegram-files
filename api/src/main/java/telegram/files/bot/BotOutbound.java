package telegram.files.bot;

import io.vertx.core.json.JsonObject;

import java.util.function.LongSupplier;

/**
 * Sends chat messages through {@link OutboundLimiter}. Callback answers are not included; Telegram needs those
 * immediately and they are not the messages that flooded the chat.
 */
public final class BotOutbound {

    public interface Transport {
        void send(long chatId, String text, JsonObject markup);

        void edit(long chatId, int messageId, String text, JsonObject markup);
    }

    public interface Scheduler {
        void schedule(long delayMs, Runnable action);
    }

    private final BotTexts texts;

    private final Transport transport;

    private final Scheduler scheduler;

    private final OutboundLimiter limiter;

    private final LongSupplier clock;

    private boolean flushScheduled;

    public BotOutbound(BotTexts texts,
                       Transport transport,
                       Scheduler scheduler,
                       OutboundLimiter limiter,
                       LongSupplier clock) {
        this.texts = texts;
        this.transport = transport;
        this.scheduler = scheduler;
        this.limiter = limiter;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public void send(long chatId, String text, JsonObject markup) {
        offer(chatId, () -> transport.send(chatId, text, markup));
    }

    public void edit(long chatId, int messageId, String text, JsonObject markup) {
        offer(chatId, () -> transport.edit(chatId, messageId, text, markup));
    }

    private void offer(long chatId, Runnable delivery) {
        OutboundLimiter.Decision decision = limiter.decide(chatId, clock.getAsLong());
        if (decision.allowed()) {
            delivery.run();
            return;
        }
        if (decision.armFlush() && !flushScheduled) {
            flushScheduled = true;
            scheduler.schedule(Math.max(1, decision.waitMs()), this::flush);
        }
    }

    private void flush() {
        flushScheduled = false;
        for (OutboundLimiter.Digest digest : limiter.flush(clock.getAsLong())) {
            transport.send(digest.chatId(), texts.suppressed(digest.count()), null);
        }
        if (limiter.pending() && !flushScheduled) {
            flushScheduled = true;
            scheduler.schedule(Math.max(1, limiter.waitMs(clock.getAsLong())), this::flush);
        }
    }
}
