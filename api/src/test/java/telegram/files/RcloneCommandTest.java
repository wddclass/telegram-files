package telegram.files;

import org.junit.jupiter.api.Test;
import telegram.files.repository.SettingRclone;

import java.nio.file.Path;
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
}
