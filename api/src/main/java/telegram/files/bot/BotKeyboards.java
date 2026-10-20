package telegram.files.bot;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import telegram.files.bot.BotModels.BotAccount;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class BotKeyboards {

    private BotKeyboards() {
    }

    public static JsonObject accounts(String token, List<BotAccount> accounts, BotTexts texts) {
        JsonArray rows = new JsonArray();
        for (BotAccount account : accounts) {
            String data = "a:" + token + ":" + account.id();
            if (data.getBytes(StandardCharsets.UTF_8).length > 64) {
                continue;
            }
            rows.add(row(button(texts.accountButton(account), data)));
        }
        return markup(rows);
    }

    public static JsonObject downloads(String token, List<BotMedia> media, BotTexts texts) {
        JsonArray rows = new JsonArray();
        rows.add(row(button(texts.downloadAll(), "d:" + token + ":all")));
        rows.add(row(
                button(texts.onlyVideos(), "d:" + token + ":video"),
                button(texts.onlyPhotos(), "d:" + token + ":photo")
        ));
        int limit = Math.min(media.size(), 12);
        for (int i = 0; i < limit; i++) {
            String data = "d:" + token + ":" + i;
            if (data.getBytes(StandardCharsets.UTF_8).length > 64) {
                continue;
            }
            rows.add(row(button(texts.itemButton(i + 1, media.get(i)), data)));
        }
        return markup(rows);
    }

    public static JsonObject removed() {
        return markup(new JsonArray());
    }

    private static JsonObject markup(JsonArray rows) {
        return new JsonObject().put("inline_keyboard", rows);
    }

    private static JsonArray row(JsonObject... buttons) {
        JsonArray row = new JsonArray();
        for (JsonObject button : buttons) {
            row.add(button);
        }
        return row;
    }

    private static JsonObject button(String text, String data) {
        return new JsonObject()
                .put("text", text == null || text.isBlank() ? " " : text)
                .put("callback_data", data);
    }
}
