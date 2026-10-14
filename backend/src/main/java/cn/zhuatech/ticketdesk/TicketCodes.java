// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 票身份HMAC，不是Stripe回调验签；仅支持服务器在线核销。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class TicketCodes {
  private final byte[] key;

  TicketCodes(byte[] key) {
    if (key.length < 32) throw new IllegalArgumentException("Signing key requires 256 bits");
    this.key = key.clone();
  }

  /** 同一票身份重读相同载荷；客户恢复票夹不制造新凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String encode(String id) {
    UUID.fromString(id);
    String body = "td1." + id;
    return body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(body));
  }

  /** 常量时间比较签名；伪造、非规范UUID及错误格式返回空。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Optional<String> decode(String code) {
    if (code == null || code.length() > 128) return Optional.empty();
    try {
      String[] parts = code.split("\\.", -1);
      if (parts.length != 3
          || !parts[0].equals("td1")
          || !UUID.fromString(parts[1]).toString().equals(parts[1])) return Optional.empty();
      byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
      return MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), actual)
          ? Optional.of(parts[1])
          : Optional.empty();
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private byte[] sign(String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(body.getBytes(StandardCharsets.US_ASCII));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("HMAC unavailable", e);
    }
  }
}
