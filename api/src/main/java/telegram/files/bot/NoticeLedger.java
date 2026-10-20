package telegram.files.bot;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers file-status notifications that were already considered, including ones that were not sent.
 * The same file and outcome is not announced again after a restart.
 */
public final class NoticeLedger {

    private static final Log log = LogFactory.get();

    private final Path file;

    private final Set<String> keys = ConcurrentHashMap.newKeySet();

    public NoticeLedger(Path file) {
        this.file = file;
        load();
    }

    public boolean first(String key) {
        if (key == null || key.isBlank() || !keys.add(key)) {
            return false;
        }
        append(key);
        return true;
    }

    private void load() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                String key = line.trim();
                if (!key.isEmpty()) {
                    keys.add(key);
                }
            }
        } catch (IOException e) {
            log.warn("Could not read bot notice ledger: %s".formatted(e.getMessage()));
        }
    }

    private void append(String key) {
        if (file == null) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = key.replace('\r', ' ').replace('\n', ' ') + "\n";
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Could not store bot notice ledger: %s".formatted(e.getMessage()));
        }
    }
}
