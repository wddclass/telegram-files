package telegram.files.bot;

import java.util.List;

public final class BotModels {

    private BotModels() {
    }

    public record BotAccount(long id, String name, boolean authorized, String stateKey) {
    }

    public record DownloadFileView(
            String accountName,
            String name,
            int percent,
            long downloaded,
            long size,
            long speedBytesPerSec,
            boolean progressKnown
    ) {
    }

    public record DownloadReport(
            List<DownloadFileView> downloading,
            int downloadingCount,
            int queued,
            int paused,
            int failed,
            boolean truncated
    ) {
        public DownloadReport {
            downloading = downloading == null ? List.of() : List.copyOf(downloading);
        }

        public static DownloadReport empty() {
            return new DownloadReport(List.of(), 0, 0, 0, 0, false);
        }
    }

    public record UploadFileView(String name, long size, boolean progressKnown, int percent) {
    }

    public record UploadReport(
            List<UploadFileView> uploading,
            int uploadingCount,
            int pending,
            int failed,
            List<UploadFileView> recent,
            boolean rcloneEnabled
    ) {
        public UploadReport {
            uploading = uploading == null ? List.of() : List.copyOf(uploading);
            recent = recent == null ? List.of() : List.copyOf(recent);
        }

        public static UploadReport empty(boolean rcloneEnabled) {
            return new UploadReport(List.of(), 0, 0, 0, List.of(), rcloneEnabled);
        }
    }

    public record BotDownloadResult(int started, int skipped, int failed, List<String> errors) {
        public BotDownloadResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }
}
