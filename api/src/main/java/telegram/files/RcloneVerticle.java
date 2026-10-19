package telegram.files;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import telegram.files.repository.FileRecord;
import telegram.files.repository.SettingKey;
import telegram.files.repository.SettingRclone;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uploads completed downloads with rclone. Work is queued off the download pipeline:
 * a download only publishes a status event, and this verticle copies or moves the file later.
 */
public class RcloneVerticle extends AbstractVerticle {

    private static final Log log = LogFactory.get();

    private static final int DRAIN_INTERVAL_MS = 2000;

    private static final int SCHEDULE_CHECK_MS = 30_000;

    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean draining = new AtomicBoolean(false);

    private volatile boolean stopped;

    private volatile long lastScheduledRunMs = System.currentTimeMillis();

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.eventBus().consumer(EventEnum.TELEGRAM_EVENT.address(), message -> {
            JsonObject body = (JsonObject) message.body();
            JsonObject payload = body.getJsonObject("payload");
            if (payload == null || payload.getInteger("type", 0) != EventPayload.TYPE_FILE_STATUS) {
                return;
            }
            JsonObject data = payload.getJsonObject("data");
            if (data == null || !FileRecord.DownloadStatus.completed.name().equals(data.getString("downloadStatus"))) {
                return;
            }
            String uniqueId = data.getString("uniqueId");
            if (StrUtil.isNotBlank(uniqueId)) {
                considerCompleted(uniqueId);
            }
        });
        vertx.eventBus().consumer(EventEnum.RCLONE_ENQUEUE.address(), message -> {
            String uniqueId = message.body() == null ? null : message.body().toString();
            if (StrUtil.isNotBlank(uniqueId)) {
                enqueue(uniqueId);
            }
        });
        vertx.eventBus().consumer(EventEnum.SETTING_UPDATE.address(SettingKey.rclone.name()), message -> {
            log.debug("Rclone settings updated");
            resumePending();
        });

        resumePending();
        vertx.setPeriodic(DRAIN_INTERVAL_MS, _ -> drain());
        vertx.setPeriodic(SCHEDULE_CHECK_MS, _ -> scheduledScan());
        log.info("Rclone verticle started");
        startPromise.complete();
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        stopped = true;
        stopPromise.complete();
    }

    private void considerCompleted(String uniqueId) {
        loadSettings().onSuccess(settings -> {
            if (settings == null || !settings.enabled) {
                return;
            }
            DataVerticle.fileRepository.getByUniqueId(uniqueId).onSuccess(record -> {
                if (!eligible(record, settings)) {
                    return;
                }
                // Status events are also emitted for rclone itself. Only a file that has
                // never been queued should enter the pipeline; retry uses RCLONE_ENQUEUE.
                if (!record.isRcloneStatus(null)) {
                    return;
                }
                DataVerticle.fileRepository.updateRcloneStatus(uniqueId, FileRecord.RcloneStatus.pending.name(), null)
                        .onSuccess(_ -> {
                            publishStatus(record, FileRecord.RcloneStatus.pending.name(), null);
                            if (!settings.scheduled()) {
                                enqueue(uniqueId);
                            }
                        });
            });
        });
    }

    private void scheduledScan() {
        if (stopped) {
            return;
        }
        loadSettings().onSuccess(settings -> {
            if (settings == null || !settings.enabled || !settings.scheduled()) {
                return;
            }
            long now = System.currentTimeMillis();
            if (!RcloneCommand.isDue(settings.schedule, lastScheduledRunMs, now)) {
                return;
            }
            lastScheduledRunMs = now;
            DataVerticle.fileRepository.markRcloneBacklog(List.copyOf(settings.acceptedTypes()))
                    .compose(_ -> DataVerticle.fileRepository.listRclonePending(100))
                    .onSuccess(records -> {
                        log.info("Scheduled rclone upload queued %d file(s)".formatted(records.size()));
                        records.forEach(record -> enqueue(record.uniqueId()));
                    })
                    .onFailure(err -> log.error("Scheduled rclone scan failed: %s".formatted(err.getMessage())));
        });
    }

    private void resumePending() {
        loadSettings().onSuccess(settings -> {
            if (settings == null || !settings.enabled) {
                return;
            }
            DataVerticle.fileRepository.resetInterruptedRcloneUploads()
                    .compose(_ -> DataVerticle.fileRepository.listRclonePending(100))
                    .onSuccess(records -> {
                        if (settings.scheduled()) {
                            return;
                        }
                        records.forEach(record -> enqueue(record.uniqueId()));
                    })
                    .onFailure(err -> log.warn("Failed to resume rclone uploads: %s".formatted(err.getMessage())));
        });
    }

    private void enqueue(String uniqueId) {
        if (!queue.contains(uniqueId)) {
            queue.add(uniqueId);
        }
        drain();
    }

    private void drain() {
        if (stopped || !draining.compareAndSet(false, true)) {
            return;
        }
        String uniqueId = queue.poll();
        if (uniqueId == null) {
            draining.set(false);
            return;
        }
        upload(uniqueId).onComplete(_ -> {
            draining.set(false);
            if (!queue.isEmpty()) {
                drain();
            }
        });
    }

    private Future<Void> upload(String uniqueId) {
        return loadSettings().compose(settings -> {
            if (settings == null || !settings.enabled || StrUtil.isBlank(settings.remote)) {
                return Future.succeededFuture();
            }
            return DataVerticle.fileRepository.getByUniqueId(uniqueId).compose(record -> {
                if (record == null || FileRecord.RcloneStatus.uploaded.name().equals(record.rcloneStatus())) {
                    return Future.succeededFuture();
                }
                if (!settings.accepts(record.type())) {
                    return Future.succeededFuture();
                }
                if (StrUtil.isBlank(record.localPath()) || !FileUtil.exist(record.localPath())) {
                    return fail(record, "Local file not found: " + record.localPath());
                }
                String configPath = settings.resolvedConfigPath(Config.APP_ROOT);
                if (!FileUtil.exist(configPath)) {
                    return fail(record, "rclone config not found: " + configPath);
                }
                List<String> command;
                try {
                    command = RcloneCommand.build(settings, Config.APP_ROOT, Path.of(record.localPath()), record.chatId(), record.messageId());
                } catch (RuntimeException e) {
                    return fail(record, e.getMessage());
                }
                return DataVerticle.fileRepository.updateRcloneStatus(uniqueId, FileRecord.RcloneStatus.uploading.name(), null)
                        .compose(_ -> {
                            publishStatus(record, FileRecord.RcloneStatus.uploading.name(), null);
                            return vertx.executeBlocking(() -> RcloneRunner.run(command, RcloneCommand.TIMEOUT));
                        })
                        .compose(result -> {
                            if (!result.success()) {
                                return fail(record, result.output());
                            }
                            log.info("Rclone uploaded %s".formatted(uniqueId));
                            return DataVerticle.fileRepository.updateRcloneStatus(uniqueId, FileRecord.RcloneStatus.uploaded.name(), null)
                                    .onSuccess(__ -> publishStatus(record, FileRecord.RcloneStatus.uploaded.name(), null))
                                    .mapEmpty();
                        });
            });
        }).recover(err -> {
            log.error("Rclone upload failed for %s: %s".formatted(uniqueId, err.getMessage()));
            return DataVerticle.fileRepository.updateRcloneStatus(uniqueId, FileRecord.RcloneStatus.failed.name(), err.getMessage())
                    .recover(updateErr -> {
                        log.warn("Failed to store rclone error for %s: %s".formatted(uniqueId, updateErr.getMessage()));
                        return Future.succeededFuture();
                    })
                    .mapEmpty();
        });
    }

    private Future<Void> fail(FileRecord record, String error) {
        log.warn("Rclone upload failed for %s: %s".formatted(record.uniqueId(), error));
        return DataVerticle.fileRepository.updateRcloneStatus(record.uniqueId(), FileRecord.RcloneStatus.failed.name(), error)
                .onSuccess(_ -> publishStatus(record, FileRecord.RcloneStatus.failed.name(), error))
                .mapEmpty();
    }

    private void publishStatus(FileRecord record, String status, String error) {
        // Omit downloadStatus. Listeners treat a completed download status as a new
        // download, which would queue the file again and retrigger auto-transfer.
        JsonObject data = new JsonObject()
                .put("fileId", record.id())
                .put("uniqueId", record.uniqueId())
                .put("localPath", record.localPath())
                .put("rcloneStatus", status)
                .put("rcloneError", error);
        vertx.eventBus().publish(EventEnum.TELEGRAM_EVENT.address(), JsonObject.of(
                "telegramId", Long.toString(record.telegramId()),
                "payload", JsonObject.mapFrom(EventPayload.build(EventPayload.TYPE_FILE_STATUS, data))
        ));
    }

    private static boolean eligible(FileRecord record, SettingRclone settings) {
        return record != null
                && record.isDownloadStatus(FileRecord.DownloadStatus.completed)
                && settings.accepts(record.type())
                && StrUtil.isNotBlank(record.localPath());
    }

    private Future<SettingRclone> loadSettings() {
        return DataVerticle.settingRepository.<SettingRclone>getByKey(SettingKey.rclone)
                .recover(err -> {
                    log.warn("Failed to load rclone settings: %s".formatted(err.getMessage()));
                    return Future.succeededFuture(SettingRclone.defaults());
                });
    }
}
