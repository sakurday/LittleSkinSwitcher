package com.littleskin.switcher;

import com.littleskin.switcher.auth.AuthException;
import com.littleskin.switcher.auth.AuthProvider;
import com.littleskin.switcher.auth.AuthProviders;
import com.littleskin.switcher.auth.AuthResult;
import com.littleskin.switcher.config.Account;
import com.littleskin.switcher.config.ModConfig;
import com.littleskin.switcher.mixin.MinecraftAccessor;
import com.littleskin.switcher.util.Uuids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话切换。
 *
 * 游戏只在握手时实时读取 {@code Minecraft.getUser()}，所以「切换账号」
 * 就是替换它；{@link #prepare} 负责在后台把 token 弄新鲜，{@link #apply}
 * 负责在主线程把它装上去。
 *
 * 启动器账户是默认身份：单人游戏、未配置的服务器都用它，且切换是零成本的。
 */
public final class SessionManager {
    /** 距上次刷新超过这个时长就重新刷新一次，避免拿着快过期的 token 去握手。 */
    private static final long TOKEN_FRESH_MS = 30 * 60 * 1000L;

    private static User launcherUser;
    private static volatile Account activeAccount;
    private static final Map<String, Long> preparedAt = new ConcurrentHashMap<>();

    private SessionManager() {
    }

    /** 记下启动器账户，供之后还原。必须在主线程首次调用。 */
    public static synchronized void ensureInit() {
        if (launcherUser != null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        launcherUser = mc.getUser();
    }

    public static User launcherUser() {
        ensureInit();
        return launcherUser;
    }

    /** 当前实际生效的账号（可能是启动器账户）。 */
    public static Account activeAccount() {
        return activeAccount;
    }

    /** 当前生效账号对应的登录方式，供握手使用。 */
    public static AuthProvider activeProvider() {
        Account a = activeAccount;
        return AuthProviders.forAccount(a);
    }

    /** 账号是否已经可以立刻使用（无需联网）。 */
    public static boolean isReady(Account account) {
        if (account == null) {
            return false;
        }
        if (account.isLauncher()) {
            return true;
        }
        Long at = preparedAt.get(account.id);
        return at != null
                && System.currentTimeMillis() - at < TOKEN_FRESH_MS
                && account.accessToken != null && !account.accessToken.isEmpty()
                && account.selectedProfile() != null;
    }

    /**
     * 确保账号可用：必要时刷新 token，刷新失败且手头还有用户刚输入的密码时静默重登。
     * 含网络请求，必须在后台线程调用。
     */
    public static void prepare(Account account) throws AuthException {
        if (account == null || account.isLauncher()) {
            return;
        }
        if (isReady(account)) {
            return;
        }

        boolean hasToken = account.accessToken != null && !account.accessToken.isEmpty();
        boolean hasPassword = account.password != null && !account.password.isEmpty();
        if (!hasToken && !hasPassword) {
            // 从没成功登录过的账户：说清楚要干什么，而不是报「刷新失败」
            throw AuthException.local("littleskin-switcher.error.needPasswordFor", account.label());
        }

        AuthProvider provider = AuthProviders.forAccount(account);
        AuthResult result;
        try {
            result = provider.refresh(account);
        } catch (AuthException refreshFailed) {
            String password = account.password;
            if (password == null || password.isEmpty()) {
                throw AuthException.local("littleskin-switcher.error.refreshFailed",
                        account.label(), refreshFailed.component());
            }
            LittleSkinSwitcher.LOGGER.info("[LittleSkinSwitcher] {} 的 token 刷新失败，用本次会话输入的密码重新登录",
                    account.label().getString());
            result = provider.login(account, password);
        }

        applyResult(account, result);
        if (account.selectedProfile() == null) {
            throw AuthException.local("littleskin-switcher.error.noProfile", account.label());
        }
        account.lastValid = true;
        ModConfig.get().save();
        markPrepared(account);
        LittleSkinSwitcher.LOGGER.info("[LittleSkinSwitcher] 账号 {} 已就绪（角色：{}）",
                account.label().getString(), account.selectedProfile().name);
    }

    /** 把认证结果写回账号。 */
    public static void applyResult(Account account, AuthResult result) {
        if (result.accessToken != null && !result.accessToken.isEmpty()) {
            account.accessToken = result.accessToken;
        }
        if (result.clientToken != null && !result.clientToken.isEmpty()) {
            account.clientToken = result.clientToken;
        }
        if (result.profiles != null && !result.profiles.isEmpty()) {
            account.profiles = new ArrayList<>(result.profiles);
        }
        String selected = Uuids.normalize(result.selectedProfileId);
        if (selected != null) {
            account.selectedProfile = selected;
        }
        // 只有一个角色时没必要让用户再点一次
        if (account.selectedProfile() == null && account.profiles.size() == 1) {
            account.selectedProfile = Uuids.normalize(account.profiles.get(0).uuid);
        }
    }

    /** 把账号装到游戏上（替换 Minecraft 的当前用户）。必须在主线程调用。 */
    public static void apply(Account account) throws AuthException {
        if (account == null || account.isLauncher()) {
            applyLauncher();
            return;
        }
        Account.Profile profile = account.selectedProfile();
        if (profile == null) {
            throw AuthException.local("littleskin-switcher.error.noProfile", account.label());
        }
        if (account.accessToken == null || account.accessToken.isEmpty()) {
            throw AuthException.local("littleskin-switcher.error.needPasswordFor", account.label());
        }
        UUID uuid = Uuids.parse(profile.uuid);
        if (uuid == null) {
            throw AuthException.local("littleskin-switcher.error.invalidProfileUuid");
        }
        if (profile.name == null || profile.name.isEmpty()) {
            throw AuthException.local("littleskin-switcher.error.noProfile", account.label());
        }
        User user = new User(profile.name, uuid, account.accessToken, Optional.empty(), Optional.empty());
        ((MinecraftAccessor) Minecraft.getInstance()).littleskin_setUser(user);
        activeAccount = account;
        LittleSkinSwitcher.LOGGER.info("[LittleSkinSwitcher] 已切换到 {}（角色 {}）",
                account.label().getString(), profile.name);
    }

    /** 恢复到启动器账户（默认身份）。 */
    public static void applyLauncher() {
        ensureInit();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && launcherUser != null && mc.getUser() != launcherUser) {
            ((MinecraftAccessor) mc).littleskin_setUser(launcherUser);
        }
        activeAccount = ModConfig.get().launcherAccount();
    }

    public static void markPrepared(Account account) {
        if (account != null && !account.isLauncher()) {
            preparedAt.put(account.id, System.currentTimeMillis());
        }
    }

    /** 认证被服务器拒绝时调用，让下次进入该服务器重新刷新 token。 */
    public static void markStale(Account account) {
        if (account != null) {
            preparedAt.remove(account.id);
        }
    }
}
