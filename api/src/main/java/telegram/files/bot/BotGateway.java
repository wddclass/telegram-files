package telegram.files.bot;

import io.vertx.core.json.JsonObject;

public interface BotGateway {

    void send(long chatId, String text, JsonObject replyMarkup);

    void edit(long chatId, int messageId, String text, JsonObject replyMarkup);

    /**
     * @param text visible toast, or null to dismiss the button spinner without text
     */
    void answer(String callbackId, String text);
}
