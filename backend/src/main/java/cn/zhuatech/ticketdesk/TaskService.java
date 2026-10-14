// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import jakarta.persistence.*;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 可重试队列处理；外部失败记录错误代码，不泄露地址凭据或票码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class TaskService {
  final Store db;
  final TicketService tickets;
  final Core core;
  final StripeGateway stripe;
  final GatewayConfig config;
  final Clock clock;
  final ObjectProvider<JavaMailSender> sender;
  final boolean mailEnabled;
  final String from;
  @PersistenceContext EntityManager em;

  public TaskService(
      Store db,
      TicketService tickets,
      Core core,
      StripeGateway stripe,
      GatewayConfig config,
      Clock clock,
      ObjectProvider<JavaMailSender> sender,
      @Value("${ticketdesk.mail-enabled:false}") boolean mailEnabled,
      @Value("${ticketdesk.mail-from:}") String from) {
    this.db = db;
    this.tickets = tickets;
    this.core = core;
    this.stripe = stripe;
    this.config = config;
    this.clock = clock;
    this.sender = sender;
    this.mailEnabled = mailEnabled;
    this.from = from;
  }

  /** 回滚后的新事务保留错误状态，避免异常被吞掉。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void failed(String kind, String id) {
    if (kind.equals("refund")) {
      var t = em.find(RefundTask.class, id, LockModeType.PESSIMISTIC_WRITE);
      if (t != null && !t.state.equals("DONE")) {
        t.state = "ERROR";
        t.error = "TASK_TRANSACTION_FAILED";
        t.nextAttempt = clock.millis() + 60000;
      }
    } else {
      var t = em.find(MailTask.class, id, LockModeType.PESSIMISTIC_WRITE);
      if (t != null && !t.state.equals("SENT")) {
        t.state = "ERROR";
        t.error = "TASK_TRANSACTION_FAILED";
        t.nextAttempt = clock.millis() + 60000;
      }
    }
  }

  /** 每次取有限任务，单条独立提交；异常不会回滚其他任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, List<String>> due() {
    return Map.of(
        "refund",
        db.all(RefundTask.class).stream()
            .filter(
                t ->
                    Set.of("PENDING", "ERROR").contains(t.state) && t.nextAttempt <= clock.millis())
            .limit(20)
            .map(t -> t.id)
            .toList(),
        "mail",
        db.all(MailTask.class).stream()
            .filter(
                t ->
                    Set.of("PENDING", "ERROR", "DISABLED").contains(t.state)
                        && t.nextAttempt <= clock.millis())
            .limit(20)
            .map(t -> t.id)
            .toList());
  }

  /** 有稳定幂等键的全额退款；只有匹配的成功响应可释放座位。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional
  public void refund(String id) {
    var t = em.find(RefundTask.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (t == null
        || !Set.of("PENDING", "ERROR").contains(t.state)
        || t.nextAttempt > clock.millis()) return;
    t.attempts++;
    try {
      String ref;
      if (t.mode.equals("DEMO") || t.mode.equals("FREE")) {
        ref = "local-" + t.id;
      } else {
        var r = stripe.refund(t);
        Rules.check(
            r.path("payment_intent").asText().equals(t.paymentIntent)
                && r.path("amount").isIntegralNumber()
                && r.path("amount").asLong(-1) == t.amount
                && r.path("currency").asText().equalsIgnoreCase(t.currency),
            "REFUND_RESPONSE_INVALID");
        ref = r.path("id").asText();
        Rules.check(ref.matches("re_[a-zA-Z0-9_]+"), "REFUND_RESPONSE_INVALID");
        t.refundId = ref;
        String state = r.path("status").asText();
        if (state.equals("failed") || state.equals("canceled")) {
          t.state = "FAILED";
          t.error = "PROVIDER_REFUND_FAILED";
          return;
        }
        if (!state.equals("succeeded")) {
          t.state = "PENDING";
          t.nextAttempt = clock.millis() + 60000;
          return;
        }
      }
      if (t.releasesSeat)
        core.confirmedRefund(
            t.bookingId, TicketService.stable("refund:" + ref), t.amount, t.currency);
      else
        core.refundExtra(
            t.bookingId,
            TicketService.stable(
                t.mode.equals("STRIPE") ? "stripe:" + t.paymentIntent : t.paymentIntent),
            TicketService.stable("refund:" + ref),
            t.amount,
            t.currency);
      t.state = "DONE";
      t.error = "";
    } catch (Exception e) {
      t.state = "ERROR";
      t.error = e instanceof Problem ? e.getMessage() : "REFUND_PROCESS_ERROR";
      t.nextAttempt = clock.millis() + Math.min(3600000, 60000L * t.attempts);
    }
  }

  /** 只发本人票夹链接；禁用SMTP或投递失败均可见，不冒充已发送。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional
  public void mail(String id) {
    var t = em.find(MailTask.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (t == null || t.state.equals("SENT") || t.nextAttempt > clock.millis()) return;
    if (!mailEnabled || sender.getIfAvailable() == null || from.isBlank()) {
      t.state = "DISABLED";
      t.error = "MAIL_NOT_CONFIGURED";
      t.nextAttempt = clock.millis() + 3600000;
      return;
    }
    t.attempts++;
    try {
      var m = new SimpleMailMessage();
      m.setFrom(from);
      m.setTo(t.recipient);
      m.setSubject("TicketDesk ticket / 活动票夹");
      m.setText(
          "Your order / 您的订单: "
              + t.bookingId
              + "\nSign in to view your ticket / 登录查看票夹: "
              + config.url
              + "/#/wallet?order="
              + t.bookingId);
      sender.getObject().send(m);
      t.state = "SENT";
      t.error = "";
    } catch (Exception e) {
      t.state = "ERROR";
      t.error = "MAIL_DELIVERY_FAILED";
      t.nextAttempt = clock.millis() + Math.min(3600000, 60000L * t.attempts);
    }
  }
}
