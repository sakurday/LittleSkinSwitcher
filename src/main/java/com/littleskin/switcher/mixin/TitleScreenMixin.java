package com.littleskin.switcher.mixin;

import com.littleskin.switcher.SessionManager;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 回到标题界面时恢复启动器账户。
 * 这样进入单人游戏始终使用正版身份（离开任何服务器回到标题即还原）。
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void littleskin_restoreLauncher(CallbackInfo ci) {
        SessionManager.ensureInit();
        SessionManager.applyLauncher();
    }
}
