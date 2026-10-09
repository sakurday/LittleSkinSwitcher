package com.littleskin.switcher.mixin;

import com.littleskin.switcher.LittleSkinSwitcher;
import com.littleskin.switcher.SessionManager;
import com.littleskin.switcher.auth.AuthException;
import com.littleskin.switcher.config.Account;
import com.littleskin.switcher.config.ModConfig;
import com.littleskin.switcher.gui.AccountsScreen;
import com.littleskin.switcher.gui.SessionLoadingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务器列表界面：
 *  - 左上角添加「账户」按钮；
 *  - 连接服务器前，按该服务器配置的账号切换会话。
 *
 * 切换含网络请求（刷新 token），因此不在渲染线程上直接做：
 * 需要联网时先转到 {@link SessionLoadingScreen}，认证完成后再继续连接。
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void littleskin_onInit(CallbackInfo ci) {
        JoinMultiplayerScreen self = (JoinMultiplayerScreen) (Object) this;
        // 回到服务器列表即恢复默认身份；单人游戏因此始终使用启动器账户
        SessionManager.ensureInit();
        SessionManager.applyLauncher();

        ((ScreenAccessor) self).littleskin_addRenderableWidget(
                Button.builder(Component.translatable("littleskin-switcher.accountsButton"),
                                button -> Minecraft.getInstance().setScreen(new AccountsScreen(self)))
                        .bounds(5, 6, 110, 20)
                        .build());
    }

    @Inject(method = "join", at = @At("HEAD"), cancellable = true)
    private void littleskin_onJoin(ServerData data, CallbackInfo ci) {
        if (data == null || data.ip == null || data.ip.isEmpty()) {
            return;
        }
        // 局域网世界的地址不稳定，不适合做账号绑定
        if (data.isLan()) {
            return;
        }
        JoinMultiplayerScreen self = (JoinMultiplayerScreen) (Object) this;
        Account target = ModConfig.get().accountForServer(data.ip);

        if (SessionManager.isReady(target)) {
            try {
                SessionManager.apply(target);
            } catch (AuthException e) {
                littleskin_reportFailure(e);
                ci.cancel();
            }
            return;
        }

        ci.cancel();
        Minecraft.getInstance().setScreen(new SessionLoadingScreen(self, data, target));
    }

    private static void littleskin_reportFailure(AuthException e) {
        LittleSkinSwitcher.LOGGER.error("[LittleSkinSwitcher] 账号不可用", e);
        SystemToast.addOrUpdate(
                Minecraft.getInstance().getToastManager(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("littleskin-switcher.toast.loginFailedTitle"),
                e.component());
    }
}
