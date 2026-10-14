// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 活动、本人票夹及后台API；权限和数据归属由事务服务校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final TicketService tickets;
  final AdminService admin;
  final PaymentService payments;
  final GatewayConfig config;

  public ApiController(
      TicketService tickets, AdminService admin, PaymentService payments, GatewayConfig config) {
    this.tickets = tickets;
    this.admin = admin;
    this.payments = payments;
    this.config = config;
  }

  /** 匿名活动列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/public/events")
  public Object events() {
    return tickets.publicEvents();
  }

  /** 匿名活动座位，不含买方身份。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/public/events/{id}")
  public Object event(@PathVariable String id) {
    return tickets.publicEvent(id);
  }

  /** 非敏感环境标识，告知是否模拟收费。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/public/config")
  public Object configuration() {
    return Map.of("paymentMode", config.mode, "version", "1.0.0", "license", "NON_COMMERCIAL");
  }

  /** 岗位活动目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog")
  public Object catalog() {
    return tickets.catalog();
  }

  /** 活动新增。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/events")
  public Object create(@RequestBody Map<String, Object> b) {
    return tickets.saveEvent(null, b);
  }

  /** 活动更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/events/{id}")
  public Object save(@PathVariable String id, @RequestBody Map<String, Object> b) {
    return tickets.saveEvent(id, b);
  }

  /** 删除未售草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/events/{id}")
  public Object delete(@PathVariable String id) {
    tickets.deleteEvent(id);
    return Map.of("ok", true);
  }

  /** 新票种及座位生成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/events/{id}/types")
  public Object type(@PathVariable String id, @RequestBody Map<String, Object> b) {
    tickets.saveType(id, null, b);
    return Map.of("ok", true);
  }

  /** 更新草稿票种。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/events/{id}/types/{typeId}")
  public Object typeSave(
      @PathVariable String id, @PathVariable Long typeId, @RequestBody Map<String, Object> b) {
    tickets.saveType(id, typeId, b);
    return Map.of("ok", true);
  }

  /** 删除草稿票种。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/events/{id}/types/{typeId}")
  public Object typeDelete(@PathVariable String id, @PathVariable Long typeId) {
    tickets.deleteType(id, typeId);
    return Map.of("ok", true);
  }

  /** 本人预留一座，请求ID恢复相同订单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/events/{id}/reserve")
  public Object reserve(@PathVariable String id, @RequestBody Map<String, Object> b) {
    return tickets.reserve(id, Rules.id(b.get("seatId")), Rules.text(b.get("requestId"), 36, true));
  }

  /** 本人订单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/wallet")
  public Object wallet() {
    return tickets.mine();
  }

  /** 本人订单恢复。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/wallet/{id}")
  public Object order(@PathVariable String id) {
    return tickets.ownOrder(id);
  }

  /** 关闭未付款预留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/wallet/{id}/cancel")
  public Object cancel(@PathVariable String id) {
    tickets.cancel(id);
    return Map.of("ok", true);
  }

  /** 本人Checkout。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/wallet/{id}/checkout")
  public Object checkout(@PathVariable String id) {
    return payments.checkout(id);
  }

  /** 仅DEMO环境模拟付款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/wallet/{id}/demo-pay")
  public Object demo(@PathVariable String id) {
    return payments.demoPay(id);
  }

  /** 本人退票申请。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/wallet/{id}/refund")
  public Object refund(@PathVariable String id) {
    tickets.refund(id, false);
    return Map.of("ok", true);
  }

  /** 主办订单和任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/events/{id}/operations")
  public Object operations(@PathVariable String id) {
    return tickets.operations(id, "sales");
  }

  /** 财务岗位只须退款权限，保留相同活动范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/events/{id}/finance")
  public Object finance(@PathVariable String id) {
    return tickets.operations(id, "refunds");
  }

  /** 维护岗位查看真实座位图。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/events/{id}/layout")
  public Object layout(@PathVariable String id) {
    return tickets.layout(id);
  }

  /** 主办发起未核销票退款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders/{id}/refund")
  public Object organizerRefund(@PathVariable String id) {
    tickets.refund(id, true);
    return Map.of("ok", true);
  }

  /** 授权活动检票。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/events/{id}/check-in")
  public Object checkIn(@PathVariable String id, @RequestBody Map<String, Object> b) {
    return tickets.checkIn(id, Rules.text(b.get("code"), 128, true));
  }

  /** 同一个原任务重试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/tasks/{kind}/{id}/retry")
  public Object retry(@PathVariable String kind, @PathVariable String id) {
    tickets.retry(kind, id);
    return Map.of("ok", true);
  }

  /** 活动报表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Object reports() {
    return tickets.reports();
  }

  /** 脱敏CSV只经授权报表服务生成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports.csv")
  public ResponseEntity<byte[]> export() {
    StringBuilder csv =
        new StringBuilder("event,mode,currency,issued,checked,gross,refunded,net\r\n");
    for (var r : tickets.reports()) {
      csv.append(
              String.join(
                  ",",
                  List.of(
                          "name",
                          "mode",
                          "currency",
                          "issued",
                          "checked",
                          "gross",
                          "refunded",
                          "net")
                      .stream()
                      .map(k -> Rules.csv(r.get(k)))
                      .toList()))
          .append("\r\n");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header("Content-Disposition", "attachment; filename=event-reports.csv")
        .body(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /** 组织审计查询。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return tickets.audit();
  }

  /** 安全后台目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{kind}")
  @Transactional(readOnly = true)
  public Object admin(@PathVariable String kind) {
    return admin.read(kind);
  }

  /** 账号选项目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin-options")
  @Transactional(readOnly = true)
  public Object options() {
    return admin.options();
  }

  /** 新建后台目录资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{kind}")
  public Object adminCreate(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return admin.save(kind, null, b);
  }

  /** 版本保护的后台目录更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{kind}/{id}")
  public Object adminUpdate(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return admin.save(kind, id, b);
  }

  /** 仅该回调免CSRF，仍须原文时间窗验签。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/payments/stripe/webhook")
  public Object webhook(
      @RequestBody byte[] raw,
      @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
    return payments.webhook(raw, signature);
  }
}
