// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import jakarta.persistence.*;

/** 可重试退款任务，以稳定幂等键绑定原付款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "refund_task")
public class RefundTask {
  @Id
  @Column(length = 36)
  public String id;

  @Column(nullable = false, length = 36)
  public String bookingId;

  @Column(nullable = false, length = 100, unique = true)
  public String paymentIntent;

  @Column(nullable = false)
  public long amount;

  @Column(nullable = false, length = 3)
  public String currency;

  @Column(nullable = false, length = 20)
  public String mode;

  @Column(nullable = false, length = 20)
  public String state = "PENDING";

  @Column(length = 100)
  public String refundId;

  @Column(nullable = false)
  public boolean releasesSeat;

  @Column(nullable = false)
  public int attempts;

  @Column(nullable = false)
  public int generation;

  @Column(nullable = false)
  public long nextAttempt;

  @Column(nullable = false, length = 40)
  public String error = "";
}
