package com.littleskin.switcher.auth;

import com.littleskin.switcher.config.Account;

/** 账号 -> 登录方式。新增登录方式时只需要在这里挂上。 */
public final class AuthProviders {
    private static final LauncherAuthProvider LAUNCHER = new LauncherAuthProvider();

    public static AuthProvider forAccount(Account account) {
        if (account == null || account.isLauncher()) {
            return LAUNCHER;
        }
        return new YggdrasilAuthProvider(account.authServer);
    }

    public static LauncherAuthProvider launcher() {
        return LAUNCHER;
    }

    private AuthProviders() {
    }
}
