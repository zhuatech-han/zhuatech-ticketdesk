// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import jakarta.persistence.*;

/** 票夹链接通知队列；不保存原始票码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "mail_task")
public class MailTask {
  @Id
  @Column(length = 36)
  public String id;

  @Column(nullable = false, length = 36)
  public String bookingId;

  @Column(nullable = false, length = 254)
  public String recipient;

  @Column(nullable = false, length = 20)
  public String state = "PENDING";

  @Column(nullable = false)
  public int attempts;

  @Column(nullable = false)
  public long nextAttempt;

  @Column(nullable = false, length = 40)
  public String error = "";
}
