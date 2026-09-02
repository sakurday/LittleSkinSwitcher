package com.littleskin.switcher;

import com.littleskin.switcher.auth.LittleSkinAuth;
import com.littleskin.switcher.auth.LittleSkinAuth.AuthResult;
import com.littleskin.switcher.config.ModConfig;
import com.littleskin.switcher.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 会话控制器：
 *  - 默认使用启动器登录的正版账户（不改变任何状态）；
 *  - 进入标记为 LittleSkin 的服务器前，切换到 LittleSkin 会话；
 *  - 会话切换通过替换 {@link Minecraft#getUser()} 实现，握手时游戏会实时读取它。
 */
public final class SessionController {
    /** 当前是否正处于 LittleSkin 登录模式（影响 joinServer 的转发目标）。 */
    private static volatile boolean littleSkinActive = false;

    /** 启动器登录的正版账户，用于还原。 */
    private static User originalUser;
    private static boolean initialized = false;

    private SessionController() {
    }

    /** 惰性初始化：保存原始（正版）会话。需要在第一次切换前调用。 */
    public static void ensureInit() {
        if (initialized) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        originalUser = mc.getUser();
        initialized = true;
    }

    /**
     * 激活 LittleSkin 会话：刷新或重新认证，然后替换 Minecraft 当前用户。
     * 若未配置账号会抛出异常。
     */
    public static void activateLittleSkin() throws Exception {
        ensureInit();
        ModConfig cfg = ModConfig.get();
        AuthResult result = refreshOrAuthenticate(cfg);

        // 更新配置中保存的会话
        cfg.account.accessToken = result.accessToken;
        if (result.clientToken != null && !result.clientToken.isEmpty()) {
            cfg.account.clientToken = result.clientToken;
        }
        if (result.profileId != null && !result.profileId.isEmpty()) {
            cfg.account.profileUuid = result.profileId;
            cfg.account.profileName = result.profileName;
        }
        cfg.account.valid = true;
        cfg.save();

        UUID uuid = uuidFromString(cfg.account.profileUuid);
        if (uuid == null) {
            throw new IllegalStateException(
                    Component.translatable("littleskin-switcher.error.invalidProfileUuid").getString());
        }
        User littleSkinUser = new User(cfg.account.profileName, uuid, cfg.account.accessToken, Optional.empty(), Optional.empty());
        ((MinecraftAccessor) Minecraft.getInstance()).littleskin_setUser(littleSkinUser);
        littleSkinActive = true;
        LittleSkinSwitcher.LOGGER.info("[LittleSkinSwitcher] 已切换到 LittleSkin 会话：{}", cfg.account.profileName);
    }

    private static AuthResult refreshOrAuthenticate(ModConfig cfg) throws Exception {
        // 优先尝试刷新（token 有效则无需重新认证）
        if (cfg.account.accessToken != null && !cfg.account.accessToken.isEmpty()) {
            try {
                AuthResult refreshed = LittleSkinAuth.refresh(cfg.account.accessToken, cfg.account.clientToken);
                if (refreshed.accessToken != null && !refreshed.accessToken.isEmpty()) {
                    return refreshed;
                }
            } catch (Exception e) {
                LittleSkinSwitcher.LOGGER.warn("[LittleSkinSwitcher] token 刷新失败，尝试密码重新认证：{}", e.getMessage());
            }
        }

        if (cfg.account.email == null || cfg.account.email.isEmpty()
                || cfg.account.password == null || cfg.account.password.isEmpty()) {
            throw new IllegalStateException(
                    Component.translatable("littleskin-switcher.error.notConfigured").getString());
        }
        return LittleSkinAuth.authenticate(cfg.account.email, cfg.account.password);
    }

    /** 恢复为正版会话（默认行为）。 */
    public static void deactivateLittleSkin() {
        ensureInit();
        if (originalUser != null) {
            ((MinecraftAccessor) Minecraft.getInstance()).littleskin_setUser(originalUser);
        }
        littleSkinActive = false;
    }

    public static boolean isLittleSkinActive() {
        return littleSkinActive;
    }

    private static UUID uuidFromString(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        String dashed = s;
        if (s.length() == 32) {
            dashed = s.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                    "$1-$2-$3-$4-$5");
        }
        try {
            return UUID.fromString(dashed);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
