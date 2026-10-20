package telegram.files.bot;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record BotMedia(int fileId, long chatId, long messageId, String type, String name, long size) {

    public static List<BotMedia> fromFiles(JsonArray files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<BotMedia> media = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            JsonObject file = files.getJsonObject(i);
            if (file == null) {
                continue;
            }
            String type = file.getString("type");
            if (type == null || "thumbnail".equalsIgnoreCase(type)) {
                continue;
            }
            long fileId = number(file, "id");
            long chatId = number(file, "chatId");
            long messageId = number(file, "messageId");
            if (fileId <= 0 || fileId > Integer.MAX_VALUE || chatId == 0 || messageId <= 0) {
                continue;
            }
            String name = file.getString("fileName");
            if (name == null || name.isBlank()) {
                name = type;
            }
            media.add(new BotMedia((int) fileId, chatId, messageId, type, name, Math.max(0, number(file, "size"))));
        }
        return List.copyOf(media);
    }

    public static List<BotMedia> select(List<BotMedia> media, String mode) {
        if (media == null || media.isEmpty() || mode == null) {
            return List.of();
        }
        return switch (mode) {
            case "all" -> List.copyOf(media);
            case "video" -> media.stream().filter(item -> "video".equalsIgnoreCase(item.type())).toList();
            case "photo" -> media.stream().filter(item -> "photo".equalsIgnoreCase(item.type())).toList();
            default -> {
                int index;
                try {
                    index = Integer.parseInt(mode);
                } catch (NumberFormatException e) {
                    yield List.of();
                }
                yield index >= 0 && index < media.size() ? List.of(media.get(index)) : List.of();
            }
        };
    }

    public String typeLabel(boolean chinese) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (chinese) {
            return switch (normalized) {
                case "video" -> "视频";
                case "photo" -> "图片";
                case "audio" -> "音频";
                case "file" -> "文件";
                default -> normalized.isEmpty() ? "文件" : type;
            };
        }
        return switch (normalized) {
            case "video" -> "video";
            case "photo" -> "photo";
            case "audio" -> "audio";
            case "file" -> "file";
            default -> normalized.isEmpty() ? "file" : type;
        };
    }

    private static long number(JsonObject json, String key) {
        Object value = json.getValue(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
