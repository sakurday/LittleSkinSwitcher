package com.littleskin.switcher.auth;

import com.littleskin.switcher.config.Account;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.UUID;

/**
 * 启动器登录方式。
 *
 * 完全不发起网络请求：会话就是启动器交给游戏的 {@link User}，
 * 握手校验也交给 {@code Minecraft.services().sessionService()} 原样处理——
 * 如果启动器已经注入了 authlib-injector，这里同样会把请求转给它，行为不变。
 */
public class LauncherAuthProvider implements AuthProvider {
    @Override
    public String typeId() {
        return Account.TYPE_LAUNCHER;
    }

    @Override
    public boolean requiresNetwork() {
        return false;
    }

    @Override
    public AuthResult login(Account account, String password) {
        return fromLauncherUser();
    }

    @Override
    public AuthResult refresh(Account account) {
        return fromLauncherUser();
    }

    public static AuthResult fromLauncherUser() {
        AuthResult r = new AuthResult();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return r;
        }
        User user = mc.getUser();
        r.accessToken = user.getAccessToken();
        Account.Profile profile = new Account.Profile(user.getProfileId().toString(), user.getName());
        r.profiles.add(profile);
        r.selectedProfileId = profile.uuid;
        return r;
    }

    @Override
    public void joinServer(Account account, UUID profileId, String accessToken, String serverId) throws AuthException {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            throw AuthException.local("littleskin-switcher.error.notReady");
        }
        try {
            mc.services().sessionService().joinServer(profileId, accessToken, serverId);
        } catch (com.mojang.authlib.exceptions.AuthenticationException e) {
            // 保留原始类型，游戏据此给出「会话失效」等具体提示
            throw new AuthException(com.littleskin.switcher.util.Components.orLiteral(e.getMessage()), e);
        }
    }
}
