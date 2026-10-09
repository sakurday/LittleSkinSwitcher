package com.littleskin.switcher.util;

import java.util.Locale;

/**
 * 服务器地址的规范化。
 *
 * 配置里用规范化后的地址当键，玩家的输入则是五花八门的
 * （大小写、结尾的点、写不写 :25565），不统一就会「同一台服务器被记成两条」。
 *
 * 这里刻意不依赖任何 Minecraft / Fabric 类型，方便单独验证。
 */
public final class ServerAddresses {
    public static String normalize(String address) {
        if (address == null) {
            return null;
        }
        String s = address.trim().toLowerCase(Locale.ROOT);
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) {
            return s;
        }
        int colon;
        if (s.startsWith("[")) {
            // IPv6 字面量：[::1]:25565
            int close = s.indexOf(']');
            if (close < 0) {
                return s;
            }
            colon = s.indexOf(':', close);
        } else if (s.indexOf(':') != s.lastIndexOf(':')) {
            // 不带方括号的 IPv6 没有端口部分，原样保留
            return s;
        } else {
            colon = s.indexOf(':');
        }
        if (colon < 0) {
            return s;
        }
        String host = s.substring(0, colon);
        String port = s.substring(colon + 1);
        return "25565".equals(port) ? host : host + ":" + port;
    }

    private ServerAddresses() {
    }
}
