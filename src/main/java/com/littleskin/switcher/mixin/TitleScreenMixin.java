package com.littleskin.switcher.mixin;

import com.littleskin.switcher.SessionController;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 回到标题界面时恢复正版会话。
 * 这样进入单人游戏始终使用正版身份（离开任何服务器回到标题即还原）。
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void littleskin_restorePremium(CallbackInfo ci) {
        SessionController.ensureInit();
        SessionController.deactivateLittleSkin();
    }
}
