// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import static org.junit.jupiter.api.Assertions.*;

import java.security.SecureRandom;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 票码格式、篡改和密钥隔离验证，非支付网关测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class TicketCodesTest {
  private TicketCodes codes() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return new TicketCodes(key);
  }

  @Test
  void roundTripIsStable() {
    var c = codes();
    String id = UUID.randomUUID().toString();
    assertEquals(Optional.of(id), c.decode(c.encode(id)));
    assertEquals(c.encode(id), c.encode(id));
  }

  @Test
  void alteredIdentityAndDifferentKeyAreRejected() {
    var a = codes();
    String id = UUID.randomUUID().toString(), code = a.encode(id);
    assertTrue(a.decode(code.replace(id, UUID.randomUUID().toString())).isEmpty());
    assertTrue(codes().decode(code).isEmpty());
  }

  @Test
  void malformedAndWeakKeyAreRejected() {
    var c = codes();
    for (String v : Arrays.asList(null, "", "td1.bad.bad", "x".repeat(200), "td1.1.bad"))
      assertTrue(c.decode(v).isEmpty());
    assertThrows(IllegalArgumentException.class, () -> new TicketCodes(new byte[8]));
  }
}
