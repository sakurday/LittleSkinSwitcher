package com.littleskin.switcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.littleskin.switcher.LittleSkinSwitcher;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 模组配置，持久化到 <config>/littleskin-switcher.json。
 *
 * account:            LittleSkin 账户信息（用于进入 LittleSkin 服务器时自动登录）
 * littleSkinServers:  标记为使用 LittleSkin 登录的服务器地址列表
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("littleskin-switcher.json");

    public Account account = new Account();
    public List<String> littleSkinServers = new ArrayList<>();
    /** Yggdrasil 服务根地址，默认 LittleSkin。修改后需要重新登录。 */
    public String authServer = "https://littleskin.cn/api/yggdrasil";

    /** LittleSkin 账户信息。 */
    public static class Account {
        public String email = "";
        /** 明文保存，用于 token 失效时自动重新认证（个人客户端模组常见做法）。 */
        public String password = "";
        public String accessToken = "";
        public String clientToken = "";
        public String profileUuid = "";
        public String profileName = "";
        /** 上次认证/刷新后 accessToken 是否可用。 */
        public boolean valid = false;
    }

    private static ModConfig INSTANCE;

    public static ModConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                ModConfig cfg = GSON.fromJson(Files.readString(CONFIG_PATH), ModConfig.class);
                if (cfg != null) {
                    if (cfg.account == null) cfg.account = new Account();
                    if (cfg.littleSkinServers == null) cfg.littleSkinServers = new ArrayList<>();
                    return cfg;
                }
            } catch (Exception e) {
                LittleSkinSwitcher.LOGGER.error("[LittleSkinSwitcher] 读取配置失败", e);
            }
        }
        return new ModConfig();
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            LittleSkinSwitcher.LOGGER.error("[LittleSkinSwitcher] 保存配置失败", e);
        }
    }

    /** 服务器地址是否被标记为使用 LittleSkin 登录。 */
    public boolean isLittleSkinServer(String address) {
        if (address == null) {
            return false;
        }
        for (String s : littleSkinServers) {
            if (s.equalsIgnoreCase(address)) {
                return true;
            }
            // 兼容“带端口 / 不带端口”的写法差异（25565 为默认端口）
            if (!address.contains(":") && (s + ":25565").equalsIgnoreCase(address)) {
                return true;
            }
            if (!s.contains(":") && (address + ":25565").equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    /** 切换一个服务器的 LittleSkin 标记，返回切换后的状态。 */
    public boolean toggleLittleSkinServer(String address) {
        boolean currently = isLittleSkinServer(address);
        if (currently) {
            littleSkinServers.removeIf(s -> s.equalsIgnoreCase(address));
            return false;
        } else {
            littleSkinServers.removeIf(s -> s.equalsIgnoreCase(address));
            littleSkinServers.add(address);
            return true;
        }
    }
}
