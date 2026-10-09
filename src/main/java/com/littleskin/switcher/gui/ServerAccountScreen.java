package com.littleskin.switcher.gui;

import com.littleskin.switcher.config.Account;
import com.littleskin.switcher.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * 为一台服务器指定登录账号。
 *
 * 选择「启动器账户」即恢复默认行为（该服务器不再做任何切换）。
 */
public class ServerAccountScreen extends Screen {
    private static final int LIST_WIDTH = 300;

    private final Screen lastScreen;
    private final String address;
    private final Component serverLabel;
    private ScrollList<Account> list;

    public ServerAccountScreen(Screen lastScreen, String serverName, String address) {
        super(Component.translatable("littleskin-switcher.serverAccount.title"));
        this.lastScreen = lastScreen;
        this.address = address;
        this.serverLabel = Component.literal(serverName == null || serverName.isBlank() ? address : serverName);
    }

    @Override
    protected void init() {
        ModConfig cfg = ModConfig.get();
        int left = this.width / 2 - LIST_WIDTH / 2;
        int top = 46;
        int height = Math.max(40, this.height - top - 34);

        this.list = new ScrollList<>(left, top, LIST_WIDTH, height, this.title, this::renderRow);
        this.list.setItems(cfg.accounts());
        this.list.setSelected(cfg.accountForServer(this.address));
        this.addRenderableWidget(this.list);

        this.addRenderableWidget(Button.builder(Component.translatable("littleskin-switcher.serverAccount.confirm"),
                        button -> this.confirm())
                .bounds(left, this.height - 28, LIST_WIDTH / 2 - 2, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(left + LIST_WIDTH / 2 + 2, this.height - 28, LIST_WIDTH / 2 - 2, 20)
                .build());
    }

    private void renderRow(GuiGraphicsExtractor graphics, Account account, int x, int y, int rowWidth,
                           boolean selected, boolean hovered) {
        int background = selected ? 0xC03A6EA5 : (hovered ? 0x50FFFFFF : 0x20FFFFFF);
        graphics.fill(x, y, x + rowWidth, y + ScrollList.ROW_HEIGHT - 2, background);
        AccountAvatars.renderFace(graphics, account, x + 4, y + 4);
        int textX = AccountAvatars.textOffset(x);
        graphics.text(this.font, account.label(), textX, y + 4, 0xFFFFFFFF);
        graphics.text(this.font, account.detail(), textX, y + 14, 0xFFB0B0B0);
    }

    private void confirm() {
        Account selected = this.list.selected();
        ModConfig cfg = ModConfig.get();
        cfg.setAccountForServer(this.address, selected == null ? null : selected.id);
        cfg.save();
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, 14, -1);
        graphics.centeredText(this.font, this.serverLabel, this.width / 2, 28, 0xFFFFD080);
        graphics.centeredText(this.font, Component.literal(this.address), this.width / 2, 38, 0xFF909090);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}
