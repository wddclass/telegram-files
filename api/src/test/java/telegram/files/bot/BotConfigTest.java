package telegram.files.bot;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BotConfigTest {

    @Test
    void settingsDefaultToOffChineseAndNoNotify() {
        BotSettings settings = BotSettings.from(null, null, null, null, null, null);
        assertFalse(settings.enabled());
        assertTrue(settings.chinese());
        assertFalse(settings.notifyEnabled());
        assertNull(settings.proxy());
        assertNull(settings.configError());
        assertTrue(settings.allowlist().isEmpty());
    }

    @Test
    void invalidTokenAndProxyAreReportedWithoutThrowing() {
        BotSettings badToken = BotSettings.from("not-a-token", "1", null, null, "true", "en");
        assertFalse(badToken.enabled());
        assertEquals("TG_BOT_TOKEN format is invalid", badToken.configError());
        assertFalse(badToken.chinese());
        assertTrue(badToken.notifyEnabled());

        BotSettings badProxy = BotSettings.from("123:abc_DEF", "1", "mtproto://host:1", "9", "off", "zh-CN");
        assertTrue(badProxy.enabled());
        assertTrue(badProxy.configError().contains("TG_BOT_PROXY"));
        assertTrue(badProxy.chinese());
        assertFalse(badProxy.notifyEnabled());
    }

    @Test
    void proxyUrlParsesHttpAndSocks5() {
        BotProxy http = BotProxy.parse("http://user:p%40ss@127.0.0.1:7890");
        assertEquals("http", http.type());
        assertEquals("127.0.0.1", http.host());
        assertEquals(7890, http.port());
        assertEquals("user", http.username());
        assertEquals("p@ss", http.password());

        BotProxy socks = BotProxy.parse("socks5://10.0.0.2:1080");
        assertEquals("socks5", socks.type());
        assertEquals(1080, socks.port());
        assertNull(socks.username());

        BotProxy implied = BotProxy.parse("localhost:8080");
        assertEquals("http", implied.type());
        assertEquals("localhost", implied.host());
    }

    @Test
    void pollOffsetAndRetryAfter() {
        JsonArray updates = new JsonArray()
                .add(new JsonObject().put("update_id", 4))
                .add(new JsonObject().put("update_id", 9));
        assertEquals(10, BotPoll.advanceOffset(3, updates));
        assertEquals(3, BotPoll.advanceOffset(3, new JsonArray()));
        JsonObject limited = new JsonObject().put("parameters", new JsonObject().put("retry_after", 7));
        assertEquals(7, BotPoll.retryAfterSeconds(429, limited));
        assertEquals(5, BotPoll.retryAfterSeconds(429, new JsonObject()));
        assertEquals(0, BotPoll.retryAfterSeconds(400, new JsonObject()));
    }

    @Test
    void notificationsIgnoreProgressAndKeepFailures() {
        assertTrue(BotNotifications.from(new JsonObject().put("uniqueId", "a").put("downloadStatus", "downloading")).isEmpty());
        assertEquals(BotNotifications.Kind.DOWNLOAD_OK, BotNotifications.from(new JsonObject()
                .put("uniqueId", "a").put("downloadStatus", "completed")).orElseThrow().kind());
        assertEquals(BotNotifications.Kind.UPLOAD_FAIL, BotNotifications.from(new JsonObject()
                .put("uniqueId", "a").put("rcloneStatus", "failed").put("rcloneError", "nope")).orElseThrow().kind());
        assertEquals("nope", BotNotifications.from(new JsonObject()
                .put("uniqueId", "a").put("rcloneStatus", "failed").put("rcloneError", "nope")).orElseThrow().error());
    }

    @Test
    void duplicateNoticesAreSuppressedInsideTheWindow() {
        NoticeDeduper deduper = new NoticeDeduper(1_000);
        assertTrue(deduper.first("file:DOWNLOAD_OK", 0));
        assertFalse(deduper.first("file:DOWNLOAD_OK", 500));
        assertTrue(deduper.first("file:DOWNLOAD_OK", 1_000));
        assertTrue(deduper.first("file:UPLOAD_OK", 1_000));
    }

    @Test
    void fileJsonSkipsThumbnailsAndKeepsAlbumItems() {
        JsonArray files = new JsonArray()
                .add(new JsonObject().put("id", 1).put("chatId", -100).put("messageId", 10).put("type", "thumbnail").put("fileName", "t.jpg").put("size", 1))
                .add(new JsonObject().put("id", 2).put("chatId", -100).put("messageId", 10).put("type", "video").put("fileName", "a.mp4").put("size", 50))
                .add(new JsonObject().put("id", 3).put("chatId", -100).put("messageId", 11).put("type", "photo").put("fileName", "b.jpg").put("size", 5));
        assertEquals(2, BotMedia.fromFiles(files).size());
        assertEquals("a.mp4", BotMedia.fromFiles(files).getFirst().name());
    }

    @Test
    void byteFormatAndCallbacks() {
        assertEquals("1.0 KB", ByteFormats.bytes(1024));
        assertEquals(50, ByteFormats.percent(50, 100));
        assertEquals(0, ByteFormats.percent(1, 0));
        assertTrue(BotCallbacks.parse("d:abc123:video").orElseThrow() instanceof BotCallbacks.DownloadChoice);
        assertTrue(BotCallbacks.parse("a:abc123:42").orElseThrow() instanceof BotCallbacks.AccountChoice);
        assertTrue(BotCallbacks.parse("nope").isEmpty());
        assertEquals("status", BotCommands.parse("/status@bot extra").orElseThrow());
        assertTrue(BotCommands.parse("https://t.me/a/1").isEmpty());
    }
}
