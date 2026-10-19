package telegram.files;

import org.junit.jupiter.api.Test;
import telegram.files.repository.SettingRclone;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RcloneCommandTest {

    @Test
    void defaultsStayOffAndOnlyIncludeVideos() {
        SettingRclone settings = SettingRclone.defaults();
        assertFalse(settings.enabled);
        assertTrue(settings.accepts("video"));
        assertFalse(settings.accepts("photo"));
        assertFalse(settings.accepts("thumbnail"));
        assertEquals("/app/data/rclone/rclone.conf", settings.resolvedConfigPath(null));
        assertEquals("/tmp/app/rclone/rclone.conf", settings.resolvedConfigPath("/tmp/app"));
    }

    @Test
    void commandUsesCopyOrMoveWithoutAShell() {
        SettingRclone settings = SettingRclone.defaults();
        settings.remote = "gdrive:telegram";
        settings.extraFlags = "--transfers 4 --exclude \"*.jpg\"";
        List<String> copy = RcloneCommand.build(settings, "/app/data", Path.of("/data/clip.mp4"), 5, 9);
        assertEquals("rclone", copy.getFirst());
        assertEquals("copyto", copy.get(1));
        assertEquals("--config", copy.get(2));
        assertEquals("/app/data/rclone/rclone.conf", copy.get(3));
        assertEquals("/data/clip.mp4", copy.get(4));
        assertEquals("gdrive:telegram/5/9/clip.mp4", copy.get(5));
        assertEquals(List.of("--transfers", "4", "--exclude", "*.jpg"), copy.subList(6, copy.size()));
        assertFalse(copy.contains("sh"));
        assertFalse(copy.contains("bash"));

        settings.operation = "move";
        assertEquals("moveto", RcloneCommand.build(settings, "/app/data", Path.of("/data/clip.mp4"), 5, 9).get(1));
    }

    @Test
    void rejectsEmptyRemoteAndUnclosedFlags() {
        SettingRclone settings = SettingRclone.defaults();
        assertThrows(IllegalArgumentException.class,
                () -> RcloneCommand.build(settings, "/app/data", Path.of("/data/a.mp4"), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> RcloneCommand.parseFlags("--exclude \"*.mp4"));
    }

    @Test
    void intervalAndCronDecideWhenAScheduledRunIsDue() {
        long start = 1_000L;
        assertFalse(RcloneCommand.isDue("3600", start, start + 3_599_000L));
        assertTrue(RcloneCommand.isDue("3600", start, start + 3_600_000L));
        assertTrue(RcloneCommand.isDue("10", start, start + 30_000L));

        ZonedDateTime due = ZonedDateTime.of(2026, 10, 19, 6, 0, 10, 0, ZoneId.systemDefault());
        long dueMs = due.toInstant().toEpochMilli();
        assertTrue(RcloneCommand.isDue("0 */6 * * *", 0, dueMs));
        assertFalse(RcloneCommand.isDue("0 */6 * * *", dueMs, due.plusSeconds(20).toInstant().toEpochMilli()));
        assertFalse(RcloneCommand.isDue("0 */6 * * *", 0, due.withMinute(15).toInstant().toEpochMilli()));
        assertFalse(RcloneCommand.isDue("not a schedule", 0, dueMs));
    }

    @Test
    void blankTemplateAndOldSettingsKeepTheCurrentLayout() {
        SettingRclone parsed = SettingRclone.fromJson("{\"enabled\":true,\"remote\":\"gdrive:telegram\"}");
        assertEquals(RclonePaths.DEFAULT_TEMPLATE, parsed.pathTemplate);
        assertEquals("", parsed.timeZone);
        assertEquals("gdrive:telegram/5/9/clip.mp4",
                RcloneCommand.destination("gdrive:telegram", 5, 9, "clip.mp4"));
    }

    @Test
    void pathTemplateUsesCompletionDateAccountNameAndUsername() {
        long completion = Instant.parse("2026-10-19T16:30:00Z").toEpochMilli();
        RclonePaths.Values values = new RclonePaths.Values(
                4242,
                "𝘄𝗱𝗱𝘇𝘇𝘇",
                "@wdd_user",
                -1001,
                "News/Room",
                99,
                "video",
                "clip.mp4",
                completion);
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        assertEquals("𝘄𝗱𝗱𝘇𝘇𝘇-20261020/clip.mp4",
                RclonePaths.render("{accountName}-{date:yyyyMMdd}/{fileName}", shanghai, values));
        assertEquals("2026-10-19",
                RclonePaths.render("{date:yyyy-MM-dd}", ZoneId.of("UTC"), values));
        assertEquals("2026/10/20/clip.mp4",
                RclonePaths.render("{date:yyyy/MM/dd}/{fileName}", shanghai, values));
        assertEquals("News_Room", RclonePaths.render("{chatTitle}", shanghai, values));
        assertEquals("wdd_user", RclonePaths.render("{accountUsername}", shanghai, values));
        assertEquals("4242", RclonePaths.render("{accountUsername}", shanghai,
                new RclonePaths.Values(4242, "𝘄𝗱𝗱𝘇𝘇𝘇", null, 1, null, 1, "video", "a.mp4", completion)));
        assertTrue(RclonePaths.render("{accountName}", shanghai, values).contains("𝘄"));

        SettingRclone settings = SettingRclone.defaults();
        settings.remote = "googledrive:WC/CC";
        settings.pathTemplate = "{accountName}-{date:yyyyMMdd}/{fileName}";
        settings.timeZone = "Asia/Shanghai";
        String destination = RcloneCommand.destination(settings, values);
        assertEquals("googledrive:WC/CC/𝘄𝗱𝗱𝘇𝘇𝘇-20261020/clip.mp4", destination);
        RclonePaths.Values sameName = new RclonePaths.Values(
                7, "𝘄𝗱𝗱𝘇𝘇𝘇", "other", 8, "Other", 100, "video", "clip.mp4", completion);
        assertEquals(destination, RcloneCommand.destination(settings, sameName));
    }

    @Test
    void pathTemplateRejectsUnsafeSegmentsAndBadPatterns() {
        RclonePaths.Values values = new RclonePaths.Values(1, "A/B:C", "ok", 2, null, 3, "video", "clip.mp4", 0L);
        assertEquals("A_B_C", RclonePaths.sanitizeSegment("A/B:C"));
        assertEquals("A_B_C", RclonePaths.render("{accountName}", ZoneId.of("UTC"), values));
        assertThrows(IllegalArgumentException.class,
                () -> RclonePaths.render("../{fileName}", ZoneId.of("UTC"), values));
        assertThrows(IllegalArgumentException.class,
                () -> RclonePaths.render("{date:}", ZoneId.of("UTC"), values));
        assertThrows(IllegalArgumentException.class, () -> RclonePaths.resolveZone("Not/AZone"));
        assertEquals(ZoneId.of("UTC"), RclonePaths.resolveZone("UTC"));
    }
}
