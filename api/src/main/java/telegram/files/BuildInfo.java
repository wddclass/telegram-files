package telegram.files;

import java.io.InputStream;
import java.util.jar.Manifest;

/**
 * Identity of the running build. Docker passes these through build-args that
 * become environment variables and manifest entries. Local runs fall back to
 * the project version.
 */
public final class BuildInfo {

    public static final String IMAGE = "ghcr.io/wddclass/telegram-files";

    public static final String REPOSITORY = "wddclass/telegram-files";

    public static final String BRANCH = "custom-0.3.1";

    public static final String VERSION = firstNonBlank(
            System.getenv("APP_VERSION"),
            manifest("Implementation-Version"),
            "0.3.1"
    );

    public static final String GIT_COMMIT = normalizeUnknown(firstNonBlank(
            System.getenv("APP_GIT_COMMIT"),
            manifest("Git-Commit"),
            ""
    ));

    public static final String GIT_COMMIT_SHORT = normalizeUnknown(firstNonBlank(
            System.getenv("APP_GIT_COMMIT_SHORT"),
            manifest("Git-Commit-Short"),
            shortSha(GIT_COMMIT)
    ));

    public static final String GIT_REF = normalizeUnknown(firstNonBlank(
            System.getenv("APP_GIT_REF"),
            manifest("Git-Ref"),
            ""
    ));

    private BuildInfo() {
    }

    static String shortSha(String sha) {
        if (sha == null) {
            return "";
        }
        String trimmed = sha.trim();
        if (trimmed.isEmpty() || "unknown".equalsIgnoreCase(trimmed)) {
            return "";
        }
        return trimmed.length() <= 7 ? trimmed : trimmed.substring(0, 7);
    }

    private static String normalizeUnknown(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return "unknown".equalsIgnoreCase(trimmed) ? "" : trimmed;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String manifest(String key) {
        try (InputStream in = BuildInfo.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (in == null) {
                return "";
            }
            String value = new Manifest(in).getMainAttributes().getValue(key);
            return value == null ? "" : value;
        } catch (Exception ignored) {
            return "";
        }
    }
}
