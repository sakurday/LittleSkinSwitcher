package com.littleskin.switcher.auth;

import net.minecraft.network.chat.Component;

/**
 * 认证失败。
 *
 * 携带一个 {@link Component} 而不是裸字符串，这样本地错误（可翻译）与
 * 远端站点返回的 errorMessage（原样展示）能用同一条路径冒泡到界面上。
 */
public class AuthException extends Exception {
    private final Component component;

    public AuthException(Component component) {
        super(component.getString());
        this.component = component;
    }

    public AuthException(Component component, Throwable cause) {
        super(component.getString(), cause);
        this.component = component;
    }

    /** 直接展示的远端文本（站点自己返回的中文/英文说明）。 */
    public static AuthException remote(String message) {
        return new AuthException(Component.literal(message));
    }

    /** 本地可翻译的错误。 */
    public static AuthException local(String translationKey, Object... args) {
        return new AuthException(Component.translatable(translationKey, args));
    }

    public Component component() {
        return component;
    }
}
