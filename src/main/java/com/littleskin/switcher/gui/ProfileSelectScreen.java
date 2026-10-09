package com.littleskin.switcher.gui;

import com.littleskin.switcher.config.Account;
import com.littleskin.switcher.config.ModConfig;
import com.littleskin.switcher.util.Uuids;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

/** 选择一个 Yggdrasil 账户下的角色。 */
public class ProfileSelectScreen extends Screen {
    private static final int LIST_WIDTH = 260;

    private final Screen lastScreen;
    private final Account account;
    private ScrollList<Account.Profile> list;

    public ProfileSelectScreen(Screen lastScreen, Account account) {
        super(Component.translatable("littleskin-switcher.profile.title"));
        this.lastScreen = lastScreen;
        this.account = account;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - LIST_WIDTH / 2;
        int top = 44;
        int height = Math.max(40, this.height - top - 34);

        this.list = new ScrollList<>(left, top, LIST_WIDTH, height,
                this.title, this::renderRow);
        this.list.setItems(new ArrayList<>(this.account.profiles));
        Account.Profile current = this.account.selectedProfile();
        if (current != null) {
            for (Account.Profile p : this.account.profiles) {
                if (Uuids.normalize(p.uuid).equals(Uuids.normalize(current.uuid))) {
                    this.list.setSelected(p);
                    break;
                }
            }
        }
        this.addRenderableWidget(this.list);

        this.addRenderableWidget(Button.builder(Component.translatable("littleskin-switcher.profile.confirm"),
                        button -> this.confirm())
                .bounds(left, this.height - 28, LIST_WIDTH, 20)
                .build());
    }

    private void renderRow(GuiGraphicsExtractor graphics, Account.Profile profile, int x, int y, int rowWidth,
                           boolean selected, boolean hovered) {
        int background = selected ? 0xC03A6EA5 : (hovered ? 0x50FFFFFF : 0x20FFFFFF);
        graphics.fill(x, y, x + rowWidth, y + ScrollList.ROW_HEIGHT - 2, background);
        // 同一账户下不同角色有各自的皮肤，所以头像按角色取
        AccountAvatars.renderFace(graphics, this.account, profile, x + 4, y + 4);
        int textX = AccountAvatars.textOffset(x);
        graphics.text(this.font, profile.name, textX, y + 8, 0xFFFFFFFF);
        // 完整 UUID 太长，只显示前 8 位就够区分了
        String uuid = profile.uuid == null ? "" : profile.uuid;
        String shortId = uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
        graphics.text(this.font, shortId, x + rowWidth - 5 - this.font.width(shortId), y + 8, 0xFF909090);
    }

    private void confirm() {
        Account.Profile selected = this.list.selected();
        if (selected != null) {
            String uuid = Uuids.normalize(selected.uuid);
            if (uuid != null) {
                account.selectedProfile = uuid;
                ModConfig.get().save();
            }
        }
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, -1);
        graphics.centeredText(this.font, Component.translatable("littleskin-switcher.profile.hint"),
                this.width / 2, 30, 0xFFA0A0A0);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}
