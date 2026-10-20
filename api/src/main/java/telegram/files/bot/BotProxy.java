package telegram.files.bot;

import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * HTTP or SOCKS5 proxy for the Bot API client. MTProto proxies cannot carry Bot API HTTPS calls.
 */
public record BotProxy(String type, String host, int port, String username, String password) {

    public static BotProxy parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (!value.contains("://")) {
            value = "http://" + value;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("TG_BOT_PROXY is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String proxyType = switch (scheme) {
            case "http", "https" -> "http";
            case "socks", "socks5" -> "socks5";
            default -> throw new IllegalArgumentException("TG_BOT_PROXY scheme must be http, https, or socks5");
        };
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getPort() < 0) {
            throw new IllegalArgumentException("TG_BOT_PROXY needs a host and port");
        }
        String user = null;
        String pass = null;
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int colon = userInfo.indexOf(':');
            if (colon < 0) {
                user = decode(userInfo);
            } else {
                user = decode(userInfo.substring(0, colon));
                pass = decode(userInfo.substring(colon + 1));
            }
        }
        return new BotProxy(proxyType, uri.getHost(), uri.getPort(), user, pass);
    }

    public ProxyOptions toOptions() {
        ProxyOptions options = new ProxyOptions()
                .setType("socks5".equals(type) ? ProxyType.SOCKS5 : ProxyType.HTTP)
                .setHost(host)
                .setPort(port);
        if (username != null && !username.isEmpty()) {
            options.setUsername(username);
            options.setPassword(password == null ? "" : password);
        }
        return options;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
