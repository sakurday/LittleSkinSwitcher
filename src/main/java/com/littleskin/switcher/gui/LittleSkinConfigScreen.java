package com.littleskin.switcher.gui;

import com.littleskin.switcher.auth.LittleSkinAuth;
import com.littleskin.switcher.auth.LittleSkinAuth.AuthResult;
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
 * LittleSkin 配置界面：输入账号密码并登录，状态显示在下方。
 * 登录成功后凭证保存在配置文件中，进入 LittleSkin 服务器时自动使用。
 */
public class LittleSkinConfigScreen extends Screen {
    private final Screen lastScreen;
    private EditBox emailField;
    private EditBox passwordField;
    private Button loginButton;
    private Component status = Component.empty();
    private boolean loggingIn = false;

    public LittleSkinConfigScreen(Screen lastScreen) {
        super(Component.literal("LittleSkin 配置"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.emailField = new EditBox(this.font, cx - 100, 76, 200, 20, Component.literal("LittleSkin 账号（邮箱）"));
        this.emailField.setValue(ModConfig.get().account.email);
        this.emailField.setMaxLength(128);
        this.addWidget(this.emailField);

        this.passwordField = new EditBox(this.font, cx - 100, 106, 200, 20, Component.literal("密码"));
        this.passwordField.setValue(ModConfig.get().account.password);
        this.passwordField.setMaxLength(128);
        this.addWidget(this.passwordField);

        this.loginButton = this.addRenderableWidget(
                Button.builder(Component.literal("登录 / 保存"), button -> this.tryLogin())
                        .bounds(cx - 100, 136, 200, 20)
                        .build());
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_BACK, button -> this.onClose())
                        .bounds(cx - 100, 166, 200, 20)
                        .build());

        ModConfig cfg = ModConfig.get();
        if (!cfg.account.profileName.isEmpty() && cfg.account.valid) {
            this.status = Component.literal("已登录 LittleSkin：" + cfg.account.profileName);
        }
        this.setInitialFocus(this.emailField);
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.emailField);
    }

    @Override
    public void resize(int width, int height) {
        String email = this.emailField.getValue();
        String password = this.passwordField.getValue();
        this.init(width, height);
        this.emailField.setValue(email);
        this.passwordField.setValue(password);
    }

    private void tryLogin() {
        if (this.loggingIn) {
            return;
        }
        String email = this.emailField.getValue();
        String password = this.passwordField.getValue();
        if (email.isEmpty() || password.isEmpty()) {
            this.status = Component.literal("请输入账号和密码");
            return;
        }
        this.loggingIn = true;
        this.status = Component.literal("正在登录 LittleSkin...");
        this.loginButton.active = false;

        Util.ioPool().execute(() -> {
            try {
                AuthResult result = LittleSkinAuth.authenticate(email, password);
                ModConfig cfg = ModConfig.get();
                cfg.account.email = email;
                cfg.account.password = password;
                cfg.account.accessToken = result.accessToken;
                if (result.clientToken != null && !result.clientToken.isEmpty()) {
                    cfg.account.clientToken = result.clientToken;
                }
                cfg.account.profileUuid = result.profileId;
                cfg.account.profileName = result.profileName;
                cfg.account.valid = true;
                cfg.save();
                String profileName = result.profileName;
                Minecraft.getInstance().execute(() -> {
                    this.status = Component.literal("登录成功：" + profileName);
                    this.loggingIn = false;
                    this.loginButton.active = true;
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                Minecraft.getInstance().execute(() -> {
                    this.status = Component.literal("登录失败：" + message);
                    this.loggingIn = false;
                    this.loginButton.active = true;
                });
            }
        });
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && !this.loggingIn) {
            this.tryLogin();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // 注意：不要在这里调用 extractBackground —— 框架的 extractRenderStateWithTooltipAndSubtitles
        // 已在调用本方法前处理过背景模糊，重复调用会触发 "Can only blur once per frame"。
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int cx = this.width / 2;
        graphics.centeredText(this.font, this.title, cx, 20, -1);
        graphics.text(this.font, Component.literal("LittleSkin 账号（邮箱）"), cx - 100 + 1, 62, -6250336);
        graphics.text(this.font, Component.literal("密码"), cx - 100 + 1, 92, -6250336);
        graphics.text(this.font, this.status, cx - 100 + 1, 196, -1);
        this.emailField.extractRenderState(graphics, mouseX, mouseY, a);
        this.passwordField.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}
