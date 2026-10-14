// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Stripe真实REST适配、原文回调验签；本地测试不等于商户沙箱验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class StripeGateway {
  final GatewayConfig config;
  final Clock clock;
  HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  final JsonMapper mapper = JsonMapper.builder().build();

  public StripeGateway(GatewayConfig config, Clock clock) {
    this.config = config;
    this.clock = clock;
  }

  /** 服务端价格建单；稳定幂等键允许网络未知后恢复，不凭跳转出票。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public JsonNode checkout(Booking b, String title) {
    Rules.check(
        b.expiresAt / 1000 - clock.instant().getEpochSecond() > 1805, "CHECKOUT_WINDOW_CLOSED");
    var form = new LinkedHashMap<String, String>();
    form.put("mode", "payment");
    form.put("payment_method_types[0]", "card");
    form.put("client_reference_id", b.id);
    form.put("metadata[booking_id]", b.id);
    form.put("payment_intent_data[metadata][booking_id]", b.id);
    form.put("line_items[0][price_data][currency]", b.currency.toLowerCase(Locale.ROOT));
    form.put("line_items[0][price_data][unit_amount]", String.valueOf(b.price));
    form.put("line_items[0][price_data][product_data][name]", title);
    form.put("line_items[0][quantity]", "1");
    form.put("expires_at", String.valueOf(b.expiresAt / 1000));
    form.put("success_url", config.url + "/#/wallet?order=" + b.id);
    form.put("cancel_url", config.url + "/#/wallet?order=" + b.id);
    return request("POST", "/v1/checkout/sessions", form, "checkout-" + b.id);
  }

  /** 退款和轮询始终绑定原付款、原金额和稳定任务键。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public JsonNode refund(RefundTask t) {
    if (t.refundId != null) return request("GET", "/v1/refunds/" + t.refundId, Map.of(), null);
    return request(
        "POST",
        "/v1/refunds",
        Map.of(
            "payment_intent",
            t.paymentIntent,
            "amount",
            String.valueOf(t.amount),
            "metadata[task_id]",
            t.id),
        "refund-" + t.id + "-g" + t.generation);
  }

  /** 验证时间窗和全部v1签名，先验原文再解析；失败不接受事件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public JsonNode webhook(byte[] raw, String header) {
    Rules.check(
        config.mode.equals("STRIPE")
            && raw.length <= 65536
            && header != null
            && header.length() <= 2048,
        "WEBHOOK_INVALID");
    long timestamp = -1;
    var signatures = new ArrayList<String>();
    for (String part : header.split(",")) {
      String[] pair = part.strip().split("=", 2);
      if (pair.length == 2) {
        if (pair[0].equals("t")) {
          Rules.check(timestamp == -1, "WEBHOOK_INVALID");
          try {
            timestamp = Long.parseLong(pair[1]);
          } catch (Exception e) {
            throw new Problem(400, "WEBHOOK_INVALID");
          }
        } else if (pair[0].equals("v1")) signatures.add(pair[1]);
      }
    }
    Rules.check(
        timestamp > 0
            && timestamp >= clock.instant().getEpochSecond() - 300
            && timestamp <= clock.instant().getEpochSecond() + 300,
        "WEBHOOK_INVALID");
    String text = new String(raw, StandardCharsets.UTF_8);
    byte[] expected =
        hmac(config.webhook, (timestamp + "." + text).getBytes(StandardCharsets.UTF_8));
    boolean ok = false;
    for (String signature : signatures) {
      try {
        ok |= MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature));
      } catch (Exception ignored) {
      }
    }
    Rules.check(ok, "WEBHOOK_INVALID");
    try {
      return mapper.readTree(raw);
    } catch (Exception e) {
      throw new Problem(400, "WEBHOOK_INVALID");
    }
  }

  private JsonNode request(
      String method, String path, Map<String, String> params, String idempotency) {
    Rules.check(config.mode.equals("STRIPE"), "PAYMENT_DISABLED");
    Rules.check(
        path.matches("/v1/(checkout/sessions|refunds(/[a-zA-Z0-9_]+)?)"),
        "PAYMENT_RESPONSE_INVALID");
    String form =
        params.entrySet().stream()
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(java.util.stream.Collectors.joining("&"));
    var builder =
        HttpRequest.newBuilder(URI.create("https://api.stripe.com" + path))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + config.key)
            .header("Content-Type", "application/x-www-form-urlencoded");
    if (idempotency != null) builder.header("Idempotency-Key", idempotency);
    if (method.equals("POST")) builder.POST(HttpRequest.BodyPublishers.ofString(form));
    else builder.GET();
    try {
      var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
      try (var in = response.body()) {
        byte[] body = in.readNBytes(65537);
        if (response.statusCode() < 200 || response.statusCode() >= 300 || body.length > 65536)
          throw new Problem(503, "PAYMENT_UNAVAILABLE");
        return mapper.readTree(body);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Problem(503, "PAYMENT_UNAVAILABLE");
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(503, "PAYMENT_UNAVAILABLE");
    }
  }

  static byte[] hmac(String key, byte[] body) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(body);
    } catch (Exception e) {
      throw new IllegalStateException("HMAC unavailable");
    }
  }
}
