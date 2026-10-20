package telegram.files.bot;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TelegramLinksTest {

    @Test
    void extractsPublicPrivateAndAlbumLinks() {
        assertEquals("https://t.me/some_user/321",
                TelegramLinks.extract("请看 https://t.me/some_user/321").orElseThrow());
        assertEquals("https://t.me/c/1401234567/99",
                TelegramLinks.extract("https://t.me/c/1401234567/99").orElseThrow());
        assertEquals("https://t.me/c/1401234567/99?single",
                TelegramLinks.extract("相册 (https://t.me/c/1401234567/99?single)").orElseThrow());
        assertEquals("https://t.me/durov/100/200",
                TelegramLinks.extract("http://telegram.me/durov/100/200,").orElseThrow());
        assertEquals("https://t.me/news/8",
                TelegramLinks.extract("t.me/news/8").orElseThrow());
    }

    @Test
    void ignoresNonMessageLinks() {
        assertTrue(TelegramLinks.extract("https://example.com/t.me/user/1").isEmpty());
        assertTrue(TelegramLinks.extract("https://t.me/username").isEmpty());
        assertTrue(TelegramLinks.extract("https://t.me/+invitehash").isEmpty());
        assertTrue(TelegramLinks.extract("你好").isEmpty());
    }

    @Test
    void readsTheFirstLinkFromMessageEntities() {
        JsonObject message = new JsonObject()
                .put("text", "看这个")
                .put("entities", new JsonArray().add(new JsonObject()
                        .put("type", "text_link")
                        .put("url", "https://t.me/c/5/6?single")));
        assertEquals("https://t.me/c/5/6?single", TelegramLinks.fromMessage(message).orElseThrow());
    }
}
