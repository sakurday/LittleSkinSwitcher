package com.littleskin.switcher.util;

import net.minecraft.network.chat.Component;

public final class Components {
    /** 把可能为 null 的文本包成 Component；null 时回退到异常自身的 toString。 */
    public static Component orLiteral(String text) {
        return Component.literal(text == null || text.isBlank() ? "unknown error" : text);
    }

    public static Component orLiteral(Throwable t) {
        return t == null ? Component.literal("unknown error")
                : orLiteral(t.getMessage() == null ? t.toString() : t.getMessage());
    }

    private Components() {
    }
}
