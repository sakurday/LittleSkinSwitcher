package com.littleskin.switcher.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.littleskin.switcher.util.ServerAddresses;
import com.littleskin.switcher.util.Uuids;

import java.util.UUID;

/**
 * v1 -> v2 配置升级。
 *
 * v1 的结构是「单个 LittleSkin 账户 + 一个服务器地址列表」：
 * <pre>
 *   { "account": { email, password, accessToken, clientToken, profileUuid, profileName, valid },
 *     "littleSkinServers": ["a.example.com"],
 *     "authServer": "https://littleskin.cn/api/yggdrasil" }
 * </pre>
 * 升级后把那个账户变成一个 preset 为 littleskin 的 Yggdrasil 账号，
 * 并把 littleSkinServers 的每个地址都指向它。
 *
 * 注意：v1 的明文密码不会被迁移过来（v2 只保存 refresh token），
 * 用户下次 token 失效时重新输入一次密码即可。
 */
final class ConfigMigration {
    private static final String DEFAULT_AUTHSERVER = "https://littleskin.cn/api/yggdrasil";

    /** 就地升级 root；返回是否发生了迁移。 */
    static boolean migrate(JsonObject root) {
        JsonElement versionElement = root.get("version");
        if (versionElement != null && !versionElement.isJsonNull()
                && versionElement.getAsInt() >= ModConfig.CURRENT_VERSION) {
            return false;
        }

        JsonObject legacyAccount = root.has("account") && root.get("account").isJsonObject()
                ? root.getAsJsonObject("account") : null;
        JsonArray legacyServers = root.has("littleSkinServers") && root.get("littleSkinServers").isJsonArray()
                ? root.getAsJsonArray("littleSkinServers") : null;

        boolean hadAccountData = legacyAccount != null
                && (!optString(legacyAccount, "email").isEmpty()
                || !optString(legacyAccount, "accessToken").isEmpty()
                || !optString(legacyAccount, "profileUuid").isEmpty());
        boolean hadServerMarks = legacyServers != null && !legacyServers.isEmpty();

        JsonArray accounts = new JsonArray();
        accounts.add(launcherAccount());

        String yggdrasilId = null;
        if (hadAccountData || hadServerMarks) {
            yggdrasilId = "acc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            accounts.add(legacyToYggdrasil(legacyAccount, root, yggdrasilId));
        }

        JsonObject serverAccounts = new JsonObject();
        if (legacyServers != null && yggdrasilId != null) {
            for (JsonElement e : legacyServers) {
                if (!e.isJsonPrimitive()) {
                    continue;
                }
                String key = ServerAddresses.normalize(e.getAsString());
                if (key != null && !key.isEmpty()) {
                    serverAccounts.addProperty(key, yggdrasilId);
                }
            }
        }

        root.remove("account");
        root.remove("littleSkinServers");
        root.remove("authServer");
        root.addProperty("version", ModConfig.CURRENT_VERSION);
        root.add("accounts", accounts);
        root.add("serverAccounts", serverAccounts);
        return true;
    }

    private static JsonObject launcherAccount() {
        JsonObject o = new JsonObject();
        o.addProperty("id", ModConfig.LAUNCHER_ACCOUNT_ID);
        o.addProperty("type", Account.TYPE_LAUNCHER);
        o.addProperty("preset", Account.TYPE_LAUNCHER);
        return o;
    }

    private static JsonObject legacyToYggdrasil(JsonObject legacy, JsonObject root, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("type", Account.TYPE_YGGDRASIL);
        o.addProperty("preset", Account.PRESET_LITTLESKIN);

        String authServer = optString(root, "authServer");
        o.addProperty("authServer", authServer.isEmpty() ? DEFAULT_AUTHSERVER : authServer);

        if (legacy != null) {
            o.addProperty("username", optString(legacy, "email"));
            o.addProperty("accessToken", optString(legacy, "accessToken"));
            o.addProperty("clientToken", optString(legacy, "clientToken"));
            o.addProperty("lastValid", legacy.has("valid") && legacy.get("valid").isJsonPrimitive()
                    && legacy.get("valid").getAsBoolean());

            String uuid = Uuids.normalize(optString(legacy, "profileUuid"));
            String name = optString(legacy, "profileName");
            JsonArray profiles = new JsonArray();
            if (uuid != null) {
                JsonObject profile = new JsonObject();
                profile.addProperty("uuid", uuid);
                profile.addProperty("name", name);
                profiles.add(profile);
                o.addProperty("selectedProfile", uuid);
            }
            o.add("profiles", profiles);
        } else {
            o.add("profiles", new JsonArray());
        }
        return o;
    }

    private static String optString(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) {
            return "";
        }
        return o.get(key).getAsString();
    }

    private ConfigMigration() {
    }
}
