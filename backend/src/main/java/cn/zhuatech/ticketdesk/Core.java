// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import jakarta.persistence.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 单座位预留、出票、核销和退票事务内核；由鉴权服务调用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class Core {
  @PersistenceContext EntityManager em;
  private final Clock clock;
  private final TicketCodes codes;

  public Core(Clock clock, TicketCodes codes) {
    this.clock = clock;
    this.codes = codes;
  }

  /** 建立一类真实座位；活动服务控制权限和配置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<Long> createSeats(
      String eventId, int count, long price, String currency, String prefix) {
    uuid(eventId);
    require(count >= 1 && count <= 500 && price >= 0 && price <= 100000000, "INVALID_INPUT");
    require(Set.of("CNY", "USD", "EUR", "GBP", "HKD").contains(currency), "INVALID_INPUT");
    require(prefix != null && prefix.length() <= 5, "INVALID_INPUT");
    List<Long> ids = new ArrayList<>();
    for (int n = 1; n <= count; n++) {
      Seat seat = new Seat();
      seat.eventId = eventId;
      seat.label = prefix + n;
      seat.price = price;
      seat.currency = currency;
      em.persist(seat);
      ids.add(seat.id);
    }
    return ids;
  }

  /** 服务器限时预留；相同请求绑定本人和座位，不能复用改订单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String reserve(String owner, long seatId, String requestId) {
    identity(owner);
    uuid(requestId);
    Seat seat = lockSeat(seatId);
    Booking replay = em.find(Booking.class, requestId);
    if (replay != null) {
      require(replay.ownerId.equals(owner) && replay.seatId == seatId, "REQUEST_CONFLICT");
      return replay.id;
    }
    if (seat.currentBookingId != null) {
      Booking old = em.find(Booking.class, seat.currentBookingId);
      if (old.state.equals("HELD") && old.expiresAt <= clock.millis()) {
        old.state = "EXPIRED";
        seat.currentBookingId = null;
      } else throw new Refusal("SEAT_UNAVAILABLE");
    }
    Booking b = new Booking();
    b.id = requestId;
    b.seatId = seatId;
    b.ownerId = owner;
    b.price = seat.price;
    b.currency = seat.currency;
    b.expiresAt = Math.addExact(clock.millis(), 35 * 60 * 1000L);
    b.state = "HELD";
    em.persist(b);
    seat.currentBookingId = b.id;
    return b.id;
  }

  /** 本人关闭未付款预留；迟到收款仍走待退款，不恢复座位。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void cancelHold(String owner, String id) {
    var b = lockBooking(id);
    require(b.ownerId.equals(owner), "NOT_OWNER");
    if (b.state.equals("EXPIRED")) return;
    require(b.state.equals("HELD"), "STATE_INVALID");
    b.state = "EXPIRED";
    var seat = em.find(Seat.class, b.seatId);
    if (b.id.equals(seat.currentBookingId)) seat.currentBookingId = null;
  }

  /** 有界清理到期预留，不影响后来持有人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void expire() {
    for (String id :
        em.createQuery(
                "select b.id from Booking b where b.state='HELD' and b.expiresAt<=:now",
                String.class)
            .setParameter("now", clock.millis())
            .setMaxResults(100)
            .getResultList()) {
      var b = lockBooking(id);
      if (b.state.equals("HELD") && b.expiresAt <= clock.millis()) {
        b.state = "EXPIRED";
        var s = em.find(Seat.class, b.seatId);
        if (b.id.equals(s.currentBookingId)) s.currentBookingId = null;
      }
    }
  }

  /** 可信服务层核实付款后出票；不能凭前端跳转调用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public PaymentResult verifiedPayment(
      String bookingId, String receiptId, long amount, String currency) {
    uuid(receiptId);
    Booking b = lockBooking(bookingId);
    require(amount == b.price && b.currency.equals(currency), "PAYMENT_MISMATCH");
    Receipt replay = em.find(Receipt.class, receiptId);
    if (replay != null) {
      require(
          replay.bookingId.equals(b.id)
              && replay.kind.equals("PAYMENT")
              && replay.amount == amount
              && replay.currency.equals(currency),
          "RECEIPT_CONFLICT");
      return result(replay);
    }
    Seat seat = em.find(Seat.class, b.seatId);
    Receipt receipt = new Receipt();
    receipt.createdAt = clock.millis();
    receipt.id = receiptId;
    receipt.bookingId = b.id;
    receipt.kind = "PAYMENT";
    receipt.amount = amount;
    receipt.currency = currency;
    if (!b.state.equals("HELD")
        || b.expiresAt <= clock.millis()
        || !b.id.equals(seat.currentBookingId)) {
      if (b.state.equals("HELD")) {
        b.state = "EXPIRED";
        if (b.id.equals(seat.currentBookingId)) seat.currentBookingId = null;
      }
      receipt.result = "REFUND_REQUIRED";
      em.persist(receipt);
      return result(receipt);
    }
    Ticket ticket = new Ticket();
    ticket.id = UUID.randomUUID().toString();
    ticket.bookingId = b.id;
    ticket.state = "ISSUED";
    em.persist(ticket);
    b.state = "PAID";
    receipt.result = "TICKET";
    receipt.ticketId = ticket.id;
    em.persist(receipt);
    return result(receipt);
  }

  /** 在线核销先验票码，再锁座位读取最新票状态；只允许一次成功。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String checkIn(String eventId, String operator, String code) {
    uuid(eventId);
    identity(operator);
    String id = codes.decode(code).orElse(null);
    Ticket ticket = id == null ? null : em.find(Ticket.class, id);
    String decision = "INVALID_CODE";
    if (ticket != null) {
      Booking b = lockBooking(ticket.bookingId);
      em.refresh(ticket);
      Seat seat = em.find(Seat.class, b.seatId);
      if (!seat.eventId.equals(eventId)) decision = "WRONG_EVENT";
      else if (!b.state.equals("PAID") || ticket.state.equals("VOID")) decision = "VOID";
      else if (ticket.state.equals("CHECKED_IN")) decision = "ALREADY_USED";
      else {
        ticket.state = "CHECKED_IN";
        decision = "ADMITTED";
      }
    }
    Scan s = new Scan();
    s.createdAt = clock.millis();
    s.eventId = eventId;
    s.operatorId = operator;
    s.ticketId = ticket == null ? null : ticket.id;
    s.decision = decision;
    em.persist(s);
    return decision;
  }

  /** 本人未核销票申请退票立即作废；到账未确认前座位不释放。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void requestRefund(String owner, String bookingId) {
    Booking b = lockBooking(bookingId);
    require(b.ownerId.equals(owner), "NOT_OWNER");
    if (b.state.equals("REFUND_PENDING") || b.state.equals("REFUNDED")) return;
    require(b.state.equals("PAID"), "REFUND_NOT_ALLOWED");
    Ticket ticket = ticketOf(b.id);
    require(ticket.state.equals("ISSUED"), "REFUND_NOT_ALLOWED");
    ticket.state = "VOID";
    b.state = "REFUND_PENDING";
  }

  /** 全额退款确认，同一事务释放原订单占用；重复回执不得二次记账。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void confirmedRefund(String bookingId, String receiptId, long amount, String currency) {
    uuid(receiptId);
    Booking b = lockBooking(bookingId);
    require(amount == b.price && b.currency.equals(currency), "REFUND_MISMATCH");
    Receipt replay = em.find(Receipt.class, receiptId);
    if (replay != null) {
      require(
          replay.kind.equals("REFUND")
              && replay.bookingId.equals(b.id)
              && replay.amount == amount
              && replay.currency.equals(currency),
          "RECEIPT_CONFLICT");
      return;
    }
    require(b.state.equals("REFUND_PENDING"), "REFUND_NOT_ALLOWED");
    Ticket t = ticketOf(b.id);
    require(t.state.equals("VOID"), "REFUND_NOT_ALLOWED");
    b.state = "REFUNDED";
    Seat seat = em.find(Seat.class, b.seatId);
    if (b.id.equals(seat.currentBookingId)) seat.currentBookingId = null;
    Receipt r = new Receipt();
    r.createdAt = clock.millis();
    r.id = receiptId;
    r.bookingId = b.id;
    r.kind = "REFUND";
    r.amount = amount;
    r.currency = currency;
    r.result = "CONFIRMED";
    r.ticketId = t.id;
    em.persist(r);
  }

  /** 迟到或重复收款的退款只改原资金记录，不作废另一张有效票。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void refundExtra(
      String bookingId, String paymentId, String refundId, long amount, String currency) {
    var b = lockBooking(bookingId);
    require(amount == b.price && b.currency.equals(currency), "REFUND_MISMATCH");
    var payment = em.find(Receipt.class, paymentId);
    require(
        payment != null && payment.bookingId.equals(b.id) && payment.kind.equals("PAYMENT"),
        "RECEIPT_CONFLICT");
    var replay = em.find(Receipt.class, refundId);
    if (replay != null) {
      require(
          replay.kind.equals("REFUND")
              && replay.bookingId.equals(b.id)
              && replay.amount == amount
              && replay.currency.equals(currency),
          "RECEIPT_CONFLICT");
      return;
    }
    require(payment.result.equals("REFUND_REQUIRED"), "REFUND_NOT_ALLOWED");
    var r = new Receipt();
    r.createdAt = clock.millis();
    r.id = refundId;
    r.bookingId = b.id;
    r.kind = "REFUND";
    r.amount = amount;
    r.currency = currency;
    r.result = "CONFIRMED";
    em.persist(r);
    payment.result = "REFUNDED_EXTRA";
  }

  /** 读取业务快照，不返回密钥或完整票码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Snapshot snapshot(String eventId) {
    long occupied =
        em.createQuery(
                "select count(s) from Seat s where s.eventId=:event and s.currentBookingId is not null",
                Long.class)
            .setParameter("event", eventId)
            .getSingleResult();
    long tickets =
        em.createQuery(
                "select count(t) from Ticket t,Booking b,Seat s where t.bookingId=b.id and b.seatId=s.id and s.eventId=:event",
                Long.class)
            .setParameter("event", eventId)
            .getSingleResult();
    long admitted =
        em.createQuery(
                "select count(x) from Scan x where x.eventId=:event and x.decision='ADMITTED'",
                Long.class)
            .setParameter("event", eventId)
            .getSingleResult();
    long receipts =
        em.createQuery(
                "select count(r) from Receipt r,Booking b,Seat s where r.bookingId=b.id and b.seatId=s.id and s.eventId=:event",
                Long.class)
            .setParameter("event", eventId)
            .getSingleResult();
    return new Snapshot(occupied, tickets, admitted, receipts);
  }

  /** 出票结果不等于支付网关响应；票已退时重放不能恢复有效票。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record PaymentResult(String status, String ticketId, String code) {}

  /** 真实数据库统计的实验快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Snapshot(long occupied, long tickets, long admissions, long receipts) {}

  private PaymentResult result(Receipt r) {
    if (r.ticketId == null) return new PaymentResult(r.result, null, null);
    Ticket t = em.find(Ticket.class, r.ticketId);
    return new PaymentResult(t.state.equals("VOID") ? "VOID" : r.result, t.id, codes.encode(t.id));
  }

  private Seat lockSeat(long id) {
    Seat s = em.find(Seat.class, id, LockModeType.PESSIMISTIC_WRITE);
    require(s != null, "SEAT_NOT_FOUND");
    return s;
  }

  private Booking lockBooking(String id) {
    uuid(id);
    Booking b = em.find(Booking.class, id);
    require(b != null, "BOOKING_NOT_FOUND");
    lockSeat(b.seatId);
    em.refresh(b);
    return b;
  }

  private Ticket ticketOf(String bookingId) {
    return em.createQuery("from Ticket where bookingId=:booking", Ticket.class)
        .setParameter("booking", bookingId)
        .getSingleResult();
  }

  private static void uuid(String value) {
    try {
      require(value != null && UUID.fromString(value).toString().equals(value), "BAD_ID");
    } catch (IllegalArgumentException e) {
      throw new Refusal("BAD_ID");
    }
  }

  private static void identity(String value) {
    require(value != null && !value.isBlank() && value.length() <= 64, "IDENTITY_REQUIRED");
  }

  private static void require(boolean valid, String code) {
    if (!valid) throw new Refusal(code);
  }
}
