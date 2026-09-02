# LittleSkinSwitcher

![](./image1.png)

Littleskin and Microsoft Multilogin in Minecraft

在Minecraft实现微软账户和LittleSkin的混合登录

当前支持26.1.2版本，使用Fabric加载器。

# 使用方法

你需要在启动器内使用正版登录。

当你安装此模组后，在多人游戏界面左上角会出现配置LittleSkin的按钮。点击它，输入你的LittleSkin账号和密码，点击登录即可。

然后，你可以自定义每个服务器该使用正版进入还是使用LittleSkin进入。每个服务器的右下角都将出现一个按钮，点击即可切换登录方式。

单人游戏默认全部使用正版登录。

# 多语言

界面使用 Minecraft 标准多语言机制：文案全部为 `Component.translatable` 翻译键，语言文件位于
`assets/littleskin-switcher/lang/`，在游戏内「选项 → 语言…」中切换即可（香港繁体、文言语种已由本模组注册进语言列表）。

| 语言文件 | 语言 |
| --- | --- |
| `en_us.json` | English（默认，也是缺失翻译键的回退） |
| `zh_cn.json` | 简体中文 |
| `zh_tw.json` | 繁體中文（台灣） |
| `zh_hk.json` | 繁體中文（香港） |
| `lzh.json` | 文言 |

# 依赖

需要安装 **Fabric API**（用于把模组的语言文件等资源挂载进游戏）。

# 注意事项

您的LittleSkin密码明文保存在版本文件夹里，所以请务必不要把文件泄露给他人。

# AI生成内容声明

本模组绝大部分内容由Claude Code使用Deepseek模型生成
