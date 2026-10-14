// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库仅初始化管理员与配置，不插入虚构活动和购票人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  public static final Set<String> CODES =
      Set.of(
          "events",
          "sales",
          "refunds",
          "checkin",
          "reports",
          "users",
          "roles",
          "settings",
          "audit",
          "portal");
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${ticketdesk.admin-username}") String username,
      @Value("${ticketdesk.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  /** 建立可管理角色和最小权限目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    Rules.check(username.matches("[a-z0-9_.-]{3,60}"), "INVALID_INPUT");
    var d = new Department();
    d.name = "主办组织";
    d.zone = "Asia/Shanghai";
    db.save(d);
    String[][] entries = {
      {"events", "活动与座位", "Events & seats"},
      {"sales", "订单", "Orders"},
      {"refunds", "退款与通知", "Refunds & notifications"},
      {"checkin", "检票", "Check-in"},
      {"reports", "报表", "Reports"},
      {"users", "登录账号", "Accounts"},
      {"roles", "角色权限", "Roles"},
      {"settings", "组织与设置", "Organization & settings"},
      {"audit", "操作审计", "Audit"},
      {"portal", "我的票夹", "My tickets"}
    };
    for (int i = 0; i < entries.length; i++) {
      var p = new Permission();
      p.code = entries[i][0];
      p.name = entries[i][1];
      db.save(p);
      var m = new NavMenu();
      m.code = p.code;
      m.name = p.name;
      m.nameEn = entries[i][2];
      m.permissionCode = p.code;
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    role("管理员", "ALL", CODES);
    role("活动主办", "DEPARTMENT", Set.of("events", "sales", "refunds", "reports", "audit"));
    role("检票员", "DEPARTMENT", Set.of("checkin"));
    role("观众", "CUSTOMER", Set.of("portal"));
    var a = new Account();
    a.username = username;
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.departmentId = d.id;
    a.roleId = db.all(AccessRole.class).getFirst().id;
    db.save(a);
    for (var kv : Map.of("signupEnabled", "1", "eventSeatLimit", "500").entrySet()) {
      var s = new SystemSetting();
      s.code = kv.getKey();
      s.value = kv.getValue();
      db.save(s);
    }
    for (String[] x :
        new String[][] {
          {"TALK", "讲座", "Talk"},
          {"SHOW", "演出", "Show"},
          {"EXHIBITION", "展览", "Exhibition"},
          {"SPORT", "赛事", "Sport"}
        }) {
      var c = new DictionaryEntry();
      c.type = "CATEGORY";
      c.code = x[0];
      c.name = x[1];
      c.nameEn = x[2];
      c.enabled = true;
      db.save(c);
    }
  }

  private void role(String name, String scope, Set<String> codes) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions.addAll(codes);
    db.save(r);
  }
}
