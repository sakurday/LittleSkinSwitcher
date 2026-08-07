package com.littleskin.switcher.mixin;

import com.littleskin.switcher.SessionController;
import com.littleskin.switcher.auth.LittleSkinAuth;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * 登录握手的关键拦截点：
 * 游戏在 {@code authenticateServer} 中调用 {@code sessionService().joinServer(...)}
 * 向 Mojang 会话服务器校验。这里在 LittleSkin 模式下把该请求转发到 LittleSkin。
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
    private void littleskin_redirectJoinServer(MinecraftSessionService service, UUID profileId, String accessToken, String serverId)
            throws AuthenticationException {
        if (SessionController.isLittleSkinActive()) {
            try {
                LittleSkinAuth.joinServer(accessToken, profileId, serverId);
            } catch (Exception e) {
                throw new AuthenticationException("LittleSkin 登录失败: " + e.getMessage(), e);
            }
        } else {
            service.joinServer(profileId, accessToken, serverId);
        }
    }
}
