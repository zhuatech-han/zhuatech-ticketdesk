// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import jakarta.persistence.*;

/** 活动、售票和退票时间与工作人员授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "ticket_event")
public class TicketEvent {
  @Id
  @Column(length = 36)
  public String id;

  @Column(nullable = false)
  public Long departmentId;

  @Column(nullable = false, length = 120)
  public String name;

  @Column(nullable = false, length = 120)
  public String nameEn;

  @Column(nullable = false, length = 120)
  public String venue;

  @Column(nullable = false, length = 120)
  public String venueEn;

  @Column(nullable = false, length = 3000)
  public String description;

  @Column(nullable = false, length = 3000)
  public String descriptionEn;

  @Column(nullable = false, length = 20)
  public String category;

  @Column(nullable = false, length = 3)
  public String currency;

  @Column(nullable = false)
  public long startsAt;

  @Column(nullable = false)
  public long saleStartsAt;

  @Column(nullable = false)
  public long saleEndsAt;

  @Column(nullable = false)
  public long refundEndsAt;

  @Column(nullable = false, length = 20)
  public String state = "DRAFT";

  @Column(nullable = false)
  public long version = 1;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "event_staff", joinColumns = @JoinColumn(name = "event_id"))
  @Column(name = "account_id")
  public java.util.Set<Long> staff = new java.util.HashSet<>();
}
