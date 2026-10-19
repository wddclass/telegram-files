package telegram.files.repository;

import cn.hutool.core.util.StrUtil;
import io.vertx.core.json.Json;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Optional rclone upload. Stored as one JSON settings value, same as the other global settings.
 * Disabled by default so existing downloads are unchanged until the user turns it on.
 */
public class SettingRclone {
    public boolean enabled;
    public String remote;
    public String configPath;
    public String extraFlags;
    public String operation;
    public String mode;
    public String schedule;
    public String fileTypes;

    public static SettingRclone defaults() {
        SettingRclone settings = new SettingRclone();
        settings.enabled = false;
        settings.remote = "";
        settings.configPath = "";
        settings.extraFlags = "";
        settings.operation = "copy";
        settings.mode = "immediate";
        settings.schedule = "3600";
        settings.fileTypes = "video";
        return settings;
    }

    public static String defaultJson() {
        return Json.encode(defaults());
    }

    public static SettingRclone fromJson(String json) {
        if (StrUtil.isBlank(json)) {
            return defaults();
        }
        SettingRclone parsed = new io.vertx.core.json.JsonObject(json).mapTo(SettingRclone.class);
        SettingRclone defaults = defaults();
        if (StrUtil.isBlank(parsed.operation)) {
            parsed.operation = defaults.operation;
        }
        if (StrUtil.isBlank(parsed.mode)) {
            parsed.mode = defaults.mode;
        }
        if (StrUtil.isBlank(parsed.schedule)) {
            parsed.schedule = defaults.schedule;
        }
        if (parsed.fileTypes == null) {
            parsed.fileTypes = defaults.fileTypes;
        }
        if (parsed.remote == null) {
            parsed.remote = "";
        }
        if (parsed.configPath == null) {
            parsed.configPath = "";
        }
        if (parsed.extraFlags == null) {
            parsed.extraFlags = "";
        }
        return parsed;
    }

    public boolean move() {
        return "move".equalsIgnoreCase(operation);
    }

    public boolean scheduled() {
        return "scheduled".equalsIgnoreCase(mode);
    }

    public String resolvedConfigPath(String appRoot) {
        if (StrUtil.isNotBlank(configPath)) {
            return configPath.trim();
        }
        String root = StrUtil.blankToDefault(appRoot, "/app/data");
        return root + File.separator + "rclone" + File.separator + "rclone.conf";
    }

    public Set<String> acceptedTypes() {
        String raw = StrUtil.blankToDefault(fileTypes, "video");
        return Arrays.stream(raw.split(","))
                .map(type -> type.trim().toLowerCase(Locale.ROOT))
                .filter(type -> !type.isEmpty())
                .collect(Collectors.toSet());
    }

    public boolean accepts(String type) {
        if (type == null || "thumbnail".equals(type)) {
            return false;
        }
        return acceptedTypes().contains(type.toLowerCase(Locale.ROOT));
    }
}
