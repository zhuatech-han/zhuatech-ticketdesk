// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import jakarta.persistence.*;

/** 每类票的价格和座位布局，售票后锁定身份价格。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "ticket_type")
public class TicketType {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 36)
  public String eventId;

  @Column(nullable = false, length = 60)
  public String name;

  @Column(nullable = false, length = 60)
  public String nameEn;

  @Column(nullable = false, length = 5)
  public String prefix;

  @Column(nullable = false)
  public long price;

  @Column(nullable = false)
  public int capacity;

  @Column(nullable = false)
  public int columns;

  @Column(nullable = false)
  public long version = 1;
}
