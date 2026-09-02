package com.littleskin.switcher.mixin;

import com.littleskin.switcher.LittleSkinSwitcher;
import com.littleskin.switcher.SessionController;
import com.littleskin.switcher.config.ModConfig;
import com.littleskin.switcher.gui.LittleSkinConfigScreen;
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
 * 服务器列表界面（JoinMultiplayerScreen）：
 *  - 左上角添加“配置 LittleSkin”按钮（文案为标准翻译键，切换语言即时生效）；
 *  - 连接服务器前根据目标服务器切换正版 / LittleSkin 会话。
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void littleskin_onInit(CallbackInfo ci) {
        JoinMultiplayerScreen self = (JoinMultiplayerScreen) (Object) this;
        // 打开服务器列表时默认回到正版登录
        SessionController.ensureInit();
        SessionController.deactivateLittleSkin();

        ((ScreenAccessor) self).littleskin_addRenderableWidget(
                Button.builder(Component.translatable("littleskin-switcher.configureButton"),
                                button -> Minecraft.getInstance().setScreen(new LittleSkinConfigScreen(self)))
                        .bounds(5, 6, 100, 20)
                        .build());
    }

    @Inject(method = "join", at = @At("HEAD"), cancellable = true)
    private void littleskin_onJoin(ServerData data, CallbackInfo ci) {
        if (data == null || data.ip == null || data.ip.isEmpty()) {
            return;
        }
        SessionController.ensureInit();
        if (ModConfig.get().isLittleSkinServer(data.ip)) {
            try {
                SessionController.activateLittleSkin();
            } catch (Exception e) {
                LittleSkinSwitcher.LOGGER.error("[LittleSkinSwitcher] 进入 LittleSkin 服务器前认证失败", e);
                ci.cancel();
                Minecraft mc = Minecraft.getInstance();
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                SystemToast.addOrUpdate(
                        mc.getToastManager(),
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("littleskin-switcher.toast.loginFailedTitle"),
                        Component.literal(message));
            }
        } else {
            SessionController.deactivateLittleSkin();
        }
    }
}
