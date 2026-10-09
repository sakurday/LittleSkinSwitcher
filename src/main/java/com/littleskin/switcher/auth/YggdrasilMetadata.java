package com.littleskin.switcher.auth;

/**
 * Yggdrasil API 根地址的元数据（authlib-injector 规范）。
 *
 * 目前只用来验证玩家填写的地址确实是一个 Yggdrasil 服务，
 * 并给新账户取一个像样的默认名字（站点名）。
 *
 * 皮肤相关字段（signaturePublickey / skinDomains）有意没有处理：
 * 本模组不接管皮肤加载，交给玩家自备的 CustomSkinLoader 之类的模组。
 */
public class YggdrasilMetadata {
    /** meta.serverName，站点自称的名字。 */
    public String serverName = "";
}
