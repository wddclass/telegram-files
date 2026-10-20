package telegram.files.bot;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import io.vertx.core.Future;
import org.drinkless.tdlib.TdApi;
import telegram.files.DataVerticle;
import telegram.files.TdApiHelp;
import telegram.files.TelegramVerticle;
import telegram.files.TelegramVerticles;
import telegram.files.bot.BotModels.BotAccount;
import telegram.files.bot.BotModels.BotDownloadResult;
import telegram.files.bot.BotModels.DownloadFileView;
import telegram.files.bot.BotModels.DownloadReport;
import telegram.files.bot.BotModels.UploadFileView;
import telegram.files.bot.BotModels.UploadReport;
import telegram.files.repository.FileRecord;
import telegram.files.repository.SettingKey;
import telegram.files.repository.SettingRclone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapts the running Telegram accounts, the download pipeline, and rclone rows to the bot.
 */
public final class BotRuntime implements BotBackend {

    private static final Log log = LogFactory.get();

    private record AccountSlice(List<DownloadFileView> downloading, int queued, int paused, int failed, boolean truncated) {
        static AccountSlice empty() {
            return new AccountSlice(List.of(), 0, 0, 0, false);
        }
    }

    @Override
    public List<BotAccount> accounts() {
        List<BotAccount> accounts = new ArrayList<>();
        for (TelegramVerticle verticle : TelegramVerticles.getAll()) {
            accounts.add(basicAccount(verticle));
        }
        return accounts;
    }

    @Override
    public Future<List<BotAccount>> accountSnapshots() {
        List<TelegramVerticle> verticles = TelegramVerticles.getAll();
        if (verticles.isEmpty()) {
            return Future.succeededFuture(List.of());
        }
        List<Future<BotAccount>> futures = verticles.stream().map(this::snapshot).toList();
        return Future.all(futures).map(composite -> {
            List<BotAccount> accounts = new ArrayList<>();
            for (int i = 0; i < composite.size(); i++) {
                accounts.add(composite.resultAt(i));
            }
            return accounts;
        });
    }

    @Override
    public Future<List<BotMedia>> parseLink(long accountId, String link) {
        return TelegramVerticles.get(accountId)
                .map(verticle -> {
                    if (!verticle.authorized) {
                        return Future.<List<BotMedia>>failedFuture("账号未授权");
                    }
                    // parseLink loads the linked message and, for albums, the sibling media.
                    return verticle.parseLink(link)
                            .map(json -> BotMedia.fromFiles(json.getJsonArray("files")));
                })
                .orElseGet(() -> Future.failedFuture("账号不存在"));
    }

    @Override
    public Future<BotDownloadResult> startDownloads(long accountId, List<BotMedia> files) {
        return TelegramVerticles.get(accountId)
                .map(verticle -> {
                    if (!verticle.authorized) {
                        return Future.<BotDownloadResult>failedFuture("账号未授权");
                    }
                    return startNext(verticle, files == null ? List.of() : files, 0, 0, 0, 0, new ArrayList<>());
                })
                .orElseGet(() -> Future.failedFuture("账号不存在"));
    }

    @Override
    public Future<DownloadReport> downloadReport() {
        List<Future<AccountSlice>> slices = new ArrayList<>();
        for (TelegramVerticle verticle : TelegramVerticles.getAll()) {
            if (verticle.telegramRecord == null) {
                continue;
            }
            slices.add(slice(verticle));
        }
        if (slices.isEmpty()) {
            return Future.succeededFuture(DownloadReport.empty());
        }
        return Future.all(slices).map(composite -> {
            List<DownloadFileView> downloading = new ArrayList<>();
            int queued = 0;
            int paused = 0;
            int failed = 0;
            boolean truncated = false;
            for (int i = 0; i < composite.size(); i++) {
                AccountSlice slice = composite.resultAt(i);
                if (slice == null) {
                    continue;
                }
                downloading.addAll(slice.downloading);
                queued += slice.queued;
                paused += slice.paused;
                failed += slice.failed;
                truncated = truncated || slice.truncated;
            }
            return new DownloadReport(downloading, downloading.size(), queued, paused, failed, truncated);
        });
    }

    @Override
    public Future<UploadReport> uploadReport() {
        if (DataVerticle.fileRepository == null) {
            return Future.succeededFuture(UploadReport.empty(false));
        }
        Future<Boolean> enabled = DataVerticle.settingRepository == null
                ? Future.succeededFuture(false)
                : DataVerticle.settingRepository.<SettingRclone>getByKey(SettingKey.rclone)
                .map(settings -> settings != null && settings.enabled)
                .recover(err -> {
                    log.warn("Failed to read rclone settings: %s".formatted(err.getMessage()));
                    return Future.succeededFuture(false);
                });
        return enabled.compose(rcloneEnabled -> Future.all(
                DataVerticle.fileRepository.listRcloneStatus(FileRecord.RcloneStatus.uploading.name(), 10),
                DataVerticle.fileRepository.countRcloneStatus(FileRecord.RcloneStatus.uploading.name()),
                DataVerticle.fileRepository.countRcloneStatus(FileRecord.RcloneStatus.pending.name()),
                DataVerticle.fileRepository.countRcloneStatus(FileRecord.RcloneStatus.failed.name()),
                DataVerticle.fileRepository.listRcloneStatus(FileRecord.RcloneStatus.uploaded.name(), 5)
        ).map(composite -> {
            List<FileRecord> uploading = composite.resultAt(0);
            List<FileRecord> recent = composite.resultAt(4);
            return new UploadReport(
                    views(uploading),
                    composite.resultAt(1),
                    composite.resultAt(2),
                    composite.resultAt(3),
                    views(recent),
                    rcloneEnabled
            );
        }));
    }

    private Future<BotAccount> snapshot(TelegramVerticle verticle) {
        BotAccount basic = basicAccount(verticle);
        if (!verticle.authorized) {
            return Future.succeededFuture(basic);
        }
        return verticle.getTelegramAccount()
                .map(json -> {
                    long id = basic.id();
                    try {
                        if (json.getString("id") != null) {
                            id = Long.parseLong(json.getString("id"));
                        }
                    } catch (NumberFormatException ignored) {
                        id = basic.id();
                    }
                    String name = json.getString("name");
                    if (name == null || name.isBlank()) {
                        name = basic.name();
                    }
                    return new BotAccount(id, name, true, "ready");
                })
                .recover(err -> Future.succeededFuture(basic));
    }

    private static BotAccount basicAccount(TelegramVerticle verticle) {
        long id = verticle.telegramRecord == null ? 0 : verticle.telegramRecord.id();
        String name = verticle.telegramRecord == null ? null : verticle.telegramRecord.firstName();
        if (name == null || name.isBlank()) {
            name = verticle.getRootId();
        }
        return new BotAccount(id, name, verticle.authorized, BotAccounts.stateKey(verticle.lastAuthorizationState, verticle.authorized));
    }

    private Future<AccountSlice> slice(TelegramVerticle verticle) {
        Future<AccountSlice> future = verticle.authorized && verticle.client != null
                ? liveSlice(verticle)
                : dbSlice(verticle);
        return future.recover(err -> {
            log.warn("Download status for %s failed: %s".formatted(verticle.getRootId(), err.getMessage()));
            return Future.succeededFuture(AccountSlice.empty());
        });
    }

    private Future<AccountSlice> liveSlice(TelegramVerticle verticle) {
        return verticle.client.execute(new TdApi.SearchFileDownloads("", true, false, "", 40))
                .compose(found -> classify(verticle, found))
                .recover(err -> {
                    log.warn("SearchFileDownloads failed for %s: %s".formatted(verticle.getRootId(), err.getMessage()));
                    return dbSlice(verticle);
                });
    }

    private Future<AccountSlice> classify(TelegramVerticle verticle, TdApi.FoundFileDownloads found) {
        TdApi.FileDownload[] files = found == null || found.files == null ? new TdApi.FileDownload[0] : found.files;
        int paused = found != null && found.totalCounts != null ? found.totalCounts.pausedCount : 0;
        boolean truncated = found != null && StrUtil.isNotBlank(found.nextOffset);
        List<Future<DownloadFileView>> views = new ArrayList<>();
        int fetchedNonPaused = 0;
        for (TdApi.FileDownload download : files) {
            if (download == null || download.isPaused) {
                continue;
            }
            fetchedNonPaused++;
            views.add(activeView(verticle, download));
        }
        int known = fetchedNonPaused;
        Future<List<DownloadFileView>> collected = views.isEmpty()
                ? Future.succeededFuture(List.of())
                : Future.all(views).map(composite -> {
                    List<DownloadFileView> active = new ArrayList<>();
                    for (int i = 0; i < composite.size(); i++) {
                        DownloadFileView view = composite.resultAt(i);
                        if (view != null) {
                            active.add(view);
                        }
                    }
                    return active;
                });
        return collected.compose(active -> failedCount(verticle).map(failed -> {
            int queued = Math.max(0, known - active.size());
            if (truncated && found.totalCounts != null) {
                queued = Math.max(queued, found.totalCounts.activeCount - paused - active.size());
            }
            return new AccountSlice(active, queued, paused, failed, truncated);
        }));
    }

    private Future<DownloadFileView> activeView(TelegramVerticle verticle, TdApi.FileDownload download) {
        String name = fileName(verticle, download);
        String account = accountName(verticle);
        long speed = verticle.currentDownloadSpeed();
        return verticle.client.<TdApi.File>execute(new TdApi.GetFile(download.fileId))
                .map(file -> {
                    if (file == null || file.local == null || !file.local.isDownloadingActive) {
                        return null;
                    }
                    long size = file.expectedSize > 0 ? file.expectedSize : file.size;
                    long downloaded = file.local.downloadedSize;
                    return new DownloadFileView(account, name, ByteFormats.percent(downloaded, size),
                            downloaded, size, speed, size > 0);
                })
                .recover(err -> Future.succeededFuture(null));
    }

    private Future<AccountSlice> dbSlice(TelegramVerticle verticle) {
        if (DataVerticle.fileRepository == null || verticle.telegramRecord == null) {
            return Future.succeededFuture(AccountSlice.empty());
        }
        long telegramId = verticle.telegramRecord.id();
        String account = accountName(verticle);
        return Future.all(
                DataVerticle.fileRepository.getByDownloadStatus(telegramId, FileRecord.DownloadStatus.downloading),
                DataVerticle.fileRepository.countByStatus(telegramId, FileRecord.DownloadStatus.paused),
                DataVerticle.fileRepository.countByStatus(telegramId, FileRecord.DownloadStatus.error)
        ).map(composite -> {
            List<FileRecord> records = composite.resultAt(0);
            int paused = composite.resultAt(1);
            int failed = composite.resultAt(2);
            List<DownloadFileView> views = new ArrayList<>();
            if (records != null) {
                for (FileRecord record : records) {
                    if (views.size() >= 20) {
                        break;
                    }
                    views.add(new DownloadFileView(account, displayName(record), 0, record.downloadedSize(),
                            record.size(), 0, false));
                }
            }
            return new AccountSlice(views, 0, paused, failed, records != null && records.size() > views.size());
        });
    }

    private Future<Integer> failedCount(TelegramVerticle verticle) {
        if (DataVerticle.fileRepository == null || verticle.telegramRecord == null) {
            return Future.succeededFuture(0);
        }
        return DataVerticle.fileRepository.countByStatus(verticle.telegramRecord.id(), FileRecord.DownloadStatus.error)
                .recover(err -> Future.succeededFuture(0));
    }

    private Future<BotDownloadResult> startNext(TelegramVerticle verticle,
                                                List<BotMedia> files,
                                                int index,
                                                int started,
                                                int skipped,
                                                int failed,
                                                List<String> errors) {
        if (index >= files.size()) {
            return Future.succeededFuture(new BotDownloadResult(started, skipped, failed, errors));
        }
        BotMedia media = files.get(index);
        return verticle.startDownload(media.chatId(), media.messageId(), media.fileId())
                .map(ignored -> (String) null)
                .recover(err -> Future.succeededFuture(err.getMessage() == null ? "error" : err.getMessage()))
                .compose(failure -> {
                    int nextStarted = started;
                    int nextSkipped = skipped;
                    int nextFailed = failed;
                    if (failure == null) {
                        nextStarted++;
                    } else if (skippedOutcome(failure)) {
                        nextSkipped++;
                    } else {
                        nextFailed++;
                        if (errors.size() < 3) {
                            errors.add(media.name() + ": " + failure);
                        }
                    }
                    return startNext(verticle, files, index + 1, nextStarted, nextSkipped, nextFailed, errors);
                });
    }

    private static boolean skippedOutcome(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("downloading") || lower.contains("already");
    }

    private static String fileName(TelegramVerticle verticle, TdApi.FileDownload download) {
        if (download.message == null) {
            return "file-" + download.fileId;
        }
        try {
            long telegramId = verticle.telegramRecord == null ? 0 : verticle.telegramRecord.id();
            return TdApiHelp.getFileHandlers(download.message).stream()
                    .filter(handler -> Objects.equals(handler.getFileId(), download.fileId))
                    .findFirst()
                    .or(() -> TdApiHelp.getFileHandler(download.message))
                    .map(handler -> handler.convertFileRecord(telegramId))
                    .map(record -> displayName(record))
                    .orElse("file-" + download.fileId);
        } catch (RuntimeException e) {
            return "file-" + download.fileId;
        }
    }

    private static String accountName(TelegramVerticle verticle) {
        if (verticle.telegramRecord != null && StrUtil.isNotBlank(verticle.telegramRecord.firstName())) {
            return verticle.telegramRecord.firstName();
        }
        return verticle.getRootId();
    }

    private static String displayName(FileRecord record) {
        if (record == null) {
            return "file";
        }
        if (StrUtil.isNotBlank(record.fileName())) {
            return record.fileName();
        }
        if (StrUtil.isNotBlank(record.type())) {
            return record.type();
        }
        return "file";
    }

    private static List<UploadFileView> views(List<FileRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<UploadFileView> views = new ArrayList<>();
        for (FileRecord record : records) {
            views.add(new UploadFileView(displayName(record), record.size(), false, 0));
        }
        return views;
    }
}
