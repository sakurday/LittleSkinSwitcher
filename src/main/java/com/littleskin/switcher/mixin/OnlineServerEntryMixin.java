package com.littleskin.switcher.mixin;

import com.littleskin.switcher.config.Account;
import com.littleskin.switcher.config.ModConfig;
import com.littleskin.switcher.gui.ServerAccountScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务器列表中的每个在线服务器条目：
 *  - 右下角显示这台服务器当前使用的账号；
 *  - 点击打开账号选择界面。
 */
@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry")
public abstract class OnlineServerEntryMixin {
    private static final int PILL_HEIGHT = 12;
    private static final int TEXT_PAD = 4;

    private static final int COLOR_LAUNCHER = 0xA05A5A5A;
    private static final int COLOR_YGGDRASIL = 0xA0FFB300;
    private static final int COLOR_YGGDRASIL_UNVERIFIED = 0xA0B04040;

    @Shadow
    @Final
    private ServerData serverData;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private int littleskin_pillX;
    @Unique
    private int littleskin_pillY;
    @Unique
    private int littleskin_pillW;
    @Unique
    private int littleskin_pillH;

    @Inject(method = "extractContent", at = @At("RETURN"))
    private void littleskin_renderPill(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a, CallbackInfo ci) {
        ObjectSelectionList.Entry<?> entry = (ObjectSelectionList.Entry<?>) (Object) this;
        Account account = ModConfig.get().accountForServer(this.serverData.ip);
        Component label = account.label();

        int w = Math.max(44, this.minecraft.font.width(label) + TEXT_PAD * 2);
        int x = entry.getContentRight() - w - 2;
        int y = entry.getContentBottom() - PILL_HEIGHT - 1;
        this.littleskin_pillX = x;
        this.littleskin_pillY = y;
        this.littleskin_pillW = w;
        this.littleskin_pillH = PILL_HEIGHT;

        int background = account.isLauncher()
                ? COLOR_LAUNCHER
                : (account.lastValid ? COLOR_YGGDRASIL : COLOR_YGGDRASIL_UNVERIFIED);
        graphics.fill(x, y, x + w, y + PILL_HEIGHT, background);
        graphics.text(this.minecraft.font, label, x + TEXT_PAD, y + 2,
                account.isLauncher() ? 0xFFD0D0D0 : 0xFF202020);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void littleskin_onPillClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (doubleClick || this.littleskin_pillH <= 0) {
            return;
        }
        double x = event.x();
        double y = event.y();
        if (x < this.littleskin_pillX || x > this.littleskin_pillX + this.littleskin_pillW
                || y < this.littleskin_pillY || y > this.littleskin_pillY + this.littleskin_pillH) {
            return;
        }
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof JoinMultiplayerScreen screen) {
            Minecraft.getInstance().setScreen(new ServerAccountScreen(
                    screen, this.serverData.name, this.serverData.ip));
        }
        cir.setReturnValue(true);
    }
}
