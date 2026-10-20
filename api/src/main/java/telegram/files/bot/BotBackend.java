package telegram.files.bot;

import io.vertx.core.Future;
import telegram.files.bot.BotModels.BotAccount;
import telegram.files.bot.BotModels.BotDownloadResult;
import telegram.files.bot.BotModels.DownloadReport;
import telegram.files.bot.BotModels.UploadReport;

import java.util.List;

public interface BotBackend {

    List<BotAccount> accounts();

    Future<List<BotAccount>> accountSnapshots();

    Future<List<BotMedia>> parseLink(long accountId, String link);

    Future<BotDownloadResult> startDownloads(long accountId, List<BotMedia> files);

    Future<DownloadReport> downloadReport();

    Future<UploadReport> uploadReport();
}
