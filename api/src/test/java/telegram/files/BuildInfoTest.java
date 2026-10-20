package telegram.files;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BuildInfoTest {

    @Test
    void identityPointsAtTheFork() {
        assertEquals("ghcr.io/wddclass/telegram-files", BuildInfo.IMAGE);
        assertEquals("wddclass/telegram-files", BuildInfo.REPOSITORY);
        assertEquals("custom-0.3.1", BuildInfo.BRANCH);
        assertFalse(BuildInfo.VERSION.isBlank());
    }

    @Test
    void shortShaTrimsAndIgnoresUnknown() {
        assertEquals("abc1234", BuildInfo.shortSha("abc1234deadbeef"));
        assertEquals("abc", BuildInfo.shortSha("abc"));
        assertEquals("", BuildInfo.shortSha("unknown"));
        assertEquals("", BuildInfo.shortSha(null));
        assertEquals("", BuildInfo.shortSha("  "));
    }
}
