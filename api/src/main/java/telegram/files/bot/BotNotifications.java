package telegram.files.bot;

import io.vertx.core.json.JsonObject;

import java.util.Optional;

/**
 * Decides which file-status events are worth a notification. Progress updates are ignored.
 */
public final class BotNotifications {

    public enum Kind {
        DOWNLOAD_OK,
        DOWNLOAD_FAIL,
        UPLOAD_OK,
        UPLOAD_FAIL
    }

    public record Notice(Kind kind, String uniqueId, String error) {
    }

    private BotNotifications() {
    }

    public static Optional<Notice> from(JsonObject data) {
        if (data == null) {
            return Optional.empty();
        }
        String uniqueId = data.getString("uniqueId");
        if (uniqueId == null || uniqueId.isBlank()) {
            return Optional.empty();
        }
        String rcloneStatus = data.getString("rcloneStatus");
        if ("uploaded".equals(rcloneStatus)) {
            return Optional.of(new Notice(Kind.UPLOAD_OK, uniqueId, null));
        }
        if ("failed".equals(rcloneStatus)) {
            return Optional.of(new Notice(Kind.UPLOAD_FAIL, uniqueId, data.getString("rcloneError")));
        }
        String downloadStatus = data.getString("downloadStatus");
        if ("completed".equals(downloadStatus)) {
            return Optional.of(new Notice(Kind.DOWNLOAD_OK, uniqueId, null));
        }
        if ("error".equals(downloadStatus)) {
            return Optional.of(new Notice(Kind.DOWNLOAD_FAIL, uniqueId, data.getString("error")));
        }
        return Optional.empty();
    }
}
