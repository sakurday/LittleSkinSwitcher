package com.littleskin.switcher.util;

import java.util.Locale;
import java.util.UUID;

/**
 * 角色 UUID 的规范化工具。
 *
 * Yggdrasil 各站返回的 UUID 有时带连字符、有时不带、有时大写，
 * 统一规范化为「小写带连字符」形式存储，向 API 发送时再去掉连字符。
 */
public final class Uuids {
    /** 规范化为小写带连字符的形式；无法解析时返回 null。 */
    public static String normalize(String raw) {
        UUID uuid = parse(raw);
        return uuid == null ? null : uuid.toString();
    }

    /** 去掉连字符，用于请求参数；无法解析时返回 null。 */
    public static String undash(String raw) {
        UUID uuid = parse(raw);
        return uuid == null ? null : uuid.toString().replace("-", "");
    }

    public static UUID parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() == 32) {
            StringBuilder sb = new StringBuilder(36);
            sb.append(s, 0, 8).append('-')
                    .append(s, 8, 12).append('-')
                    .append(s, 12, 16).append('-')
                    .append(s, 16, 20).append('-')
                    .append(s, 20, 32);
            s = sb.toString();
        }
        try {
            return UUID.fromString(s.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Uuids() {
    }
}
