package com.littleskin.switcher.gui;

import com.littleskin.switcher.config.Account;
import com.littleskin.switcher.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * 账号列表。
 *
 * 启动器账户恒在第一位且不可删除；其余为用户自己添加的 Yggdrasil 账户。
 */
public class AccountsScreen extends Screen {
    private static final int LIST_WIDTH = 300;

    private final Screen lastScreen;
    private ScrollList<Account> list;
    private Button editButton;
    private Button deleteButton;
    private String selectedId = ModConfig.LAUNCHER_ACCOUNT_ID;

    public AccountsScreen(Screen lastScreen) {
        super(Component.translatable("littleskin-switcher.accounts.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        ModConfig cfg = ModConfig.get();
        int left = this.width / 2 - LIST_WIDTH / 2;
        int listTop = 44;
        int listHeight = Math.max(40, this.height - listTop - 66);

        this.list = new ScrollList<>(left, listTop, LIST_WIDTH, listHeight,
                Component.translatable("littleskin-switcher.accounts.title"),
                this::renderRow);
        // 选中项变了就必须重算按钮可用状态，否则「编辑 / 删除」会一直停在初次进入时的样子
        this.list.setOnSelectionChanged(account -> this.updateButtons());
        this.list.setItems(cfg.accounts());
        this.list.setSelected(this.initialSelection(cfg));
        this.addRenderableWidget(this.list);

        this.addRenderableWidget(Button.builder(
                        Component.translatable("littleskin-switcher.accounts.add"),
                        button -> this.minecraft.setScreen(new AccountEditScreen(this, ModConfig.get().newYggdrasilAccount(), true)))
                .bounds(left, this.height - 60, LIST_WIDTH, 20)
                .build());

        int third = (LIST_WIDTH - 8) / 3;
        this.editButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("littleskin-switcher.accounts.edit"),
                        button -> this.editSelected())
                .bounds(left, this.height - 36, third, 20)
                .build());
        this.deleteButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("littleskin-switcher.accounts.delete"),
                        button -> this.deleteSelected())
                .bounds(left + third + 4, this.height - 36, third, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(left + (third + 4) * 2, this.height - 36, LIST_WIDTH - (third + 4) * 2, 20)
                .build());

        this.updateButtons();
    }

    /**
     * 首次进入时默认选中第一个可编辑的账户。
     * 启动器账户不可编辑，选它会让「编辑 / 删除」一上来就是灰的。
     */
    private Account initialSelection(ModConfig cfg) {
        Account remembered = cfg.accountById(this.selectedId);
        if (remembered != null && !remembered.isLauncher()) {
            return remembered;
        }
        for (Account candidate : cfg.accounts()) {
            if (!candidate.isLauncher()) {
                return candidate;
            }
        }
        return cfg.launcherAccount();
    }

    private void updateButtons() {
        Account selected = this.list.selected();
        if (selected == null) {
            selected = ModConfig.get().launcherAccount();
            this.list.setSelected(selected);
        }
        boolean editable = !selected.isLauncher();
        this.editButton.active = editable;
        this.deleteButton.active = editable;
        this.selectedId = selected.id;
    }

    private void editSelected() {
        Account selected = this.list.selected();
        if (selected == null || selected.isLauncher()) {
            return;
        }
        this.selectedId = selected.id;
        this.minecraft.setScreen(new AccountEditScreen(this, selected, false));
    }

    private void deleteSelected() {
        Account selected = this.list.selected();
        if (selected == null || selected.isLauncher()) {
            return;
        }
        ModConfig cfg = ModConfig.get();
        cfg.removeAccount(selected.id);
        cfg.save();
        this.selectedId = ModConfig.LAUNCHER_ACCOUNT_ID;
        this.rebuildWidgets();
    }

    private void renderRow(GuiGraphicsExtractor graphics, Account account, int x, int y, int rowWidth,
                           boolean selected, boolean hovered) {
        int background = selected ? 0xC03A6EA5 : (hovered ? 0x50FFFFFF : 0x20FFFFFF);
        graphics.fill(x, y, x + rowWidth, y + ScrollList.ROW_HEIGHT - 2, background);
        AccountAvatars.renderFace(graphics, account, x + 4, y + 4);
        int textX = AccountAvatars.textOffset(x);
        int titleColor = account.isLauncher() ? 0xFFE0E0E0 : (account.lastValid ? 0xFFFFFFFF : 0xFFFFC060);
        graphics.text(this.font, account.label(), textX, y + 4, titleColor);
        graphics.text(this.font, account.detail(), textX, y + 14, 0xFFB0B0B0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, -1);
        graphics.centeredText(this.font, Component.translatable("littleskin-switcher.accounts.hint"),
                this.width / 2, 30, 0xFFA0A0A0);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}
