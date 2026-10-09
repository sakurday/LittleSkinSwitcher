package com.littleskin.switcher.config;

import com.littleskin.switcher.util.Uuids;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个可用的登录身份。
 *
 * 两种类型：
 *  - {@link #TYPE_LAUNCHER}：启动器提供的会话，模组不做任何认证，原样使用；
 *  - {@link #TYPE_YGGDRASIL}：任意 Yggdrasil（皮肤站）账户，LittleSkin 只是其中一个预设地址。
 */
public class Account {
    public static final String TYPE_LAUNCHER = "launcher";
    public static final String TYPE_YGGDRASIL = "yggdrasil";

    public static final String PRESET_LITTLESKIN = "littleskin";
    public static final String PRESET_CUSTOM = "custom";

    public String id = "";
    public String type = TYPE_YGGDRASIL;
    /** 用户自定义的显示名，留空时回退到角色名 / 站点域名。 */
    public String displayName = "";
    /** 预设来源，仅用于界面展示与「填充默认地址」，认证行为完全由 authServer 决定。 */
    public String preset = PRESET_CUSTOM;
    /** Yggdrasil 服务根地址（形如 https://example.com/api/yggdrasil）。 */
    public String authServer = "";
    /** 上次登录用的账号名，仅用于预填输入框。 */
    public String username = "";
    public String accessToken = "";
    public String clientToken = "";
    public List<Profile> profiles = new ArrayList<>();
    /** 选中的角色 UUID（规范化为小写带连字符），空表示尚未选择。 */
    public String selectedProfile = "";
    /** 最近一次认证 / 刷新是否成功。 */
    public boolean lastValid = false;

    /**
     * 用户刚刚输入的密码，仅存在于内存中，绝不写入配置文件。
     * 用途只有一个：同一局游戏内 refresh token 失效时静默重登一次；
     * 重启游戏后它一定是空的，此时会要求用户重新输入。
     */
    public transient String password = "";

    public boolean isLauncher() {
        return TYPE_LAUNCHER.equals(type);
    }

    public boolean isYggdrasil() {
        return !isLauncher();
    }

    /** 当前选中的角色；未选择时返回 null。 */
    public Profile selectedProfile() {
        String want = Uuids.normalize(selectedProfile);
        if (want == null) {
            return null;
        }
        for (Profile p : profiles) {
            if (want.equals(Uuids.normalize(p.uuid))) {
                return p;
            }
        }
        return null;
    }

    /** 界面展示用的名称。 */
    public Component label() {
        if (isLauncher()) {
            return Component.translatable("littleskin-switcher.account.launcher");
        }
        if (displayName != null && !displayName.isBlank()) {
            return Component.literal(displayName);
        }
        Profile p = selectedProfile();
        if (p != null) {
            return Component.literal(p.name);
        }
        String host = hostOf(authServer);
        return host == null
                ? Component.translatable("littleskin-switcher.account.unnamed")
                : Component.literal(host);
    }

    /** 列表里显示的副标题：站点地址 + 角色。 */
    public Component detail() {
        if (isLauncher()) {
            return Component.translatable("littleskin-switcher.account.launcherDetail");
        }
        String host = hostOf(authServer);
        Profile p = selectedProfile();
        if (host == null) {
            return Component.translatable("littleskin-switcher.account.unnamed");
        }
        return p == null
                ? Component.literal(host)
                : Component.literal(host + " · " + p.name);
    }

    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String s = url.trim();
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            s = s.substring(scheme + 3);
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        return s.isEmpty() ? null : s;
    }

    /** Yggdrasil 站点的某一个角色。 */
    public static class Profile {
        public String uuid = "";
        public String name = "";

        public Profile() {
        }

        public Profile(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }
}
