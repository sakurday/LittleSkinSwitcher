package com.littleskin.switcher.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.littleskin.switcher.LittleSkinSwitcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Yggdrasil 的 HTTP 细节：超时、头部、以及把站点返回的错误体翻译成可读消息。 */
final class YggdrasilHttp {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    /** 站点运营者可以从 User-Agent 看出请求来自哪个版本，所以跟着 mod_version 走。 */
    private static volatile String userAgent;

    private static String userAgent() {
        String cached = userAgent;
        if (cached == null) {
            cached = "LittleSkinSwitcher/" + LittleSkinSwitcher.version();
            userAgent = cached;
        }
        return cached;
    }

    static JsonObject post(String url, JsonObject body) throws AuthException {
        return request("POST", url, body.toString());
    }

    static JsonObject get(String url) throws AuthException {
        return request("GET", url, null);
    }

    private static JsonObject request(String method, String url, String json) throws AuthException {
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        } catch (IOException | IllegalArgumentException e) {
            throw AuthException.local("littleskin-switcher.error.badAddress", url);
        }
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", userAgent());
            conn.setRequestProperty("Accept", "application/json");
            if (json != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String text = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                throw errorFromResponse(code, text);
            }
            return text.isBlank() ? new JsonObject() : JsonParser.parseString(text).getAsJsonObject();
        } catch (IOException e) {
            throw AuthException.local("littleskin-switcher.error.network", String.valueOf(e.getMessage()));
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Yggdrasil 的错误响应是 {error, errorMessage, cause}。
     * errorMessage 是站点自己写给玩家看的（LittleSkin 为中文），优先展示它。
     */
    private static AuthException errorFromResponse(int code, String body) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            String message = opt(o, "errorMessage");
            if (!message.isEmpty()) {
                return AuthException.remote(message);
            }
            String error = opt(o, "error");
            if (!error.isEmpty()) {
                return AuthException.remote(error + " (HTTP " + code + ")");
            }
        } catch (Exception ignored) {
            // 不是 JSON（网关错误页之类），退回到状态码
        }
        LittleSkinSwitcher.LOGGER.debug("[LittleSkinSwitcher] HTTP {}: {}", code, body);
        return AuthException.local("littleskin-switcher.error.http", code);
    }

    private static String opt(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) {
            return "";
        }
        return o.get(key).getAsString();
    }

    private YggdrasilHttp() {
    }
}
