package telegram.files.bot;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BotAllowlistTest {

    @Test
    void emptyAllowlistDeniesEveryone() {
        BotAllowlist allowlist = BotAllowlist.parse(null);
        assertTrue(allowlist.isEmpty());
        assertFalse(allowlist.allows(1, "steven"));
        assertFalse(BotAllowlist.parse("  ,  ").allows(1, "steven"));
    }

    @Test
    void numericIdsAndUsernames() {
        BotAllowlist allowlist = BotAllowlist.parse(" 42, @Steven, steven2, 100 ");
        assertTrue(allowlist.allows(42, null));
        assertTrue(allowlist.allows(7, "Steven"));
        assertTrue(allowlist.allows(7, "@steven2"));
        assertFalse(allowlist.allows(7, "someone"));
        assertFalse(allowlist.allows(43, null));
        assertEquals(2, allowlist.idCount());
        assertEquals(2, allowlist.usernameCount());
    }

    @Test
    void shortOrNegativeTokensAreIgnored() {
        BotAllowlist allowlist = BotAllowlist.parse("abc, -5, 0, @ab");
        assertTrue(allowlist.isEmpty());
    }
}
