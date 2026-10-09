package com.littleskin.switcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.littleskin.switcher.LittleSkinSwitcher;
import com.littleskin.switcher.util.ServerAddresses;
import com.littleskin.switcher.util.Uuids;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 模组配置，持久化到 <config>/littleskin-switcher.json。
 *
 * accounts:        所有登录身份（含一个不可删除的启动器账户）
 * serverAccounts:  服务器地址 -> 账号 id；没有条目表示使用启动器账户
 *
 * 旧版（v1）的字段在 {@link ConfigMigration} 里升级到 v2。
 */
public class ModConfig {
    public static final int CURRENT_VERSION = 2;
    /** 启动器账户的固定 id，恒存在且不可删除。 */
    public static final String LAUNCHER_ACCOUNT_ID = "launcher";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("littleskin-switcher.json");

    public int version = CURRENT_VERSION;
    public List<Account> accounts = new ArrayList<>();
    public Map<String, String> serverAccounts = new LinkedHashMap<>();

    private static ModConfig INSTANCE;

    public static ModConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(CONFIG_PATH)).getAsJsonObject();
                boolean migrated = ConfigMigration.migrate(root);
                ModConfig cfg = GSON.fromJson(root, ModConfig.class);
                if (cfg != null) {
                    cfg.normalize();
                    if (migrated) {
                        LittleSkinSwitcher.LOGGER.info("[LittleSkinSwitcher] 配置已从 v1 升级到 v{}", CURRENT_VERSION);
                        cfg.save();
                    }
                    return cfg;
                }
            } catch (Exception e) {
                LittleSkinSwitcher.LOGGER.error("[LittleSkinSwitcher] 读取配置失败，将使用默认配置", e);
            }
        }
        ModConfig cfg = new ModConfig();
        cfg.normalize();
        return cfg;
    }

    /** 补齐缺失字段，并保证启动器账户存在。 */
    private void normalize() {
        version = CURRENT_VERSION;
        if (accounts == null) {
            accounts = new ArrayList<>();
        }
        if (serverAccounts == null) {
            serverAccounts = new LinkedHashMap<>();
        }
        accounts.removeIf(a -> a == null);
        for (Account a : accounts) {
            if (a.id == null || a.id.isEmpty()) {
                a.id = newAccountId();
            }
            if (a.type == null || a.type.isEmpty()) {
                a.type = Account.TYPE_YGGDRASIL;
            }
            // 手改过的配置里可能有显式 null，统一补空串，后面就不用到处判空了
            a.displayName = nullToEmpty(a.displayName);
            a.preset = nullToEmpty(a.preset);
            a.authServer = nullToEmpty(a.authServer);
            a.username = nullToEmpty(a.username);
            a.accessToken = nullToEmpty(a.accessToken);
            a.clientToken = nullToEmpty(a.clientToken);
            a.selectedProfile = nullToEmpty(a.selectedProfile);
            if (a.profiles == null) {
                a.profiles = new ArrayList<>();
            }
            a.profiles.removeIf(p -> p == null || Uuids.normalize(p.uuid) == null);
            for (Account.Profile p : a.profiles) {
                p.uuid = Uuids.normalize(p.uuid);
                p.name = nullToEmpty(p.name);
            }
            if (a.clientToken.isEmpty()) {
                a.clientToken = UUID.randomUUID().toString().replace("-", "");
            }
        }
        if (accountByIdInternal(LAUNCHER_ACCOUNT_ID) == null) {
            Account launcher = new Account();
            launcher.id = LAUNCHER_ACCOUNT_ID;
            launcher.type = Account.TYPE_LAUNCHER;
            launcher.preset = Account.TYPE_LAUNCHER;
            accounts.add(0, launcher);
        }
        // 指向已删除账号的服务器映射要清掉，否则会静默落到启动器账户上
        serverAccounts.entrySet().removeIf(e -> accountByIdInternal(e.getValue()) == null);
    }

    /** 认证在后台线程完成，写盘需要互斥，避免和界面里的保存互相覆盖。 */
    public synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            LittleSkinSwitcher.LOGGER.error("[LittleSkinSwitcher] 保存配置失败", e);
        }
    }

    // ---------------------------------------------------------------- 账户

    public List<Account> accounts() {
        return accounts;
    }

    public Account accountById(String id) {
        return id == null ? null : accountByIdInternal(id);
    }

    private Account accountByIdInternal(String id) {
        for (Account a : accounts) {
            if (id.equals(a.id)) {
                return a;
            }
        }
        return null;
    }

    /** 启动器账户，永远非 null。 */
    public Account launcherAccount() {
        Account a = accountByIdInternal(LAUNCHER_ACCOUNT_ID);
        if (a == null) {
            normalize();
            a = accountByIdInternal(LAUNCHER_ACCOUNT_ID);
        }
        return a;
    }

    public Account newYggdrasilAccount() {
        Account a = new Account();
        a.id = newAccountId();
        a.type = Account.TYPE_YGGDRASIL;
        a.clientToken = UUID.randomUUID().toString().replace("-", "");
        return a;
    }

    public void addAccount(Account account) {
        if (accountByIdInternal(account.id) == null) {
            accounts.add(account);
        }
    }

    /** 删除账号；启动器账户不可删除。返回是否真的删除了。 */
    public boolean removeAccount(String id) {
        if (LAUNCHER_ACCOUNT_ID.equals(id)) {
            return false;
        }
        Account a = accountByIdInternal(id);
        if (a == null) {
            return false;
        }
        accounts.remove(a);
        serverAccounts.entrySet().removeIf(e -> id.equals(e.getValue()));
        return true;
    }

    private static String newAccountId() {
        return "acc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------ 服务器映射

    /** 某个服务器要使用的账号，未配置时回落到启动器账户。 */
    public Account accountForServer(String address) {
        String id = serverAccounts.get(ServerAddresses.normalize(address));
        Account a = accountById(id);
        return a != null ? a : launcherAccount();
    }

    /** 该服务器是否有显式配置（无配置即「跟随默认」= 启动器账户）。 */
    public boolean hasServerAccount(String address) {
        return accountById(serverAccounts.get(ServerAddresses.normalize(address))) != null;
    }

    /** 为服务器指定账号；传 null 或启动器账户 id 表示恢复默认。 */
    public void setAccountForServer(String address, String accountId) {
        String key = ServerAddresses.normalize(address);
        if (key == null || key.isEmpty()) {
            return;
        }
        if (accountId == null || LAUNCHER_ACCOUNT_ID.equals(accountId)) {
            serverAccounts.remove(key);
        } else {
            serverAccounts.put(key, accountId);
        }
    }

}
