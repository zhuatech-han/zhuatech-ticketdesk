// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 本地合同和伪造回调测试；不调用真实Stripe或使用真实凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class StripeGatewayTest {
  final Clock clock = Clock.fixed(Instant.parse("2026-10-13T03:00:00Z"), ZoneOffset.UTC);
  final GatewayConfig config =
      new GatewayConfig(
          "STRIPE",
          "https://tickets.example.invalid",
          "sk_test_QA_NOT_A_REAL_KEY",
          "whsec_QA_NOT_A_REAL_SECRET",
          true);

  @Test
  void invalidRealPaymentConfigurationFailsClosed() {
    assertThrows(
        Problem.class, () -> new GatewayConfig("STRIPE", "http://localhost:8160", "", "", false));
    assertThrows(
        Problem.class, () -> new GatewayConfig("OTHER", "http://localhost", "", "", false));
  }

  String signature(String body, long epoch) {
    return "t="
        + epoch
        + ",v1="
        + HexFormat.of()
            .formatHex(
                StripeGateway.hmac(
                    config.webhook, (epoch + "." + body).getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void originalBodyAndTimestampAreVerified() {
    var gateway = new StripeGateway(config, clock);
    String body = "{\"type\":\"checkout.session.completed\"}";
    long timestamp = clock.instant().getEpochSecond();
    assertEquals(
        "checkout.session.completed",
        gateway
            .webhook(body.getBytes(StandardCharsets.UTF_8), signature(body, timestamp))
            .path("type")
            .asText());
    assertThrows(
        Problem.class,
        () ->
            gateway.webhook(
                (body + " ").getBytes(StandardCharsets.UTF_8), signature(body, timestamp)));
    assertThrows(
        Problem.class,
        () ->
            gateway.webhook(
                body.getBytes(StandardCharsets.UTF_8), signature(body, timestamp - 301)));
    assertThrows(
        Problem.class,
        () ->
            gateway.webhook(
                body.getBytes(StandardCharsets.UTF_8), signature(body, timestamp + 301)));
    assertThrows(Problem.class, () -> gateway.webhook(body.getBytes(StandardCharsets.UTF_8), null));
  }

  @Test
  void malformedAndDuplicateTimestampHeadersAreRejected() {
    var gateway = new StripeGateway(config, clock);
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    for (String s :
        List.of("t=no,v1=ff", "t=1,t=2,v1=ff", "t=" + clock.instant().getEpochSecond() + ",v1=zz"))
      assertThrows(Problem.class, () -> gateway.webhook(body, s));
  }

  @SuppressWarnings("unchecked")
  HttpResponse<InputStream> response(String body, int status) {
    var r = (HttpResponse<InputStream>) mock(HttpResponse.class);
    when(r.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    when(r.statusCode()).thenReturn(status);
    return r;
  }

  @Test
  void checkoutCarriesFrozenAmountAndStableIdempotency() throws Exception {
    var gateway = new StripeGateway(config, clock);
    gateway.client = mock(HttpClient.class);
    var reply = response("{}", 200);
    when(gateway.client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(reply);
    var b = new Booking();
    b.id = UUID.randomUUID().toString();
    b.price = 2000;
    b.currency = "CNY";
    b.expiresAt = clock.millis() + 35 * 60000;
    gateway.checkout(b, "QA event");
    var capture = ArgumentCaptor.forClass(HttpRequest.class);
    verify(gateway.client).send(capture.capture(), any(HttpResponse.BodyHandler.class));
    assertEquals(
        "https://api.stripe.com/v1/checkout/sessions", capture.getValue().uri().toString());
    assertEquals(
        "checkout-" + b.id,
        capture.getValue().headers().firstValue("Idempotency-Key").orElseThrow());
    assertTrue(capture.getValue().bodyPublisher().isPresent());
    b.expiresAt = clock.millis() + 20 * 60000;
    assertThrows(Problem.class, () -> gateway.checkout(b, "QA"));
  }

  @Test
  void gatewayFailureDoesNotPretendSuccess() throws Exception {
    var gateway = new StripeGateway(config, clock);
    gateway.client = mock(HttpClient.class);
    var reply = response("{}", 500);
    when(gateway.client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(reply);
    var task = new RefundTask();
    task.id = UUID.randomUUID().toString();
    task.paymentIntent = "pi_QA";
    task.amount = 2000;
    assertEquals(
        "PAYMENT_UNAVAILABLE",
        assertThrows(Problem.class, () -> gateway.refund(task)).getMessage());
  }
}
