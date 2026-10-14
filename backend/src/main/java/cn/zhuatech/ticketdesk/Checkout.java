// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import jakarta.persistence.*;

/** 冻结支付会话及归属；付款回调只接受已绑定会话。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "ticket_checkout")
public class Checkout {
  @Id
  @Column(length = 36)
  public String bookingId;

  @Column(length = 100, unique = true)
  public String sessionId;

  @Column(length = 100)
  public String paymentIntent;

  @Column(length = 2000)
  public String url;

  @Column(nullable = false, length = 20)
  public String mode;

  @Column(nullable = false)
  public long createdAt;
}
