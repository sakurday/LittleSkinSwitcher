package com.littleskin.switcher.gui;

import com.littleskin.switcher.LittleSkinSwitcher;
import com.littleskin.switcher.auth.YggdrasilAuthProvider;
import com.littleskin.switcher.config.Account;
import com.littleskin.switcher.util.Uuids;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 账户列表里的头像。
 *
 * 只做界面展示，不接管游戏内的皮肤加载（那是 CustomSkinLoader 之类模组的活）。
 * 这里的做法是把站点返回的 textures 属性塞进一个 GameProfile，
 * 再交给原版 {@link net.minecraft.client.resources.SkinManager} —— 下载、磁盘缓存、
 * 纹理注册全部由它完成，本类只负责把属性取回来并记住结果。
 */
public final class AccountAvatars {
    /** 头像边长；原版 8x8 的头部会放大到这个尺寸。 */
    public static final int SIZE = 16;
    /** 头像左侧留白，也是文字应该左移的基准。 */
    public static final int ROW_INSET = SIZE + 8;

    private record Key(String accountId, String profileId) {
    }

    /** 已向 SkinManager 注册过的头像；取值时未下载完会先回落到默认皮肤。 */
    private static final Map<Key, Supplier<PlayerSkin>> REGISTERED = new ConcurrentHashMap<>();
    /** 正在后台取属性，避免每帧重复发起。 */
    private static final Set<Key> PENDING = ConcurrentHashMap.newKeySet();
    /** 站点明确表示该角色没有皮肤，不必再问。 */
    private static final Set<Key> FAILED = ConcurrentHashMap.newKeySet();
    /** 网络出错时的下次重试时间，避免每帧都去打站点。 */
    private static final Map<Key, Long> RETRY_AFTER = new ConcurrentHashMap<>();
    private static final long RETRY_DELAY_MS = 30_000L;
    private static Supplier<PlayerSkin> launcherSkin;

    /** 画某个账户当前角色的头像。 */
    public static void renderFace(GuiGraphicsExtractor graphics, Account account, int x, int y) {
        renderFace(graphics, account, account.selectedProfile(), x, y);
    }

    /** 画指定角色的头像；profile 为 null 时画默认皮肤。 */
    public static void renderFace(GuiGraphicsExtractor graphics, Account account, Account.Profile profile, int x, int y) {
        PlayerFaceExtractor.extractRenderState(graphics, skinOf(account, profile), x, y, SIZE);
    }

    /** 账户头像是显示在文字左侧的，这里给出文字该从哪里开始。 */
    public static int textOffset(int x) {
        return x + ROW_INSET;
    }

    private static PlayerSkin skinOf(Account account, Account.Profile profile) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return DefaultPlayerSkin.get(Util.NIL_UUID);
        }
        if (account.isLauncher()) {
            if (launcherSkin == null) {
                // 启动器账户的档案里就带着纹理属性，交给原版即可
                launcherSkin = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
            }
            return launcherSkin.get();
        }
        if (profile == null) {
            return DefaultPlayerSkin.get(defaultUuidFor(account));
        }
        Key key = new Key(account.id, profile.uuid);
        Supplier<PlayerSkin> registered = REGISTERED.get(key);
        if (registered != null) {
            return registered.get();
        }
        request(account, profile, key);
        return DefaultPlayerSkin.get(defaultUuidFor(account));
    }

    private static UUID defaultUuidFor(Account account) {
        Account.Profile profile = account.selectedProfile();
        UUID uuid = Uuids.parse(profile == null ? null : profile.uuid);
        return uuid == null ? Util.NIL_UUID : uuid;
    }

    /** 后台取一次纹理属性；成功后回到主线程交给 SkinManager 注册。 */
    private static void request(Account account, Account.Profile profile, Key key) {
        UUID uuid = Uuids.parse(profile.uuid);
        if (uuid == null || FAILED.contains(key) || !PENDING.add(key)) {
            return;
        }
        if (System.currentTimeMillis() < RETRY_AFTER.getOrDefault(key, 0L)) {
            PENDING.remove(key);
            return;
        }
        String address = account.authServer;
        String name = profile.name;
        Util.ioPool().execute(() -> {
            Property textures = null;
            boolean failed = false;
            try {
                textures = new YggdrasilAuthProvider(address).fetchTexturesProperty(uuid);
            } catch (Exception e) {
                // 网络问题，稍后再试；站点确实没皮肤的情况在下面按 FAILED 处理
                failed = true;
                LittleSkinSwitcher.LOGGER.debug("[LittleSkinSwitcher] 取 {} 的头像失败：{}", name, e.getMessage());
            }
            Property result = textures;
            boolean transientFailure = failed;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                PENDING.remove(key);
                return;
            }
            mc.execute(() -> register(key, uuid, name, result, transientFailure));
        });
    }

    private static void register(Key key, UUID uuid, String name, Property textures, boolean transientFailure) {
        PENDING.remove(key);
        if (transientFailure) {
            RETRY_AFTER.put(key, System.currentTimeMillis() + RETRY_DELAY_MS);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            RETRY_AFTER.put(key, System.currentTimeMillis() + RETRY_DELAY_MS);
            return;
        }
        if (textures == null) {
            // 站点答了，只是这个角色没设皮肤
            FAILED.add(key);
            return;
        }
        try {
            // authlib 7 的 PropertyMap 是不可变的：EMPTY 背后是 ImmutableMultimap，
            // 对 new GameProfile(id, name) 出来的实例做 put 会抛 UnsupportedOperationException。
            // 原版也是先把 multimap 建好再交给构造函数（见 ByteBufCodecs.GAME_PROFILE_PROPERTIES）。
            PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", textures));
            GameProfile profile = new GameProfile(uuid, name == null || name.isEmpty() ? "?" : name, properties);
            // requireSecure=false：站点用自己的私钥签名，原版不认识它；这里本来也只用于界面展示
            REGISTERED.put(key, mc.getSkinManager().createLookup(profile, false));
            LittleSkinSwitcher.LOGGER.debug("[LittleSkinSwitcher] 已为 {} 注册头像", name);
        } catch (Exception e) {
            // 失败要记下来，否则会变成每帧重试一次网络请求
            FAILED.add(key);
            LittleSkinSwitcher.LOGGER.warn("[LittleSkinSwitcher] 为 {} 注册头像失败", name, e);
        }
    }

    private AccountAvatars() {
    }
}
