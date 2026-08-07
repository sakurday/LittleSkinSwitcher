package com.littleskin.switcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.littleskin.switcher.config.ModConfig;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * LittleSkin（Blessing Skin / authlib-injector 兼容）Yggdrasil 认证客户端。
 *
 * 默认服务地址为 https://littleskin.cn/api/yggdrasil，
 * 可通过配置文件 littleskin-switcher.json 中的 "authServer" 字段覆盖。
 */
public class LittleSkinAuth {
    /** LittleSkin 的默认 Yggdrasil 服务根地址。 */
    public static final String DEFAULT_AUTH_SERVER = "https://littleskin.cn/api/yggdrasil";

    private static String baseUrl() {
        String configured = ModConfig.get().authServer;
        return (configured == null || configured.isBlank()) ? DEFAULT_AUTH_SERVER : configured.trim();
    }

    private static String authenticateUrl() {
        return baseUrl() + "/authserver/authenticate";
    }

    private static String refreshUrl() {
        return baseUrl() + "/authserver/refresh";
    }

    private static String joinUrl() {
        return baseUrl() + "/sessionserver/session/minecraft/join";
    }

    private static String profileUrl(String profileId) {
        return baseUrl() + "/sessionserver/session/minecraft/profile/" + profileId;
    }

    /** authenticate / refresh 的结果。 */
    public static class AuthResult {
        public String accessToken;
        public String clientToken;
        /** 无连字符的 profile UUID。 */
        public String profileId;
        public String profileName;
    }

    /**
     * 使用账号密码向 LittleSkin 发起认证，返回 accessToken 与角色信息。
     */
    public static AuthResult authenticate(String email, String password) throws Exception {
        JsonObject body = new JsonObject();
        JsonObject agent = new JsonObject();
        agent.addProperty("name", "Minecraft");
        agent.addProperty("version", 1);
        body.add("agent", agent);
        body.addProperty("username", email);
        body.addProperty("password", password);
        body.addProperty("requestUser", false);
        return parseAuthResponse(request("POST", authenticateUrl(), body.toString()));
    }

    /**
     * 刷新 accessToken（保持会话有效，无需重新输入密码）。
     */
    public static AuthResult refresh(String accessToken, String clientToken) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("accessToken", accessToken);
        body.addProperty("clientToken", clientToken);
        return parseAuthResponse(request("POST", refreshUrl(), body.toString()));
    }

    private static AuthResult parseAuthResponse(JsonObject resp) {
        AuthResult r = new AuthResult();
        r.accessToken = resp.get("accessToken").getAsString();
        if (resp.has("clientToken") && !resp.get("clientToken").isJsonNull()) {
            r.clientToken = resp.get("clientToken").getAsString();
        }
        if (resp.has("selectedProfile") && !resp.get("selectedProfile").isJsonNull()) {
            JsonObject profile = resp.getAsJsonObject("selectedProfile");
            if (profile.has("id") && !profile.get("id").isJsonNull()) {
                r.profileId = profile.get("id").getAsString();
            }
            if (profile.has("name") && !profile.get("name").isJsonNull()) {
                r.profileName = profile.get("name").getAsString();
            }
        }
        return r;
    }

    /**
     * 向 LittleSkin 的 sessionserver 发送 join 请求。
     * 这是服务器校验玩家登录的关键一步，等效于 authlib-injector 客户端的行为。
     */
    public static void joinServer(String accessToken, UUID profileId, String serverId) throws Exception {
        if (profileId == null) {
            throw new IOException("LittleSkin joinServer: profileId 为空");
        }
        JsonObject body = new JsonObject();
        body.addProperty("accessToken", accessToken);
        body.addProperty("selectedProfile", profileId.toString().replace("-", ""));
        body.addProperty("serverId", serverId);
        request("POST", joinUrl(), body.toString());
    }

    /**
     * 获取指定角色的皮肤/披风纹理。仅当 UUID 匹配时返回，否则返回 null。
     * （此方法为可选增强，用于确保本机在 LittleSkin 服务器上显示 LittleSkin 皮肤。）
     */
    public static Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> fetchTextures(String profileId) {
        try {
            JsonObject resp = request("GET", profileUrl(profileId.replace("-", "")), null);
            JsonArray props = resp.has("properties") && !resp.get("properties").isJsonNull()
                    ? resp.getAsJsonArray("properties") : null;
            if (props == null) {
                return null;
            }
            for (JsonElement e : props) {
                JsonObject p = e.getAsJsonObject();
                if (!"textures".equals(p.get("name").getAsString())) {
                    continue;
                }
                String value = p.get("value").getAsString();
                String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(decoded)
                        .getAsJsonObject()
                        .getAsJsonObject("textures");
                Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> result = new HashMap<>();
                if (textures.has("SKIN")) {
                    JsonObject skin = textures.getAsJsonObject("SKIN");
                    result.put(MinecraftProfileTexture.Type.SKIN,
                            new MinecraftProfileTexture(skin.get("url").getAsString(), metadataOf(skin)));
                }
                if (textures.has("CAPE")) {
                    JsonObject cape = textures.getAsJsonObject("CAPE");
                    result.put(MinecraftProfileTexture.Type.CAPE,
                            new MinecraftProfileTexture(cape.get("url").getAsString(), metadataOf(cape)));
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
        } catch (Exception ignored) {
            // 拉取失败就回退到默认逻辑
        }
        return null;
    }

    private static Map<String, String> metadataOf(JsonObject o) {
        Map<String, String> m = new HashMap<>();
        if (o.has("metadata") && !o.get("metadata").isJsonNull()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("metadata").entrySet()) {
                m.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return m;
    }

    private static JsonObject request(String method, String url, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "LittleSkinSwitcher/0.1.0");
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
            String responseBody = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + ": " + responseBody);
            }
            if (responseBody == null || responseBody.isBlank()) {
                return new JsonObject();
            }
            return JsonParser.parseString(responseBody).getAsJsonObject();
        } finally {
            conn.disconnect();
        }
    }

    private LittleSkinAuth() {
    }
}
