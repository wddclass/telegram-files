package telegram.files.bot;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import telegram.files.bot.BotModels.BotAccount;
import telegram.files.bot.BotModels.BotDownloadResult;
import telegram.files.bot.BotModels.DownloadFileView;
import telegram.files.bot.BotModels.DownloadReport;
import telegram.files.bot.BotModels.UploadFileView;
import telegram.files.bot.BotModels.UploadReport;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BotUpdateHandlerTest {

    @Test
    void emptyAllowlistRejectsEveryoneAndDoesNotReply() {
        Harness harness = harness("", true);
        harness.handler.onUpdate(messageUpdate(7, "steven", 7, "private", "/status"));
        harness.handler.onUpdate(messageUpdate(7, "steven", -100, "supergroup", "https://t.me/c/1400/9"));
        assertTrue(harness.gateway.sent.isEmpty());
        assertTrue(harness.backend.parsedLinks.isEmpty());
    }

    @Test
    void numericAndUsernameAllowlistRejectsEveryoneElseIncludingGroups() {
        Harness harness = harness("42, @Steven", true);
        harness.handler.onUpdate(messageUpdate(7, "intruder", -1001, "group", "/downloads https://t.me/news/5"));
        harness.handler.onUpdate(callbackUpdate(7, "intruder", -1001, "group", "d:abcdef:all", "cb-1"));
        assertTrue(harness.gateway.sent.isEmpty());
        assertEquals(List.of("cb-1"), harness.gateway.answered);
        assertTrue(harness.backend.started.isEmpty());
        assertTrue(harness.backend.parsedLinks.isEmpty());
    }

    @Test
    void usernameMatchIsCaseInsensitiveAndRepliesInPrivateWhenTheUpdateIsFromAGroup() {
        Harness harness = harness("@Steven", true);
        harness.handler.onUpdate(messageUpdate(99, "steven", -1005, "supergroup", "/help"));
        assertEquals(1, harness.gateway.sent.size());
        assertEquals(99, harness.gateway.sent.getFirst().chatId());
        assertTrue(harness.gateway.sent.getFirst().text().contains("/downloads"));
        assertTrue(harness.remembered.isEmpty());
    }

    @Test
    void helpStatusDownloadsAndUploadsUseChineseByDefault() {
        Harness harness = harness("99", true);
        harness.backend.downloadReport = new DownloadReport(List.of(
                new DownloadFileView("账号A", "clip.mp4", 40, 40, 100, 2048, true)
        ), 1, 2, 3, 4, false);
        harness.backend.uploadReport = new UploadReport(List.of(
                new UploadFileView("clip.mp4", 100, false, 0)
        ), 1, 5, 1, List.of(new UploadFileView("done.mp4", 10, false, 0)), true);
        harness.backend.snapshots = List.of(new BotAccount(99, "张三", true, "ready"));

        harness.handler.onUpdate(messageUpdate(99, null, 99, "private", "/status"));
        harness.handler.onUpdate(messageUpdate(99, null, 99, "private", "/downloads@filesbot"));
        harness.handler.onUpdate(messageUpdate(99, null, 99, "private", "/uploads"));

        assertEquals(3, harness.gateway.sent.size());
        assertTrue(harness.gateway.sent.get(0).text().contains("已授权"));
        assertTrue(harness.gateway.sent.get(0).text().contains("张三"));
        assertTrue(harness.gateway.sent.get(1).text().contains("40%"));
        assertTrue(harness.gateway.sent.get(1).text().contains("队列 2"));
        assertTrue(harness.gateway.sent.get(1).text().contains("已暂停 3"));
        assertTrue(harness.gateway.sent.get(1).text().contains("失败 4"));
        assertTrue(harness.gateway.sent.get(2).text().contains("等待 5"));
        assertTrue(harness.gateway.sent.get(2).text().contains("done.mp4"));
        assertTrue(harness.gateway.sent.get(2).text().contains("实时进度不可用"));
        assertTrue(harness.remembered.stream().allMatch(id -> id == 99L));
        assertFalse(harness.remembered.isEmpty());
    }

    @Test
    void englishRepliesWhenLanguageIsEnglish() {
        Harness harness = harness("99", false);
        harness.handler.onUpdate(messageUpdate(99, null, 99, "private", "/start"));
        assertTrue(harness.gateway.sent.getFirst().text().contains("/downloads"));
        assertTrue(harness.gateway.sent.getFirst().text().contains("private chat"));
        assertFalse(harness.gateway.sent.getFirst().text().contains("下载"));
    }

    @Test
    void privateChannelAndPublicLinksArePassedToTheAlbumAwareParser() {
        Harness harness = harness("5", true);
        harness.backend.accounts = List.of(new BotAccount(5, "only", true, "ready"));
        harness.backend.media = List.of(
                new BotMedia(11, -100, 20, "video", "a.mp4", 1000),
                new BotMedia(12, -100, 21, "photo", "b.jpg", 2000),
                new BotMedia(13, -100, 22, "video", "c.mp4", 3000)
        );

        harness.handler.onUpdate(messageUpdate(5, null, 5, "private",
                "相册 https://t.me/c/1401234567/99?single 看一下"));
        assertEquals("https://t.me/c/1401234567/99?single", harness.backend.parsedLinks.getFirst());
        assertEquals(5, harness.backend.parsedAccounts.getFirst());
        String listing = harness.gateway.sent.getFirst().text();
        assertTrue(listing.contains("a.mp4"));
        assertTrue(listing.contains("b.jpg"));
        assertTrue(listing.contains("c.mp4"));
        assertTrue(listing.contains("视频"));
        assertTrue(listing.contains("图片"));

        String onlyVideos = callbackData(harness.gateway.sent.getFirst().markup(), "仅视频");
        harness.handler.onUpdate(callbackUpdate(5, null, 5, "private", onlyVideos, "go"));
        assertEquals(List.of(11, 13), harness.backend.started.stream().map(BotMedia::fileId).toList());
        assertTrue(harness.gateway.sent.get(1).text().contains("已开始下载 2"));
    }

    @Test
    void publicUserLinkAndIndividualPhotoSelection() {
        Harness harness = harness("5", true);
        harness.backend.accounts = List.of(new BotAccount(8, "main", true, "ready"));
        harness.backend.media = List.of(
                new BotMedia(1, 10, 20, "photo", "one.jpg", 10),
                new BotMedia(2, 10, 21, "file", "notes.pdf", 20)
        );
        harness.handler.onUpdate(messageUpdate(5, null, 5, "private", "https://telegram.me/some_user/321"));
        assertEquals("https://t.me/some_user/321", harness.backend.parsedLinks.getFirst());
        String one = callbackData(harness.gateway.sent.getFirst().markup(), "1. 图片 one.jpg");
        harness.handler.onUpdate(callbackUpdate(5, null, 5, "private", one, "one"));
        assertEquals(1, harness.backend.started.size());
        assertEquals(1, harness.backend.started.getFirst().fileId());
    }

    @Test
    void multipleAccountsAskBeforeParsingUnlessADefaultIsConfigured() {
        Harness harness = new Harness(BotAllowlist.parse("5"), true, null);
        harness.backend.accounts = List.of(
                new BotAccount(1, "alpha", true, "ready"),
                new BotAccount(2, "beta", false, "wait_code"),
                new BotAccount(3, "gamma", true, "ready")
        );
        harness.handler.onUpdate(messageUpdate(5, null, 5, "private", "https://t.me/news/7"));
        assertTrue(harness.backend.parsedLinks.isEmpty());
        assertTrue(harness.gateway.sent.getFirst().text().contains("多个已登录账号"));
        String gamma = callbackData(harness.gateway.sent.getFirst().markup(), "gamma");
        harness.backend.media = List.of(new BotMedia(9, 1, 2, "video", "v.mp4", 5));
        harness.handler.onUpdate(callbackUpdate(5, null, 5, "private", gamma, "pick"));
        assertEquals(3, harness.backend.parsedAccounts.getFirst());
        assertEquals("https://t.me/news/7", harness.backend.parsedLinks.getFirst());

        Harness withDefault = new Harness(BotAllowlist.parse("5"), true, "1");
        withDefault.backend.accounts = harness.backend.accounts;
        withDefault.backend.media = harness.backend.media;
        withDefault.handler.onUpdate(messageUpdate(5, null, 5, "private", "https://t.me/news/8"));
        assertEquals(List.of(1L), withDefault.backend.parsedAccounts);
        assertEquals("https://t.me/news/8", withDefault.backend.parsedLinks.getFirst());
    }

    @Test
    void anotherAllowedUserCannotUseSomeoneElsesDownloadButton() {
        Harness harness = harness("5,6", true);
        harness.backend.accounts = List.of(new BotAccount(1, "only", true, "ready"));
        harness.backend.media = List.of(new BotMedia(4, 1, 2, "video", "v.mp4", 5));
        harness.handler.onUpdate(messageUpdate(5, null, 5, "private", "https://t.me/news/1"));
        String all = callbackData(harness.gateway.sent.getFirst().markup(), "全部下载");
        harness.handler.onUpdate(callbackUpdate(6, "other", 6, "private", all, "steal"));
        assertTrue(harness.backend.started.isEmpty());
        assertTrue(harness.gateway.sent.stream().anyMatch(sent -> sent.text().contains("过期")));
    }

    @Test
    void hiddenTextLinkIsAccepted() {
        Harness harness = harness("5", true);
        harness.backend.accounts = List.of(new BotAccount(1, "only", true, "ready"));
        JsonObject message = new JsonObject()
                .put("message_id", 3)
                .put("from", new JsonObject().put("id", 5).put("is_bot", false))
                .put("chat", new JsonObject().put("id", 5).put("type", "private"))
                .put("text", "看这个")
                .put("entities", new JsonArray().add(new JsonObject()
                        .put("type", "text_link")
                        .put("offset", 0)
                        .put("length", 3)
                        .put("url", "https://t.me/durov/100")));
        harness.handler.onUpdate(new JsonObject().put("update_id", 1).put("message", message));
        assertEquals(List.of("https://t.me/durov/100"), harness.backend.parsedLinks);
    }

    private static String callbackData(JsonObject markup, String textPart) {
        JsonArray rows = markup.getJsonArray("inline_keyboard");
        for (int i = 0; i < rows.size(); i++) {
            JsonArray row = rows.getJsonArray(i);
            for (int j = 0; j < row.size(); j++) {
                JsonObject button = row.getJsonObject(j);
                if (button.getString("text").contains(textPart)) {
                    return button.getString("callback_data");
                }
            }
        }
        throw new AssertionError("missing button " + textPart);
    }

    private static Harness harness(String allowlist, boolean chinese) {
        return new Harness(BotAllowlist.parse(allowlist), chinese, null);
    }

    private static JsonObject messageUpdate(long userId, String username, long chatId, String chatType, String text) {
        JsonObject from = new JsonObject().put("id", userId).put("is_bot", false);
        if (username != null) {
            from.put("username", username);
        }
        JsonObject message = new JsonObject()
                .put("message_id", 1)
                .put("from", from)
                .put("chat", new JsonObject().put("id", chatId).put("type", chatType))
                .put("text", text);
        return new JsonObject().put("update_id", 1).put("message", message);
    }

    private static JsonObject callbackUpdate(long userId, String username, long chatId, String chatType, String data, String id) {
        JsonObject from = new JsonObject().put("id", userId).put("is_bot", false);
        if (username != null) {
            from.put("username", username);
        }
        JsonObject callback = new JsonObject()
                .put("id", id)
                .put("from", from)
                .put("data", data)
                .put("message", new JsonObject()
                        .put("message_id", 10)
                        .put("chat", new JsonObject().put("id", chatId).put("type", chatType)));
        return new JsonObject().put("update_id", 2).put("callback_query", callback);
    }

    private static final class Harness {
        final RecordingGateway gateway = new RecordingGateway();
        final FakeBackend backend = new FakeBackend();
        final List<Long> remembered = new ArrayList<>();
        final BotUpdateHandler handler;

        Harness(BotAllowlist allowlist, boolean chinese, String defaultAccount) {
            handler = new BotUpdateHandler(allowlist, new BotTexts(chinese), defaultAccount, backend, gateway,
                    new BotSessions(), remembered::add);
        }
    }

    private static final class RecordingGateway implements BotGateway {
        final List<Sent> sent = new ArrayList<>();
        final List<String> answered = new ArrayList<>();

        record Sent(long chatId, Integer messageId, String text, JsonObject markup) {
        }

        @Override
        public void send(long chatId, String text, JsonObject replyMarkup) {
            sent.add(new Sent(chatId, null, text, replyMarkup));
        }

        @Override
        public void edit(long chatId, int messageId, String text, JsonObject replyMarkup) {
            sent.add(new Sent(chatId, messageId, text, replyMarkup));
        }

        @Override
        public void answer(String callbackId, String text) {
            answered.add(callbackId);
        }
    }

    private static final class FakeBackend implements BotBackend {
        List<BotAccount> accounts = List.of();
        List<BotAccount> snapshots = List.of();
        List<BotMedia> media = List.of();
        final List<String> parsedLinks = new ArrayList<>();
        final List<Long> parsedAccounts = new ArrayList<>();
        final List<BotMedia> started = new ArrayList<>();
        DownloadReport downloadReport = DownloadReport.empty();
        UploadReport uploadReport = UploadReport.empty(false);

        @Override
        public List<BotAccount> accounts() {
            return accounts;
        }

        @Override
        public Future<List<BotAccount>> accountSnapshots() {
            return Future.succeededFuture(snapshots.isEmpty() ? accounts : snapshots);
        }

        @Override
        public Future<List<BotMedia>> parseLink(long accountId, String link) {
            parsedAccounts.add(accountId);
            parsedLinks.add(link);
            return Future.succeededFuture(media);
        }

        @Override
        public Future<BotDownloadResult> startDownloads(long accountId, List<BotMedia> files) {
            started.addAll(files);
            return Future.succeededFuture(new BotDownloadResult(files.size(), 0, 0, List.of()));
        }

        @Override
        public Future<DownloadReport> downloadReport() {
            return Future.succeededFuture(downloadReport);
        }

        @Override
        public Future<UploadReport> uploadReport() {
            return Future.succeededFuture(uploadReport);
        }
    }

    @Test
    void photosOnlyAndAllFilters() {
        List<BotMedia> media = List.of(
                new BotMedia(1, 1, 1, "photo", "a", 1),
                new BotMedia(2, 1, 2, "video", "b", 1),
                new BotMedia(3, 1, 3, "photo", "c", 1)
        );
        assertEquals(List.of(1, 3), BotMedia.select(media, "photo").stream().map(BotMedia::fileId).toList());
        assertEquals(3, BotMedia.select(media, "all").size());
        assertTrue(BotMedia.select(media, "9").isEmpty());
    }

    @Test
    void accountSelectionUsesTheDefaultIdOrName() {
        List<BotAccount> accounts = List.of(
                new BotAccount(1, "Alpha", true, "ready"),
                new BotAccount(2, "Beta", true, "ready"),
                new BotAccount(3, "closed", false, "closed")
        );
        assertTrue(BotAccounts.select(accounts, null).needsChoice());
        assertEquals(1, BotAccounts.select(accounts, "1").chosen().id());
        assertEquals(2, BotAccounts.select(accounts, "beta").chosen().id());
        assertTrue(BotAccounts.select(accounts, "missing").defaultMissed());
        assertTrue(BotAccounts.select(List.of(), null).chosen() == null);
    }
}
