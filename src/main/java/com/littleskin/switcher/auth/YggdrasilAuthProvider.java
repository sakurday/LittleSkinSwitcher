package com.littleskin.switcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.littleskin.switcher.config.Account;
import com.littleskin.switcher.util.Uuids;
import com.mojang.authlib.properties.Property;

import java.net.URI;
import java.util.UUID;

/**
 * 任意 Yggdrasil 服务（authlib-injector 规范）。
 *
 * LittleSkin 不是特例：它只是 {@link #LITTLESKIN} 这个预设地址下的一个实例，
 * 玩家填写的任何 Blessing Skin 自建站都走同一套代码。
 */
public class YggdrasilAuthProvider implements AuthProvider {
    /** LittleSkin 的 Yggdrasil 服务根地址，仅用于「填充默认值」与首次配置。 */
    public static final String LITTLESKIN = "https://littleskin.cn/api/yggdrasil";

    private static final String AUTHENTICATE_PATH = "/authserver/authenticate";
    private static final String REFRESH_PATH = "/authserver/refresh";
    private static final String JOIN_PATH = "/sessionserver/session/minecraft/join";
    private static final String PROFILE_PATH = "/sessionserver/session/minecraft/profile";

    private final String rawBaseUrl;

    /**
     * @param rawBaseUrl 用户填写的地址，可以是 "example.com"、"https://example.com"
     *                   或完整的 "https://example.com/api/yggdrasil"，使用前统一规范化。
     */
    public YggdrasilAuthProvider(String rawBaseUrl) {
        this.rawBaseUrl = rawBaseUrl == null ? "" : rawBaseUrl;
    }

    @Override
    public String typeId() {
        return Account.TYPE_YGGDRASIL;
    }

    /**
     * 把用户输入的地址补全成 Yggdrasil 根地址。
     * 接受省略协议、带或不带 /api/yggdrasil 后缀的写法。
     */
    public static String normalizeBaseUrl(String raw) throws AuthException {
        if (raw == null || raw.isBlank()) {
            throw AuthException.local("littleskin-switcher.error.emptyAddress");
        }
        String s = raw.trim();
        if (!s.matches("(?i)^https?://.*")) {
            s = "https://" + s;
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(s);
        } catch (IllegalArgumentException e) {
            throw AuthException.local("littleskin-switcher.error.badAddress", raw);
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw AuthException.local("littleskin-switcher.error.badAddress", raw);
        }
        if (!s.toLowerCase().endsWith("/api/yggdrasil")) {
            s = s + "/api/yggdrasil";
        }
        return s;
    }

    private String base() throws AuthException {
        return normalizeBaseUrl(rawBaseUrl);
    }

    @Override
    public AuthResult login(Account account, String password) throws AuthException {
        if (account.username == null || account.username.isBlank()) {
            throw AuthException.local("littleskin-switcher.error.emptyUsername");
        }
        if (password == null || password.isEmpty()) {
            throw AuthException.local("littleskin-switcher.error.emptyPassword");
        }
        JsonObject agent = new JsonObject();
        agent.addProperty("name", "Minecraft");
        agent.addProperty("version", 1);

        JsonObject body = new JsonObject();
        body.add("agent", agent);
        body.addProperty("username", account.username);
        body.addProperty("password", password);
        body.addProperty("clientToken", account.clientToken);
        body.addProperty("requestUser", false);

        return parseAuthResponse(YggdrasilHttp.post(base() + AUTHENTICATE_PATH, body));
    }

    @Override
    public AuthResult refresh(Account account) throws AuthException {
        if (account.accessToken == null || account.accessToken.isEmpty()) {
            throw AuthException.local("littleskin-switcher.error.needPassword");
        }
        JsonObject body = new JsonObject();
        body.addProperty("accessToken", account.accessToken);
        body.addProperty("clientToken", account.clientToken);
        body.addProperty("requestUser", false);
        return parseAuthResponse(YggdrasilHttp.post(base() + REFRESH_PATH, body));
    }

    @Override
    public void joinServer(Account account, UUID profileId, String accessToken, String serverId) throws AuthException {
        if (profileId == null) {
            throw AuthException.local("littleskin-switcher.error.invalidProfileUuid");
        }
        if (accessToken == null || accessToken.isEmpty()) {
            throw AuthException.local("littleskin-switcher.error.needPassword");
        }
        JsonObject body = new JsonObject();
        body.addProperty("accessToken", accessToken);
        body.addProperty("selectedProfile", profileId.toString().replace("-", ""));
        body.addProperty("serverId", serverId);
        YggdrasilHttp.post(base() + JOIN_PATH, body);
    }

    /**
     * 取某个角色在站点上的皮肤纹理属性（authlib-injector 规范的 textures 属性）。
     *
     * 只为界面上的头像服务：本模组不接管游戏内的皮肤加载。
     * 拿到属性后交给原版 {@code SkinManager} 处理，下载与缓存都由它负责。
     * 站点没有为这个角色设置皮肤时返回 null。
     */
    public Property fetchTexturesProperty(UUID profileId) throws AuthException {
        JsonObject resp = YggdrasilHttp.get(
                base() + PROFILE_PATH + "/" + profileId.toString().replace("-", ""));
        if (!resp.has("properties") || !resp.get("properties").isJsonArray()) {
            return null;
        }
        for (JsonElement e : resp.getAsJsonArray("properties")) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject property = e.getAsJsonObject();
            if (!"textures".equals(opt(property, "name"))) {
                continue;
            }
            String value = opt(property, "value");
            if (value.isEmpty()) {
                continue;
            }
            String signature = opt(property, "signature");
            return signature.isEmpty()
                    ? new Property("textures", value)
                    : new Property("textures", value, signature);
        }
        return null;
    }

    /** 读取 API 根地址的元数据，用于校验地址与获取站点名。 */
    public YggdrasilMetadata fetchMetadata() throws AuthException {
        JsonObject root = YggdrasilHttp.get(base());
        YggdrasilMetadata meta = new YggdrasilMetadata();
        if (root.has("meta") && root.get("meta").isJsonObject()) {
            JsonObject m = root.getAsJsonObject("meta");
            meta.serverName = opt(m, "serverName");
            if (!m.has("serverName") && m.has("implementationName")) {
                meta.serverName = opt(m, "implementationName");
            }
        }
        return meta;
    }

    private AuthResult parseAuthResponse(JsonObject resp) throws AuthException {
        AuthResult r = new AuthResult();
        r.accessToken = opt(resp, "accessToken");
        if (r.accessToken.isEmpty()) {
            throw AuthException.local("littleskin-switcher.error.badResponse");
        }
        r.clientToken = opt(resp, "clientToken");

        // authenticate 会带 availableProfiles；refresh 只有 selectedProfile，此时保留账号里已有的角色列表
        if (resp.has("availableProfiles") && resp.get("availableProfiles").isJsonArray()) {
            JsonArray array = resp.getAsJsonArray("availableProfiles");
            for (JsonElement e : array) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject p = e.getAsJsonObject();
                String uuid = Uuids.normalize(opt(p, "id"));
                if (uuid != null) {
                    r.profiles.add(new Account.Profile(uuid, opt(p, "name")));
                }
            }
        }

        if (resp.has("selectedProfile") && resp.get("selectedProfile").isJsonObject()) {
            JsonObject p = resp.getAsJsonObject("selectedProfile");
            String uuid = Uuids.normalize(opt(p, "id"));
            if (uuid != null) {
                r.selectedProfileId = uuid;
                boolean known = false;
                for (Account.Profile existing : r.profiles) {
                    if (uuid.equals(Uuids.normalize(existing.uuid))) {
                        known = true;
                        break;
                    }
                }
                if (!known) {
                    r.profiles.add(new Account.Profile(uuid, opt(p, "name")));
                }
            }
        }
        return r;
    }

    private static String opt(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) {
            return "";
        }
        return o.get(key).getAsString();
    }
}
