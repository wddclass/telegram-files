package telegram.files;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class RcloneRunner {

    public record Result(boolean success, String output) {
    }

    private RcloneRunner() {
    }

    public static Result run(List<String> command, Duration timeout) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            StringBuilder output = new StringBuilder();
            Process running = process;
            Thread reader = Thread.startVirtualThread(() -> read(running.getInputStream(), output));
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(1000);
                return new Result(false, trim("rclone timed out\n" + output));
            }
            reader.join(2000);
            if (process.exitValue() != 0) {
                return new Result(false, trim("rclone exited with " + process.exitValue() + "\n" + output));
            }
            return new Result(true, trim(output.toString()));
        } catch (IOException e) {
            return new Result(false, "Failed to start rclone. Install rclone and make sure it is on PATH. " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new Result(false, "rclone was interrupted");
        }
    }

    private static void read(InputStream input, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < 4000) {
                    output.append(line).append('\n');
                }
            }
        } catch (IOException ignored) {
            // The process is reported via its exit code.
        }
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }
}
