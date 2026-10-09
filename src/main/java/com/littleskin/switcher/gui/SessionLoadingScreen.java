package com.littleskin.switcher.gui;

import com.littleskin.switcher.LittleSkinSwitcher;
import com.littleskin.switcher.SessionManager;
import com.littleskin.switcher.auth.AuthException;
import com.littleskin.switcher.config.Account;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * 进入服务器前的等待界面。
 *
 * 刷新 token 是一次网络请求，放在渲染线程上会直接卡住界面（旧版就是这么做的），
 * 所以这里先切到这个界面，在后台线程完成认证，成功后自动继续连接。
 */
public class SessionLoadingScreen extends Screen {
    private static final int WIDTH = 300;

    private final JoinMultiplayerScreen parent;
    private final ServerData data;
    private final Account account;

    private boolean started;
    private boolean failed;
    private Component detail = Component.empty();
    private Button configureButton;

    public SessionLoadingScreen(JoinMultiplayerScreen parent, ServerData data, Account account) {
        super(Component.translatable("littleskin-switcher.loading.title"));
        this.parent = parent;
        this.data = data;
        this.account = account;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - WIDTH / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("littleskin-switcher.loading.cancel"),
                        button -> this.onClose())
                .bounds(left, this.height - 28, WIDTH, 20)
                .build());
        this.configureButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("littleskin-switcher.loading.configure"),
                        button -> this.minecraft.setScreen(new AccountEditScreen(this, this.account, false)))
                .bounds(left, this.height - 52, WIDTH, 20)
                .build());
        this.configureButton.visible = this.failed;
        this.configureButton.active = this.failed;

        if (!this.started) {
            this.started = true;
            this.start();
        } else if (this.failed && SessionManager.isReady(this.account)) {
            // 用户刚在「重新登录该账户」里成功登录过，直接继续连接
            this.failed = false;
            this.detail = Component.empty();
            this.succeed();
        }
    }

    private void start() {
        Util.ioPool().execute(() -> {
            try {
                SessionManager.prepare(this.account);
                Minecraft.getInstance().execute(this::succeed);
            } catch (AuthException e) {
                LittleSkinSwitcher.LOGGER.warn("[LittleSkinSwitcher] 进入服务器前认证失败：{}", e.getMessage());
                Minecraft.getInstance().execute(() -> this.fail(e.component()));
            } catch (Exception e) {
                LittleSkinSwitcher.LOGGER.error("[LittleSkinSwitcher] 进入服务器前认证异常", e);
                Minecraft.getInstance().execute(() -> this.fail(Component.literal(String.valueOf(e.getMessage()))));
            }
        });
    }

    private void succeed() {
        try {
            SessionManager.apply(this.account);
        } catch (AuthException e) {
            this.fail(e.component());
            return;
        }
        this.parent.join(this.data);
    }

    private void fail(Component message) {
        if (this.failed) {
            return;
        }
        this.failed = true;
        this.detail = message;
        if (this.configureButton != null) {
            this.configureButton.visible = true;
            this.configureButton.active = true;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int center = this.width / 2;
        graphics.centeredText(this.font, this.title, center, this.height / 2 - 40, -1);
        graphics.centeredText(this.font, Component.translatable("littleskin-switcher.loading.account",
                this.account.label()), center, this.height / 2 - 22, 0xFFFFD080);
        if (this.failed) {
            graphics.centeredText(this.font, Component.translatable("littleskin-switcher.loading.failed"),
                    center, this.height / 2 + 2, 0xFFFF6060);
            graphics.centeredText(this.font, this.detail, center, this.height / 2 + 16, 0xFFE0E0E0);
        } else {
            graphics.centeredText(this.font, Component.translatable("littleskin-switcher.loading.working"),
                    center, this.height / 2 + 2, 0xFFA0A0A0);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
