package com.littleskin.switcher.auth;

import com.littleskin.switcher.config.Account;

import java.util.ArrayList;
import java.util.List;

/** authenticate / refresh 的结果。 */
public class AuthResult {
    public String accessToken = "";
    public String clientToken = "";
    /** 站点返回的可用角色；refresh 响应里通常为空，此时保留账号里原有的列表。 */
    public List<Account.Profile> profiles = new ArrayList<>();
    /** 站点在响应里点名的角色 UUID（规范化后）；为空表示站点没指定。 */
    public String selectedProfileId = "";
}
