package telegram.files.bot;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The production failure was the /start help text repeating many times a minute.
 * Telegram redelivers an update until getUpdates is called with offset = update_id + 1.
 */
public class BotSpamTest {

    @TempDir
    Path temp;

    @Test
    void redeliveredStartSendsHelpOnceAndConfirmsOffset() throws Exception {
        List<String> sent = new ArrayList<>();
        BotUpdateHandler handler = new BotUpdateHandler(
                BotAllowlist.parse("5"),
                new BotTexts(true),
                null,
                null,
                new BotGateway() {
                    @Override
                    public void send(long chatId, String text, JsonObject replyMarkup) {
                        sent.add(text);
                    }

                    @Override
                    public void edit(long chatId, int messageId, String text, JsonObject replyMarkup) {
                    }

                    @Override
                    public void answer(String callbackId, String text) {
                    }
                },
                new BotSessions(),
                ignored -> {
                }
        );
        JsonArray batch = new JsonObject("""
                {"ok":true,"result":[{"update_id":100,"message":{"message_id":1,"date":1,"text":"/start","chat":{"id":5,"type":"private"},"from":{"id":5,"is_bot":false}}}]}
                """).getJsonArray("result");
        BotUpdateCursor cursor = new BotUpdateCursor(0);
        for (int i = 0; i < 30; i++) {
            for (JsonObject update : cursor.accept(batch)) {
                handler.onUpdate(update);
            }
        }
        assertEquals(1, sent.size());
        assertTrue(sent.getFirst().contains("Telegram Files 机器人"));
        assertEquals(101, cursor.offset());

        JsonObject nextRequest = new JsonObject().put("offset", cursor.offset()).put("timeout", 50);
        JsonObject echoed = new JsonObject(nextRequest.encode());
        assertEquals(101L, echoed.getLong("offset"));

        Path offsetFile = temp.resolve("bot-offset.txt");
        BotFiles.writeAtomically(offsetFile, BotOffsetFile.encode(5, cursor.offset()));
        BotOffsetFile.Loaded sameBot = BotOffsetFile.load(offsetFile, 5);
        assertFalse(sameBot.reset());
        assertEquals(101, sameBot.offset());
    }

    @Test
    void staleOffsetFromAnotherBotIsResetAndLowerUpdateIdsAreConfirmed() throws Exception {
        BotOffsetFile.Loaded legacy = BotOffsetFile.parse("484995938", 5);
        assertTrue(legacy.reset());
        assertEquals(0, legacy.offset());

        BotOffsetFile.Loaded otherBot = BotOffsetFile.parse("111 484995938", 5);
        assertTrue(otherBot.reset());
        assertEquals(0, otherBot.offset());
        assertTrue(otherBot.reason().contains("111"));

        assertEquals(5, BotSettings.botId("5:abc_DEF"));
        assertEquals("5 187620024", BotOffsetFile.encode(5, 187620024));

        Path offsetFile = temp.resolve("bot-offset.txt");
        BotFiles.writeAtomically(offsetFile, "484995938");
        BotOffsetFile.Loaded loaded = BotOffsetFile.load(offsetFile, 5);
        assertTrue(loaded.reset());
        assertEquals(0, loaded.offset());

        // The stored offset was the previous bot's update_id. Telegram still delivers this bot's smaller ids.
        JsonArray delivered = new JsonObject("""
                {"ok":true,"result":[{"update_id":187620023,"message":{"message_id":1,"text":"/start"}}]}
                """).getJsonArray("result");
        BotUpdateCursor cursor = new BotUpdateCursor(484995938);
        assertEquals(1, cursor.accept(delivered).size());
        assertEquals(187620024L, cursor.offset());
        assertTrue(cursor.accept(delivered).isEmpty());
        assertEquals(187620024L, cursor.offset());
    }

    @Test
    void decodedUpdateIdsAdvancePastIntegerRangeAndNumericStrings() {
        JsonObject small = new JsonObject("{\"update_id\":42}");
        assertEquals(42L, BotPoll.updateId(small));
        assertEquals(Integer.class, small.getValue("update_id").getClass());

        JsonObject wide = new JsonObject("{\"update_id\":3000000000}");
        assertEquals(3000000001L, BotPoll.advanceOffset(0, new JsonArray().add(wide)));

        JsonArray quoted = new JsonArray().add(new JsonObject().put("update_id", "77"));
        assertEquals(78, BotPoll.advanceOffset(10, quoted));

        BotUpdateCursor cursor = new BotUpdateCursor(0);
        JsonArray missing = new JsonArray().add(new JsonObject().put("message", new JsonObject().put("text", "/start")));
        assertTrue(cursor.accept(missing).isEmpty());
        assertEquals(0, cursor.offset());
        assertEquals(1, cursor.lastUnreadable());
    }

    @Test
    void sameBatchDoesNotHandleOneUpdateTwice() {
        JsonObject update = new JsonObject().put("update_id", 7).put("message", new JsonObject().put("text", "/start"));
        BotUpdateCursor cursor = new BotUpdateCursor(0);
        List<JsonObject> fresh = cursor.accept(new JsonArray().add(update).add(update));
        assertEquals(1, fresh.size());
        assertEquals(1, cursor.lastDuplicates());
        assertEquals(8, cursor.offset());
    }

    @Test
    void outboundRateLimitKeepsTwentyPerMinuteAndDigestsTheRest() {
        List<String> sent = new ArrayList<>();
        List<Runnable> tasks = new ArrayList<>();
        long[] now = {1_000};
        BotOutbound outbound = new BotOutbound(
                new BotTexts(true),
                new BotOutbound.Transport() {
                    @Override
                    public void send(long chatId, String text, JsonObject markup, boolean interactive) {
                        sent.add(text);
                    }

                    @Override
                    public void edit(long chatId, int messageId, String text, JsonObject markup) {
                        sent.add(text);
                    }
                },
                (delay, action) -> tasks.add(action),
                new OutboundLimiter(20, 60_000),
                () -> now[0]
        );
        for (int i = 0; i < 25; i++) {
            outbound.send(5, "help " + i, null);
        }
        assertEquals(20, sent.size());
        assertEquals("help 0", sent.getFirst());
        assertEquals("help 19", sent.get(19));
        assertEquals(1, tasks.size());

        now[0] = 61_000;
        tasks.getFirst().run();
        assertEquals(21, sent.size());
        assertTrue(sent.get(20).contains("已合并 5"));
        assertTrue(sent.get(20).contains("/downloads"));
    }

    @Test
    void singleReplyIsSentImmediatelyEvenWhenNotificationsAreCapped() {
        List<String> sent = new ArrayList<>();
        List<Long> waits = new ArrayList<>();
        long[] now = {5_000};
        BotOutbound outbound = new BotOutbound(
                new BotTexts(true),
                new BotOutbound.Transport() {
                    @Override
                    public void send(long chatId, String text, JsonObject markup, boolean interactive) {
                        sent.add(interactive ? "now:" + text : text);
                    }

                    @Override
                    public void edit(long chatId, int messageId, String text, JsonObject markup) {
                        sent.add("edit:" + text);
                    }
                },
                (delay, action) -> waits.add(delay),
                new OutboundLimiter(20, 60_000),
                () -> now[0]
        );
        outbound.sendNow(5, "help", null);
        assertEquals(List.of("now:help"), sent);
        assertTrue(waits.isEmpty());

        for (int i = 0; i < 20; i++) {
            outbound.send(5, "notice " + i, null);
        }
        outbound.sendNow(5, "status", null);
        assertEquals("now:status", sent.getLast());
        assertTrue(waits.isEmpty());
    }

    @Test
    void inactiveAccountErrorsAreNotPerFileNoticesAndLedgerSurvivesRestart() throws Exception {
        assertTrue(BotNotifications.inactiveAccount("401 Unauthorized"));
        assertTrue(BotNotifications.inactiveAccount("账号未授权"));
        assertTrue(BotNotifications.inactiveAccount("AUTH_KEY_UNREGISTERED"));
        assertFalse(BotNotifications.inactiveAccount("disk full"));
        assertFalse(BotNotifications.inactiveAccount(null));

        Path file = temp.resolve("bot-notices.log");
        NoticeLedger ledger = new NoticeLedger(file);
        assertTrue(ledger.first("file:UPLOAD_FAIL"));
        assertFalse(ledger.first("file:UPLOAD_FAIL"));
        NoticeLedger restarted = new NoticeLedger(file);
        assertFalse(restarted.first("file:UPLOAD_FAIL"));
        assertTrue(restarted.first("file:UPLOAD_OK"));
    }
}
