// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import jakarta.persistence.*;

/** 单座位占用指针；所有状态变更先锁此行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "ticket_seat")
class Seat {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 36)
  String eventId;

  @Column(nullable = false, length = 10)
  String label;

  @Column(nullable = false)
  long price;

  @Column(nullable = false, length = 3)
  String currency;

  @Column(length = 36)
  String currentBookingId;
}

/** 单座位订单与冻结价格；身份由可信调用层传入，不接受浏览器自行指定身份。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "ticket_booking")
class Booking {
  @Id
  @Column(length = 36)
  String id;

  @Column(nullable = false)
  Long seatId;

  @Column(nullable = false, length = 64)
  String ownerId;

  @Column(nullable = false)
  long price;

  @Column(nullable = false, length = 3)
  String currency;

  @Column(nullable = false)
  long expiresAt;

  @Column(nullable = false, length = 20)
  String state;
}

/** 每个订单至多一张票，VOID不可再核销。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "ticket_ticket")
class Ticket {
  @Id
  @Column(length = 36)
  String id;

  @Column(nullable = false, length = 36, unique = true)
  String bookingId;

  @Column(nullable = false, length = 20)
  String state;
}

/** 经服务层验证的支付及退款记账记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "ticket_receipt")
class Receipt {
  @Column(nullable = false)
  long createdAt;

  @Id
  @Column(length = 36)
  String id;

  @Column(nullable = false, length = 36)
  String bookingId;

  @Column(nullable = false, length = 20)
  String kind;

  @Column(nullable = false)
  long amount;

  @Column(nullable = false, length = 3)
  String currency;

  @Column(nullable = false, length = 20)
  String result;

  @Column(length = 36)
  String ticketId;
}

/** 核销决策留痕，不保存完整票码或签名密钥。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "ticket_scan")
class Scan {
  @Column(nullable = false)
  long createdAt;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 36)
  String eventId;

  @Column(nullable = false, length = 64)
  String operatorId;

  @Column(length = 36)
  String ticketId;

  @Column(nullable = false, length = 20)
  String decision;
}
