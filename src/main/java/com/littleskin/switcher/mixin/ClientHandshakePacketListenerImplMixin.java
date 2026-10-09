package com.littleskin.switcher.mixin;

import com.littleskin.switcher.SessionManager;
import com.littleskin.switcher.auth.AuthException;
import com.littleskin.switcher.auth.AuthProvider;
import com.littleskin.switcher.config.Account;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * 登录握手的关键拦截点。
 *
 * 游戏在 {@code authenticateServer} 里调用 sessionService().joinServer(...) 做会话校验。
 * 这里不再判断「是不是 LittleSkin」，而是问当前生效的账号用哪种登录方式，
 * 交给它自己处理——启动器方式会原样转发给 Mojang（或启动器注入的 authlib-injector）。
 */
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakePacketListenerImplMixin {
    @Redirect(
            method = "authenticateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;joinServer(Ljava/util/UUID;Ljava/lang/String;Ljava/lang/String;)V"
            )
    )
    private void littleskin_joinServer(MinecraftSessionService service, UUID profileId, String accessToken, String serverId)
            throws AuthenticationException {
        Account account = SessionManager.activeAccount();
        AuthProvider provider = SessionManager.activeProvider();
        try {
            provider.joinServer(account, profileId, accessToken, serverId);
        } catch (AuthException e) {
            // 这次校验用的 token 已经不可信了，下次进入时重新刷新
            SessionManager.markStale(account);
            if (e.getCause() instanceof AuthenticationException authlibFailure) {
                // 保留 authlib 的原始异常类型，游戏才能给出「会话已失效」这类具体提示
                throw authlibFailure;
            }
            throw new AuthenticationException(e.getMessage(), e);
        }
    }
}
