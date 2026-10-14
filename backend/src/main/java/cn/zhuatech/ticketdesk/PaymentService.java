// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import jakarta.persistence.*;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** 付款入口绑定本人、冻结订单和已建会话；外部事件只在验签后处理。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class PaymentService {
  final TicketService tickets;
  final AccessService access;
  final Core core;
  final Store db;
  final StripeGateway stripe;
  final GatewayConfig config;
  final Clock clock;
  @PersistenceContext EntityManager em;

  public PaymentService(
      TicketService tickets,
      AccessService access,
      Core core,
      Store db,
      StripeGateway stripe,
      GatewayConfig config,
      Clock clock) {
    this.tickets = tickets;
    this.access = access;
    this.core = core;
    this.db = db;
    this.stripe = stripe;
    this.config = config;
    this.clock = clock;
  }

  /** 恢复既有Checkout，免费票直接出票；未配置真实网关只返回明确演示模式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> checkout(String id) {
    access.require("portal");
    var b = tickets.booking(id, true);
    Rules.check(b.ownerId.equals(access.current().id.toString()), "NOT_OWNER");
    Rules.check(b.state.equals("HELD") && clock.millis() < b.expiresAt, "ORDER_EXPIRED");
    var c = em.find(Checkout.class, id);
    if (c == null) {
      c = new Checkout();
      c.bookingId = id;
      c.mode = b.price == 0 ? "FREE" : config.mode;
      c.createdAt = clock.millis();
      db.save(c);
    }
    if (c.mode.equals("FREE")) {
      c.paymentIntent = "free-" + id;
      var result = core.verifiedPayment(id, TicketService.stable(c.paymentIntent), 0, b.currency);
      if (result.status().equals("TICKET")) tickets.queueMail(b);
      return Map.of("mode", "FREE", "orderId", id);
    }
    if (c.mode.equals("DEMO")) {
      Rules.check(config.mode.equals("DEMO"), "PAYMENT_DISABLED");
      return Map.of("mode", "DEMO", "orderId", id);
    }
    Rules.check(config.mode.equals("STRIPE"), "PAYMENT_DISABLED");
    if (c.sessionId == null) {
      var s = stripe.checkout(b, tickets.event(tickets.seat(b.seatId).eventId, false).name);
      Rules.check(
          s.path("client_reference_id").asText().equals(id)
              && s.path("amount_total").isIntegralNumber()
              && s.path("amount_total").asLong() == b.price
              && s.path("currency").asText().equalsIgnoreCase(b.currency)
              && s.path("expires_at").isIntegralNumber()
              && s.path("expires_at").asLong() == b.expiresAt / 1000,
          "PAYMENT_RESPONSE_INVALID");
      String session = s.path("id").asText(), url = s.path("url").asText();
      Rules.check(
          session.matches("cs_[a-zA-Z0-9_]+") && url.length() <= 2000, "PAYMENT_RESPONSE_INVALID");
      var uri = URI.create(url);
      Rules.check(
          uri.getScheme().equals("https")
              && uri.getHost().equals("checkout.stripe.com")
              && uri.getUserInfo() == null,
          "PAYMENT_RESPONSE_INVALID");
      c.sessionId = session;
      c.url = url;
    }
    return Map.of("mode", "STRIPE", "url", c.url, "orderId", id);
  }

  /** 演示确认仅本人DEMO订单，绝不在STRIPE环境开放。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> demoPay(String id) {
    Rules.check(config.mode.equals("DEMO"), "PAYMENT_DISABLED");
    access.require("portal");
    var b = tickets.booking(id, true);
    Rules.check(b.ownerId.equals(access.current().id.toString()), "NOT_OWNER");
    var c = em.find(Checkout.class, id);
    Rules.check(c != null && c.mode.equals("DEMO"), "PAYMENT_DISABLED");
    c.paymentIntent = "demo-" + id;
    var result =
        core.verifiedPayment(id, TicketService.stable(c.paymentIntent), b.price, b.currency);
    if (result.status().equals("TICKET")) tickets.queueMail(b);
    else if (result.status().equals("REFUND_REQUIRED"))
      tickets.createRefund(b, c.paymentIntent, c.mode, false);
    access.audit(
        "DEMO_PAYMENT", id, tickets.event(tickets.seat(b.seatId).eventId, false).departmentId);
    return tickets.ownOrder(id);
  }

  /** 验签原文后匹配会话与冻结金额，以支付身份去重而非通知身份。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Boolean> webhook(byte[] raw, String signature) {
    JsonNode event = stripe.webhook(raw, signature);
    String kind = event.path("type").asText();
    if (!Set.of("checkout.session.completed", "checkout.session.async_payment_succeeded")
        .contains(kind)) return Map.of("ok", true);
    var s = event.path("data").path("object");
    if (!s.path("payment_status").asText().equals("paid")) return Map.of("ok", true);
    Rules.check(
        s.path("livemode").asBoolean() == config.key.startsWith("sk_live_"), "WEBHOOK_INVALID");
    String id = s.path("client_reference_id").asText();
    var b = tickets.booking(id, true);
    var c = em.find(Checkout.class, id);
    Rules.check(
        c != null && c.mode.equals("STRIPE") && Objects.equals(c.sessionId, s.path("id").asText()),
        "WEBHOOK_ORDER_MISMATCH");
    String intent = s.path("payment_intent").asText();
    Rules.check(
        intent.matches("pi_[a-zA-Z0-9_]+")
            && s.path("amount_total").isIntegralNumber()
            && s.path("amount_total").asLong(-1) == b.price
            && s.path("currency").asText().equalsIgnoreCase(b.currency),
        "PAYMENT_MISMATCH");
    if (c.paymentIntent == null) c.paymentIntent = intent;
    var result =
        core.verifiedPayment(id, TicketService.stable("stripe:" + intent), b.price, b.currency);
    if (result.status().equals("TICKET")) tickets.queueMail(b);
    else if (result.status().equals("REFUND_REQUIRED"))
      tickets.createRefund(b, intent, "STRIPE", false);
    return Map.of("ok", true);
  }
}
