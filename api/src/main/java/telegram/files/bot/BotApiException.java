package telegram.files.bot;

/**
 * A Bot API error that must not take down the process.
 */
public final class BotApiException extends RuntimeException {

    private final int errorCode;

    private final int retryAfterSeconds;

    public BotApiException(int errorCode, int retryAfterSeconds, String message) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int errorCode() {
        return errorCode;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
