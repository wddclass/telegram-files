package telegram.files.bot;

import telegram.files.bot.BotModels.BotAccount;
import telegram.files.bot.BotModels.BotDownloadResult;
import telegram.files.bot.BotModels.DownloadFileView;
import telegram.files.bot.BotModels.DownloadReport;
import telegram.files.bot.BotModels.UploadFileView;
import telegram.files.bot.BotModels.UploadReport;

import java.util.List;
import java.util.Objects;

public final class BotTexts {

    private final boolean chinese;

    public BotTexts(boolean chinese) {
        this.chinese = chinese;
    }

    public boolean chinese() {
        return chinese;
    }

    public String help() {
        if (chinese) {
            return """
                    <b>Telegram Files 机器人</b>
                    命令：
                    /status — 已连接账号和授权状态
                    /downloads — 正在下载的文件、进度、速度，以及队列 / 暂停 / 失败数量
                    /uploads — rclone 上传状态
                    /help — 显示此帮助

                    把消息链接发给我即可下载。公开链接、私有频道链接和相册都会解析：
                    https://t.me/username/123
                    https://t.me/c/1234567890/123

                    解析后可以选择全部下载、仅视频、仅图片，或单个文件。下载走现有任务，已开启 rclone 时会自动上传。
                    有多个已登录账号时会让你选择；也可以用 TG_BOT_DEFAULT_ACCOUNT 指定默认账号。
                    请在私聊中使用。群里的请求不会在群内回复。""";
        }
        return """
                <b>Telegram Files bot</b>
                Commands:
                /status — connected accounts and authorization
                /downloads — active downloads, progress, speed, and queued / paused / failed counts
                /uploads — rclone upload status
                /help — this help

                Send a message link to download. Public links, private channel links, and albums are parsed:
                https://t.me/username/123
                https://t.me/c/1234567890/123

                Then choose all, videos only, photos only, or one item. Downloads use the existing pipeline, so rclone auto-upload applies when it is enabled.
                If several accounts are signed in, you pick one. TG_BOT_DEFAULT_ACCOUNT can skip that.
                Use a private chat. Group requests are not answered in the group.""";
    }

    public String unknown() {
        return chinese
                ? "发送一条 Telegram 消息链接，或使用 /help 查看命令。"
                : "Send a Telegram message link, or use /help.";
    }

    public String noAccount() {
        return chinese
                ? "没有已授权的 Telegram 账号。请先在网页里登录账号。"
                : "No authorized Telegram account. Sign in from the web UI first.";
    }

    public String pickAccount(boolean defaultMissed) {
        if (chinese) {
            return defaultMissed
                    ? "没有匹配到 TG_BOT_DEFAULT_ACCOUNT。请选择用于解析和下载的账号："
                    : "有多个已登录账号。请选择用于解析和下载的账号：";
        }
        return defaultMissed
                ? "TG_BOT_DEFAULT_ACCOUNT did not match. Choose the account used to parse and download:"
                : "Several accounts are signed in. Choose the account used to parse and download:";
    }

    public String accountButton(BotAccount account) {
        String name = account.name() == null || account.name().isBlank() ? Long.toString(account.id()) : account.name();
        return clip(name + " (" + account.id() + ")", 60);
    }

    public String parsing() {
        return chinese ? "正在解析…" : "Parsing…";
    }

    public String parseFailed(String reason) {
        String detail = reason == null || reason.isBlank()
                ? (chinese ? "未知错误" : "unknown error")
                : clip(reason, 300);
        return chinese ? "解析失败：" + esc(detail) : "Could not parse the link: " + esc(detail);
    }

    public String noMedia(int rawCount) {
        if (chinese) {
            return rawCount > 0
                    ? "这条消息里没有可下载的视频、图片或文件。"
                    : "没有找到可下载的媒体。请确认该账号已加入对应频道或群组。";
        }
        return rawCount > 0
                ? "That message has no downloadable video, photo, or file."
                : "No downloadable media. Make sure this account has joined the chat.";
    }

    public String mediaList(String link, List<BotMedia> media) {
        StringBuilder builder = new StringBuilder();
        if (chinese) {
            builder.append("<b>找到 ").append(media.size()).append(" 个文件</b>\n");
        } else {
            builder.append("<b>Found ").append(media.size()).append(media.size() == 1 ? " file" : " files").append("</b>\n");
        }
        builder.append(esc(link)).append("\n");
        int shown = Math.min(media.size(), 30);
        for (int i = 0; i < shown; i++) {
            BotMedia item = media.get(i);
            builder.append(i + 1).append(". ")
                    .append(item.typeLabel(chinese)).append(" ")
                    .append(esc(item.name()))
                    .append(" · ").append(ByteFormats.bytes(item.size()))
                    .append("\n");
        }
        if (media.size() > shown) {
            builder.append(chinese ? "… 其余文件可用「全部下载」\n" : "… the rest is included in Download all\n");
        }
        builder.append(chinese ? "\n请选择要下载的内容：" : "\nChoose what to download:");
        return builder.toString().strip();
    }

    public String downloadAll() {
        return chinese ? "全部下载" : "Download all";
    }

    public String onlyVideos() {
        return chinese ? "仅视频" : "Videos only";
    }

    public String onlyPhotos() {
        return chinese ? "仅图片" : "Photos only";
    }

    public String itemButton(int index, BotMedia media) {
        return clip(index + ". " + media.typeLabel(chinese) + " " + media.name(), 60);
    }

    public String nothingMatched() {
        return chinese ? "没有符合条件的文件" : "Nothing matches";
    }

    public String starting() {
        return chinese ? "开始下载…" : "Starting…";
    }

    public String expired() {
        return chinese ? "操作已过期，请重新发送链接。" : "That action expired. Send the link again.";
    }

    public String started(BotDownloadResult result) {
        if (chinese) {
            StringBuilder builder = new StringBuilder("已开始下载 ")
                    .append(result.started()).append(" 个");
            if (result.skipped() > 0) {
                builder.append("，跳过 ").append(result.skipped()).append(" 个（已在下载或已完成）");
            }
            if (result.failed() > 0) {
                builder.append("，失败 ").append(result.failed()).append(" 个");
            }
            builder.append("。");
            appendErrors(builder, result.errors());
            return builder.toString();
        }
        StringBuilder builder = new StringBuilder("Started ")
                .append(result.started()).append(result.started() == 1 ? " download" : " downloads");
        if (result.skipped() > 0) {
            builder.append(", skipped ").append(result.skipped()).append(" already active or completed");
        }
        if (result.failed() > 0) {
            builder.append(", failed ").append(result.failed());
        }
        builder.append(".");
        appendErrors(builder, result.errors());
        return builder.toString();
    }

    public String failed(String action, String reason) {
        String detail = reason == null || reason.isBlank() ? (chinese ? "未知错误" : "unknown error") : clip(reason, 300);
        return chinese ? esc(action) + "失败：" + esc(detail) : esc(action) + " failed: " + esc(detail);
    }

    public String suppressed(int count) {
        int n = Math.max(count, 1);
        if (chinese) {
            return "发送过快，已合并 " + n + " 条未逐条发出的消息。发送 /downloads 或 /uploads 可查看当前进度。";
        }
        return "Sending too fast; " + n + " messages were combined instead of sent one by one. Use /downloads or /uploads for the current progress.";
    }

    public String status(List<BotAccount> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return chinese ? "还没有 Telegram 账号。" : "No Telegram accounts yet.";
        }
        StringBuilder builder = new StringBuilder(chinese ? "<b>Telegram 账号</b>\n" : "<b>Telegram accounts</b>\n");
        for (BotAccount account : accounts) {
            String name = account.name() == null || account.name().isBlank()
                    ? (chinese ? "未命名" : "unnamed")
                    : account.name();
            builder.append("• ").append(esc(name));
            if (account.id() > 0) {
                builder.append(" (").append(account.id()).append(")");
            }
            builder.append(" — ").append(stateLabel(account.stateKey())).append("\n");
        }
        return builder.toString().strip();
    }

    public String downloads(DownloadReport report) {
        if (report.downloading().isEmpty() && report.queued() == 0 && report.paused() == 0 && report.failed() == 0) {
            return chinese ? "当前没有下载任务。" : "No downloads right now.";
        }
        StringBuilder builder = new StringBuilder(chinese ? "<b>下载状态</b>\n" : "<b>Downloads</b>\n");
        String account = null;
        int shown = 0;
        for (DownloadFileView file : report.downloading()) {
            if (shown >= 20) {
                break;
            }
            if (!Objects.equals(account, file.accountName())) {
                account = file.accountName();
                builder.append("\n").append(esc(account == null ? "" : account));
                if (file.speedBytesPerSec() > 0) {
                    builder.append(chinese ? " · 速度 " : " · speed ").append(ByteFormats.speed(file.speedBytesPerSec()));
                }
                builder.append("\n");
            }
            builder.append("• ").append(esc(file.name())).append("  ");
            if (file.progressKnown()) {
                builder.append(file.percent()).append("%  ")
                        .append(ByteFormats.bytes(file.downloaded()))
                        .append(" / ")
                        .append(ByteFormats.bytes(file.size()));
            } else {
                builder.append(chinese ? "进度未知  " : "progress unknown  ")
                        .append(ByteFormats.bytes(file.size()));
            }
            builder.append("\n");
            shown++;
        }
        builder.append("\n");
        if (chinese) {
            builder.append("正在下载 ").append(report.downloadingCount())
                    .append(" · 队列 ").append(report.queued())
                    .append(" · 已暂停 ").append(report.paused())
                    .append(" · 失败 ").append(report.failed());
        } else {
            builder.append("downloading ").append(report.downloadingCount())
                    .append(" · queued ").append(report.queued())
                    .append(" · paused ").append(report.paused())
                    .append(" · failed ").append(report.failed());
        }
        if (report.truncated() || report.downloadingCount() > shown) {
            builder.append(chinese ? "\n列表可能不完整。" : "\nThe list may be incomplete.");
        }
        return builder.toString().strip();
    }

    public String uploads(UploadReport report) {
        if (!report.rcloneEnabled()
                && report.uploading().isEmpty()
                && report.pending() == 0
                && report.failed() == 0
                && report.recent().isEmpty()) {
            return chinese
                    ? "当前没有上传任务。rclone 上传默认关闭，可在设置里开启。"
                    : "No uploads. Rclone upload is off by default; enable it in Settings.";
        }
        StringBuilder builder = new StringBuilder(chinese ? "<b>上传状态</b>\n" : "<b>Uploads</b>\n");
        if (report.uploading().isEmpty()) {
            builder.append(chinese ? "当前没有正在上传的文件。\n" : "Nothing is uploading.\n");
        } else {
            builder.append(chinese ? "正在上传\n" : "Uploading\n");
            for (UploadFileView file : report.uploading()) {
                builder.append("• ").append(esc(file.name())).append("  ").append(ByteFormats.bytes(file.size()));
                if (file.progressKnown()) {
                    builder.append("  ").append(file.percent()).append("%");
                } else {
                    builder.append(chinese ? "（实时进度不可用）" : " (live progress unavailable)");
                }
                builder.append("\n");
            }
        }
        if (chinese) {
            builder.append("等待 ").append(report.pending()).append(" · 失败 ").append(report.failed());
        } else {
            builder.append("pending ").append(report.pending()).append(" · failed ").append(report.failed());
        }
        if (!report.recent().isEmpty()) {
            builder.append(chinese ? "\n\n最近完成\n" : "\n\nRecent completions\n");
            for (UploadFileView file : report.recent()) {
                builder.append("• ").append(esc(file.name())).append("  ").append(ByteFormats.bytes(file.size())).append("\n");
            }
        }
        if (!report.rcloneEnabled()) {
            builder.append(chinese ? "\nrclone 上传当前是关闭的。" : "\nRclone upload is currently disabled.");
        }
        return builder.toString().strip();
    }

    public String notice(BotNotifications.Kind kind, String name, long size, String error) {
        String label = name == null || name.isBlank() ? (chinese ? "未命名文件" : "unnamed file") : name;
        String sizeText = size > 0 ? "（" + ByteFormats.bytes(size) + "）" : "";
        String sizeTextEn = size > 0 ? " (" + ByteFormats.bytes(size) + ")" : "";
        String text = switch (kind) {
            case DOWNLOAD_OK -> chinese ? "下载完成：" + esc(label) + sizeText : "Download finished: " + esc(label) + sizeTextEn;
            case DOWNLOAD_FAIL -> chinese ? "下载失败：" + esc(label) : "Download failed: " + esc(label);
            case UPLOAD_OK -> chinese ? "上传完成：" + esc(label) + sizeText : "Upload finished: " + esc(label) + sizeTextEn;
            case UPLOAD_FAIL -> chinese ? "上传失败：" + esc(label) : "Upload failed: " + esc(label);
        };
        if ((kind == BotNotifications.Kind.DOWNLOAD_FAIL || kind == BotNotifications.Kind.UPLOAD_FAIL)
                && error != null && !error.isBlank()) {
            text += chinese ? "\n原因：" + esc(clip(error, 300)) : "\nReason: " + esc(clip(error, 300));
        }
        return text;
    }

    public String stateLabel(String stateKey) {
        String key = stateKey == null ? "unknown" : stateKey;
        if (chinese) {
            return switch (key) {
                case "ready" -> "已授权";
                case "wait_phone" -> "等待手机号";
                case "wait_code" -> "等待验证码";
                case "wait_password" -> "等待两步验证密码";
                case "wait_email" -> "等待邮箱";
                case "wait_email_code" -> "等待邮箱验证码";
                case "wait_registration" -> "等待注册";
                case "wait_other_device" -> "等待其他设备确认";
                case "closed" -> "已关闭";
                case "closing" -> "正在关闭";
                case "logging_out" -> "正在退出";
                case "initializing" -> "正在初始化";
                case "unknown" -> "未知";
                default -> "未授权";
            };
        }
        return switch (key) {
            case "ready" -> "authorized";
            case "wait_phone" -> "waiting for phone number";
            case "wait_code" -> "waiting for code";
            case "wait_password" -> "waiting for password";
            case "wait_email" -> "waiting for email";
            case "wait_email_code" -> "waiting for email code";
            case "wait_registration" -> "waiting for registration";
            case "wait_other_device" -> "waiting for another device";
            case "closed" -> "closed";
            case "closing" -> "closing";
            case "logging_out" -> "logging out";
            case "initializing" -> "initializing";
            case "unknown" -> "unknown";
            default -> "unauthorized";
        };
    }

    public static String esc(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static void appendErrors(StringBuilder builder, List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return;
        }
        builder.append("\n");
        for (String error : errors) {
            builder.append(esc(clip(error, 180))).append("\n");
        }
    }
}
