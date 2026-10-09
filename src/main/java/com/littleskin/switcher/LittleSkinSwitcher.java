package com.littleskin.switcher;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 入口类（仅客户端）。
 *
 * 功能概述：
 *  - 可以保存多个登录身份，每个身份要么是「启动器登录」，
 *    要么是任意 Yggdrasil 服务（LittleSkin 只是默认填充的一个地址）；
 *  - 服务器列表左上角提供「账户」按钮管理这些身份；
 *  - 每个服务器条目右下角可以选择该服务器用哪个身份进入，
 *    未配置时使用启动器账户（单人游戏同理）。
 *
 * 皮肤不归本模组管：请配合 CustomSkinLoader 之类的模组使用。
 */
public class LittleSkinSwitcher implements ClientModInitializer {
    public static final String MOD_ID = "littleskin-switcher";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 模组版本，取自 fabric.mod.json（即 gradle.properties 里的 mod_version）。
     * 不要在任何地方再写字面量版本号，否则改了 mod_version 之后会悄悄对不上。
     */
    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("LittleSkin Switcher {} loaded.", version());
    }
}
