package com.littleskin.switcher;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 入口类（仅客户端）。
 *
 * 功能概述：
 *  - 默认使用启动器登录的正版账户；
 *  - 服务器列表界面左上角提供 "配置 LittleSkin" 按钮；
 *  - 每个服务器条目右下角提供 "使用 LittleSkin" 切换按钮与标记；
 *  - 进入标记为 LittleSkin 的服务器时，自动以 LittleSkin 账户认证并切换会话。
 */
public class LittleSkinSwitcher implements ClientModInitializer {
    public static final String MOD_ID = "littleskin-switcher";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("LittleSkin Switcher loaded.");
    }
}
