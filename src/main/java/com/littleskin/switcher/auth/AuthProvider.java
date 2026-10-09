package com.littleskin.switcher.auth;

import com.littleskin.switcher.config.Account;

import java.util.UUID;

/**
 * 一种登录方式。
 *
 * 目前有两个实现：
 *  - {@link LauncherAuthProvider}：启动器登录（正版 / 启动器已注入的 authlib-injector），
 *    模组不介入认证，只把请求原样转发；
 *  - {@link YggdrasilAuthProvider}：任意 Yggdrasil 皮肤站，地址由用户填写。
 *
 * 新增一种登录方式只需实现本接口，无需改动会话切换与握手的代码。
 */
public interface AuthProvider {
    /** 账号 type 字段的取值，用于存档与界面展示。 */
    String typeId();

    /** 该方式是否需要联网才能切换（启动器方式不需要，因此切换是瞬时的）。 */
    default boolean requiresNetwork() {
        return true;
    }

    /**
     * 用账号密码登录，返回可用会话。不改动 account 本身。
     * 可能在后台线程调用。
     */
    AuthResult login(Account account, String password) throws AuthException;

    /**
     * 用已有的 accessToken 续期。失败意味着必须重新输入密码。
     * 可能在后台线程调用。
     */
    AuthResult refresh(Account account) throws AuthException;

    /**
     * 向该登录方式的会话服务器声明「我要加入 serverId 这个服务器」。
     * 这是服务器校验玩家身份的关键一步，在握手线程上调用。
     */
    void joinServer(Account account, UUID profileId, String accessToken, String serverId) throws AuthException;
}
