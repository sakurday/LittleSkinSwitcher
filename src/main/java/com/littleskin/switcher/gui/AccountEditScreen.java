package com.littleskin.switcher.gui;

import com.littleskin.switcher.LittleSkinSwitcher;
import com.littleskin.switcher.SessionManager;
import com.littleskin.switcher.auth.AuthException;
import com.littleskin.switcher.auth.AuthResult;
import com.littleskin.switcher.auth.YggdrasilAuthProvider;
import com.littleskin.switcher.config.Account;
import com.littleskin.switcher.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * 编辑一个 Yggdrasil 账户：站点地址由玩家自己填写，LittleSkin 只是一个快捷填充。
 *
 * 密码只在「登录」时用一次，随后只保留 refresh token；刷新失败时会再问一次密码。
 */
public class AccountEditScreen extends Screen {
    private static final int FIELD_WIDTH = 300;
    private static final int FIELD_HEIGHT = 18;

    private final Screen lastScreen;
    private final Account account;
    private final boolean isNew;

    private EditBox displayNameField;
    private EditBox addressField;
    private EditBox usernameField;
    private EditBox passwordField;
    private Button loginButton;
    private Button profileButton;

    private Component status = Component.empty();
    private boolean busy;

    public AccountEditScreen(Screen lastScreen, Account account, boolean isNew) {
        super(Component.translatable(isNew
                ? "littleskin-switcher.edit.titleAdd"
                : "littleskin-switcher.edit.title"));
        this.lastScreen = lastScreen;
        this.account = account;
        this.isNew = isNew;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - FIELD_WIDTH / 2;

        this.displayNameField = this.addField(left, 36,
                Component.translatable("littleskin-switcher.edit.displayName"), this.account.displayName, 32);

        this.addressField = this.addField(left, 68,
                Component.translatable("littleskin-switcher.edit.address"), this.account.authServer, 128);

        this.addRenderableWidget(Button.builder(
                        Component.translatable("littleskin-switcher.edit.useLittleSkin"),
                        button -> this.addressField.setValue(YggdrasilAuthProvider.LITTLESKIN))
                .bounds(left, 90, FIELD_WIDTH, FIELD_HEIGHT + 2)
                .build());

        this.usernameField = this.addField(left, 120,
                Component.translatable("littleskin-switcher.edit.username"), this.account.username, 64);

        this.passwordField = this.addField(left, 152,
                Component.translatable("littleskin-switcher.edit.password"), "", 128);
        this.passwordField.addFormatter((text, offset) ->
                Component.literal("*".repeat(text.length())).getVisualOrderText());

        this.loginButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("littleskin-switcher.edit.login"), button -> this.tryLogin())
                .bounds(left, this.height - 52, FIELD_WIDTH / 2 - 2, 20)
                .build());

        this.profileButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("littleskin-switcher.edit.selectProfile"),
                        button -> this.openProfileSelect())
                .bounds(left + FIELD_WIDTH / 2 + 2, this.height - 52, FIELD_WIDTH / 2 - 2, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("littleskin-switcher.edit.save"),
                        button -> this.save())
                .bounds(left, this.height - 28, FIELD_WIDTH / 2 - 2, 20)
                .build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose())
                .bounds(left + FIELD_WIDTH / 2 + 2, this.height - 28, FIELD_WIDTH / 2 - 2, 20)
                .build());

        this.setInitialFocus(this.addressField);
        this.updateButtons();
    }

    private EditBox addField(int x, int y, Component label, String value, int maxLength) {
        EditBox box = new EditBox(this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT, label);
        box.setValue(value == null ? "" : value);
        box.setMaxLength(maxLength);
        this.addWidget(box);
        return box;
    }

    /** 有临时消息（错误 / 进行中）就显示它，否则显示账号当前状态。 */
    private Component displayStatus() {
        if (this.status != null && !this.status.getString().isEmpty()) {
            return this.status;
        }
        return this.describeState();
    }

    private Component describeState() {
        if (this.account.isLauncher()) {
            return Component.empty();
        }
        Account.Profile profile = this.account.selectedProfile();
        if (profile == null) {
            return Component.translatable("littleskin-switcher.edit.status.noProfile");
        }
        return Component.translatable(this.account.lastValid
                        ? "littleskin-switcher.edit.status.loggedIn"
                        : "littleskin-switcher.edit.status.needsLogin",
                profile.name);
    }

    private void updateButtons() {
        this.loginButton.active = !this.busy;
        this.profileButton.active = !this.busy && !this.account.profiles.isEmpty();
    }

    /** 保存按钮：只写回字段，不发起认证。 */
    private void save() {
        if (this.busy) {
            return;
        }
        if (!this.applyFields()) {
            return;
        }
        ModConfig cfg = ModConfig.get();
        cfg.addAccount(this.account);
        cfg.save();
        this.onClose();
    }

    /** 把输入框内容写回账号；地址非法时显示错误并返回 false。 */
    private boolean applyFields() {
        this.account.displayName = this.displayNameField.getValue().trim();
        String username = this.usernameField.getValue().trim();
        if (!username.isEmpty()) {
            this.account.username = username;
        }
        String address = this.addressField.getValue().trim();
        try {
            this.account.authServer = YggdrasilAuthProvider.normalizeBaseUrl(address);
        } catch (AuthException e) {
            this.status = e.component();
            return false;
        }
        this.addressField.setValue(this.account.authServer);
        return true;
    }

    private void tryLogin() {
        if (this.busy || !this.applyFields()) {
            return;
        }
        String username = this.usernameField.getValue().trim();
        String password = this.passwordField.getValue();
        if (username.isEmpty()) {
            this.status = Component.translatable("littleskin-switcher.error.emptyUsername");
            return;
        }
        if (password.isEmpty()) {
            this.status = Component.translatable("littleskin-switcher.error.emptyPassword");
            return;
        }

        this.account.username = username;
        // 密码只留在内存里，供本局游戏内 token 失效时静默重登
        this.account.password = password;
        this.busy = true;
        this.status = Component.translatable("littleskin-switcher.edit.status.loggingIn");
        this.updateButtons();

        String address = this.account.authServer;
        Util.ioPool().execute(() -> {
            try {
                YggdrasilAuthProvider provider = new YggdrasilAuthProvider(address);
                AuthResult result = provider.login(this.account, password);
                SessionManager.applyResult(this.account, result);

                // 站点名用来给账户起个默认名字，拿不到就算了
                String siteName = "";
                try {
                    siteName = provider.fetchMetadata().serverName;
                } catch (AuthException ignored) {
                    // 元数据不是必须的
                }
                this.account.lastValid = true;

                String finalSiteName = siteName;
                Minecraft.getInstance().execute(() -> {
                    if (this.account.displayName.isBlank() && !finalSiteName.isBlank()) {
                        this.account.displayName = finalSiteName;
                        this.displayNameField.setValue(finalSiteName);
                    }
                    ModConfig cfg = ModConfig.get();
                    cfg.addAccount(this.account);
                    cfg.save();
                    SessionManager.markPrepared(this.account);
                    this.busy = false;
                    this.updateButtons();
                    this.afterLogin();
                });
            } catch (AuthException e) {
                LittleSkinSwitcher.LOGGER.warn("[LittleSkinSwitcher] 登录失败", e);
                Minecraft.getInstance().execute(() -> {
                    this.busy = false;
                    this.status = e.component();
                    this.updateButtons();
                });
            } catch (Exception e) {
                LittleSkinSwitcher.LOGGER.error("[LittleSkinSwitcher] 登录异常", e);
                Minecraft.getInstance().execute(() -> {
                    this.busy = false;
                    this.status = Component.literal(String.valueOf(e.getMessage()));
                    this.updateButtons();
                });
            }
        });
    }

    /** 打开角色选择；清掉临时消息，这样选完回来会重新显示最新状态。 */
    private void openProfileSelect() {
        this.status = Component.empty();
        this.minecraft.setScreen(new ProfileSelectScreen(this, this.account));
    }

    private void afterLogin() {
        Account.Profile profile = this.account.selectedProfile();
        if (profile == null || this.account.profiles.size() > 1) {
            // 让用户明确挑一个角色，而不是替他们猜
            this.openProfileSelect();
            return;
        }
        this.status = Component.translatable("littleskin-switcher.edit.status.success", profile.name);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && !this.busy) {
            this.tryLogin();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int left = this.width / 2 - FIELD_WIDTH / 2;
        graphics.centeredText(this.font, this.title, this.width / 2, 12, -1);

        graphics.text(this.font, Component.translatable("littleskin-switcher.edit.displayName"), left, 26, 0xFFA0A0A0);
        graphics.text(this.font, Component.translatable("littleskin-switcher.edit.address"), left, 58, 0xFFA0A0A0);
        graphics.text(this.font, Component.translatable("littleskin-switcher.edit.username"), left, 110, 0xFFA0A0A0);
        graphics.text(this.font, Component.translatable("littleskin-switcher.edit.password"), left, 142, 0xFFA0A0A0);
        graphics.text(this.font, Component.translatable("littleskin-switcher.edit.passwordHint"),
                left + 40, 142, 0xFF6E6E6E);

        graphics.text(this.font, this.displayStatus(), left, Math.max(176, this.height - 64), -1);

        // EditBox 不是 renderable widget，需要自己画
        for (EditBox box : new EditBox[]{this.displayNameField, this.addressField, this.usernameField, this.passwordField}) {
            box.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}
