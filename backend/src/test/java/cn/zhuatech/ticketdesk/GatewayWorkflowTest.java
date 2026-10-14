// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 真实验签/HTTP/数据库状态配合模拟外部HTTP响应；绝不表示Stripe商户已验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(
    properties = {
      "ticketdesk.admin-username=qaadmin",
      "ticketdesk.admin-password=Aa9TicketTestOnlyPassword",
      "ticketdesk.signing-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "ticketdesk.payment-mode=STRIPE",
      "ticketdesk.public-url=https://tickets.example.invalid",
      "ticketdesk.stripe-secret-key=sk_test_QA_NOT_A_REAL_KEY",
      "ticketdesk.stripe-webhook-secret=whsec_QA_NOT_A_REAL_SECRET",
      "server.servlet.session.cookie.secure=true",
      "ticketdesk.scheduler-enabled=false"
    })
class GatewayWorkflowTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    boolean mysql = System.getenv("CORE_TEST_DATABASE_URL") != null;
    r.add(
        "spring.datasource.url",
        () ->
            mysql
                ? System.getenv("CORE_TEST_DATABASE_URL")
                : "jdbc:h2:mem:ticketgateway;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> mysql ? System.getenv("DATABASE_USER") : "sa");
    r.add("spring.datasource.password", () -> mysql ? System.getenv("DATABASE_PASSWORD") : "");
    r.add(
        "spring.jpa.database-platform",
        () ->
            mysql ? "cn.zhuatech.ticketdesk.MySqlJdbcDialect" : "org.hibernate.dialect.H2Dialect");
  }

  @Autowired MockMvc mvc;
  @Autowired StripeGateway stripe;
  @Autowired TaskService tasks;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, buyer;
  String booking, session, intent;
  long expires;

  MvcResult call(MockHttpServletRequestBuilder r, MockHttpSession s, Object b, int code)
      throws Exception {
    if (s != null) r.session(s);
    if (b instanceof Raw raw) r.contentType("application/json").content(raw.data());
    else if (b != null) r.contentType("application/json").content(json.writeValueAsString(b));
    var result = mvc.perform(r.with(csrf())).andReturn();
    assertEquals(code, result.getResponse().getStatus());
    return result;
  }

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  MockHttpSession login(String user, String pw) throws Exception {
    return (MockHttpSession)
        call(post("/api/auth/login"), null, Map.of("username", user, "password", pw), 200)
            .getRequest()
            .getSession(false);
  }

  @SuppressWarnings("unchecked")
  void reply(Object data) throws Exception {
    var res = (HttpResponse<InputStream>) mock(HttpResponse.class);
    when(res.statusCode()).thenReturn(200);
    when(res.body()).thenReturn(new ByteArrayInputStream(json.writeValueAsBytes(data)));
    when(stripe.client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(res);
  }

  @BeforeEach
  void setup() throws Exception {
    stripe.client = mock(HttpClient.class);
    admin = login("qaadmin", "Aa9TicketTestOnlyPassword");
    String suffix = UUID.randomUUID().toString().substring(0, 8), user = "qa-stripe-" + suffix;
    call(
        post("/api/auth/register"),
        null,
        Map.of(
            "username",
            user,
            "password",
            "Aa9QaGatewayOnlyPassword",
            "displayName",
            "QA buyer",
            "email",
            "qa@example.invalid"),
        200);
    buyer = login(user, "Aa9QaGatewayOnlyPassword");
    var e = new LinkedHashMap<String, Object>();
    e.put("name", "QA gateway " + suffix);
    e.put("nameEn", "QA gateway " + suffix);
    e.put("venue", "QA Hall");
    e.put("venueEn", "QA Hall");
    e.put("description", "");
    e.put("descriptionEn", "");
    e.put("category", "TALK");
    e.put("currency", "CNY");
    e.put("departmentId", 1);
    e.put("staff", List.of());
    e.put("state", "DRAFT");
    var now = Instant.now();
    e.put("startsAt", now.plusSeconds(3600).toString());
    e.put("saleStartsAt", now.minusSeconds(3600).toString());
    e.put("saleEndsAt", now.plusSeconds(1800).toString());
    e.put("refundEndsAt", now.plusSeconds(1200).toString());
    String event = body(call(post("/api/events"), admin, e, 200)).path("id").asText();
    call(
        post("/api/events/" + event + "/types"),
        admin,
        Map.of(
            "name",
            "QA",
            "nameEn",
            "QA",
            "prefix",
            "A-",
            "price",
            2000,
            "capacity",
            2,
            "columns",
            2),
        200);
    e.put("version", 1);
    e.put("state", "PUBLISHED");
    call(put("/api/events/" + event), admin, e, 200);
    long seat =
        body(call(get("/api/public/events/" + event), null, null, 200))
            .path("seats")
            .get(0)
            .path("id")
            .asLong();
    var held =
        body(
            call(
                post("/api/events/" + event + "/reserve"),
                buyer,
                Map.of("seatId", seat, "requestId", UUID.randomUUID().toString()),
                200));
    booking = held.path("id").asText();
    expires = held.path("expiresAt").asLong() / 1000;
    session = "cs_test_QA_" + suffix;
    intent = "pi_QA_" + suffix;
    reply(
        Map.of(
            "id",
            session,
            "url",
            "https://checkout.stripe.com/c/pay/qa",
            "client_reference_id",
            booking,
            "amount_total",
            2000,
            "currency",
            "cny",
            "expires_at",
            expires));
    call(post("/api/wallet/" + booking + "/checkout"), buyer, null, 200);
  }

  Map<String, Object> event(String kind, int amount, String sessionId) {
    return Map.of(
        "type",
        kind,
        "data",
        Map.of(
            "object",
            Map.of(
                "id",
                sessionId,
                "client_reference_id",
                booking,
                "payment_status",
                "paid",
                "payment_intent",
                intent,
                "amount_total",
                amount,
                "currency",
                "cny",
                "livemode",
                false)));
  }

  void webhook(Object payload, int expected) throws Exception {
    byte[] raw = json.writeValueAsBytes(payload);
    long time = Instant.now().getEpochSecond();
    String signature =
        "t="
            + time
            + ",v1="
            + HexFormat.of()
                .formatHex(
                    StripeGateway.hmac(
                        "whsec_QA_NOT_A_REAL_SECRET",
                        (time + "." + new String(raw, StandardCharsets.UTF_8))
                            .getBytes(StandardCharsets.UTF_8)));
    call(
        post("/api/payments/stripe/webhook").header("Stripe-Signature", signature),
        null,
        nullBytes(raw),
        expected);
  }

  Object nullBytes(byte[] raw) {
    return new Raw(raw);
  }

  record Raw(byte[] data) {}

  @Test
  void sameChargeAcrossDifferentNotificationsIssuesOnlyOneTicket() throws Exception {
    webhook(event("checkout.session.completed", 2000, session), 200);
    String ticket =
        body(call(get("/api/wallet/" + booking), buyer, null, 200)).path("ticketId").asText();
    webhook(event("checkout.session.async_payment_succeeded", 2000, session), 200);
    var order = body(call(get("/api/wallet/" + booking), buyer, null, 200));
    assertEquals(ticket, order.path("ticketId").asText());
    assertEquals("PAID", order.path("state").asText());
    call(post("/api/wallet/" + booking + "/demo-pay"), buyer, null, 409);
  }

  @Test
  void signedButWrongSessionOrAmountCannotIssue() throws Exception {
    webhook(event("checkout.session.completed", 1999, session), 409);
    webhook(event("checkout.session.completed", 2000, "cs_test_OTHER"), 409);
    var order = body(call(get("/api/wallet/" + booking), buyer, null, 200));
    assertEquals("HELD", order.path("state").asText());
    assertFalse(order.has("ticketId"));
  }

  @Test
  void refundSuccessIsBoundToOriginalAmountAndIntent() throws Exception {
    webhook(event("checkout.session.completed", 2000, session), 200);
    call(post("/api/wallet/" + booking + "/refund"), buyer, null, 200);
    String event =
        body(call(get("/api/wallet/" + booking), buyer, null, 200)).path("eventId").asText();
    var t =
        body(call(get("/api/events/" + event + "/finance"), admin, null, 200))
            .path("refunds")
            .get(0);
    reply(
        Map.of(
            "id",
            "re_QA_" + booking.replace("-", ""),
            "status",
            "succeeded",
            "payment_intent",
            intent,
            "amount",
            1999,
            "currency",
            "cny"));
    tasks.refund(t.path("id").asText());
    assertEquals(
        "REFUND_PENDING",
        body(call(get("/api/wallet/" + booking), buyer, null, 200)).path("state").asText());
    call(post("/api/tasks/refund/" + t.path("id").asText() + "/retry"), admin, null, 200);
    reply(
        Map.of(
            "id",
            "re_QA_" + booking.replace("-", ""),
            "status",
            "succeeded",
            "payment_intent",
            intent,
            "amount",
            2000,
            "currency",
            "cny"));
    tasks.refund(t.path("id").asText());
    assertEquals(
        "REFUNDED",
        body(call(get("/api/wallet/" + booking), buyer, null, 200)).path("state").asText());
  }
}
