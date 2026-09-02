package com.littleskin.switcher.mixin;

import com.littleskin.switcher.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
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
 *  - 右下角渲染“使用 LittleSkin / 使用正版”切换按钮与标记（文字走 Minecraft 标准多语言，
 *    按当前语言解析后的内容自动定宽）；
 *  - 点击按钮切换该服务器的 LittleSkin 登录标记（持久化到配置）。
 */
@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry")
public abstract class OnlineServerEntryMixin {
    private static final String KEY_USE_LITTLESKIN = "littleskin-switcher.serverList.useLittleSkin";
    private static final String KEY_USE_PREMIUM = "littleskin-switcher.serverList.usePremium";
    private static final int TOGGLE_HEIGHT = 12;
    private static final int TEXT_PAD = 4;

    @Shadow
    @Final
    private ServerData serverData;

    @Shadow
    @Final
    private Minecraft minecraft;

    /** 上次渲染时的切换按钮矩形，供点击判定使用（与画面所见一致）。 */
    @Unique
    private int littleskin_pillX;
    @Unique
    private int littleskin_pillY;
    @Unique
    private int littleskin_pillW;
    @Unique
    private int littleskin_pillH;

    @Inject(method = "extractContent", at = @At("RETURN"))
    private void littleskin_renderToggle(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a, CallbackInfo ci) {
        ObjectSelectionList.Entry<?> entry = (ObjectSelectionList.Entry<?>) (Object) this;
        boolean active = ModConfig.get().isLittleSkinServer(this.serverData.ip);
        Component label = Component.translatable(active ? KEY_USE_LITTLESKIN : KEY_USE_PREMIUM);
        int w = Math.max(44, this.minecraft.font.width(label.getString()) + TEXT_PAD * 2);
        int x = entry.getContentRight() - w - 2;
        int y = entry.getContentBottom() - TOGGLE_HEIGHT - 1;
        this.littleskin_pillX = x;
        this.littleskin_pillY = y;
        this.littleskin_pillW = w;
        this.littleskin_pillH = TOGGLE_HEIGHT;
        // 背景胶囊：LittleSkin 为橙色，正版为灰色。文字反映当前登录方式。
        graphics.fill(x, y, x + w, y + TOGGLE_HEIGHT, active ? 0xA0FFB300 : 0xA05A5A5A);
        graphics.text(this.minecraft.font, label, x + TEXT_PAD, y + 2, active ? 0xFF202020 : 0xFFD0D0D0);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void littleskin_onToggleClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (doubleClick) {
            return; // 双击走默认的加入服务器逻辑
        }
        if (this.littleskin_pillH <= 0) {
            return; // 还没有渲染过该条目，无法确定按钮位置
        }
        double x = event.x();
        double y = event.y();
        if (x >= this.littleskin_pillX && x <= this.littleskin_pillX + this.littleskin_pillW
                && y >= this.littleskin_pillY && y <= this.littleskin_pillY + this.littleskin_pillH) {
            ModConfig.get().toggleLittleSkinServer(this.serverData.ip);
            ModConfig.get().save();
            cir.setReturnValue(true);
        }
    }
}
