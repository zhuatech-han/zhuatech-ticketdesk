// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

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

/** 真实HTTP安全链和持久化流程；只创建QA资料，不操作真实款项或SMTP。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(
    properties = {
      "ticketdesk.admin-username=qaadmin",
      "ticketdesk.admin-password=Aa9TicketTestOnlyPassword",
      "ticketdesk.signing-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "ticketdesk.payment-mode=DEMO",
      "ticketdesk.public-url=http://localhost:8160",
      "ticketdesk.scheduler-enabled=false"
    })
class HttpWorkflowTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    boolean mysql = System.getenv("CORE_TEST_DATABASE_URL") != null;
    r.add(
        "spring.datasource.url",
        () ->
            mysql
                ? System.getenv("CORE_TEST_DATABASE_URL")
                : "jdbc:h2:mem:ticketapi;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> mysql ? System.getenv("DATABASE_USER") : "sa");
    r.add("spring.datasource.password", () -> mysql ? System.getenv("DATABASE_PASSWORD") : "");
    r.add(
        "spring.jpa.database-platform",
        () ->
            mysql ? "cn.zhuatech.ticketdesk.MySqlJdbcDialect" : "org.hibernate.dialect.H2Dialect");
  }

  static final String PW = "Aa9QaAccountOnlyPassword";
  @Autowired MockMvc mvc;
  @Autowired TaskService tasks;
  @Autowired Store db;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, buyer, other, gate;
  String event;
  long gateId;
  Map<String, Object> eventBody;

  JsonNode read(MvcResult result) {
    try {
      return json.readTree(result.getResponse().getContentAsString());
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  MvcResult request(
      MockHttpServletRequestBuilder req, MockHttpSession session, Object body, int status)
      throws Exception {
    if (session != null) req.session(session);
    if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
    if (!req.toString().isEmpty()) req.with(csrf());
    var result = mvc.perform(req).andReturn();
    assertEquals(
        status,
        result.getResponse().getStatus(),
        () -> "HTTP status / " + read(result).path("code").asText());
    return result;
  }

  MockHttpSession login(String username, String password) throws Exception {
    return (MockHttpSession)
        request(
                post("/api/auth/login"),
                null,
                Map.of("username", username, "password", password),
                200)
            .getRequest()
            .getSession(false);
  }

  long role(String scope, String permission) throws Exception {
    for (var r : read(request(get("/api/admin-options"), admin, null, 200)).path("roles"))
      if (r.path("scope").asText().equals(scope)
          && r.path("permissions").toString().contains("\"" + permission + "\""))
        return r.path("id").asLong();
    throw new AssertionError("missing role");
  }

  long account(String username, String scope, String permission) throws Exception {
    var b = new LinkedHashMap<String, Object>();
    b.put("username", username);
    b.put("displayName", "QA " + username);
    b.put("roleId", role(scope, permission));
    b.put("departmentId", 1);
    b.put("enabled", true);
    b.put("password", PW);
    return read(request(post("/api/admin/users"), admin, b, 200)).path("id").asLong();
  }

  @BeforeEach
  void prepare() throws Exception {
    admin = login("qaadmin", "Aa9TicketTestOnlyPassword");
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String u = "qa-b-" + suffix, v = "qa-o-" + suffix, g = "qa-g-" + suffix;
    account(u, "CUSTOMER", "portal");
    account(v, "CUSTOMER", "portal");
    gateId = account(g, "DEPARTMENT", "checkin");
    buyer = login(u, PW);
    other = login(v, PW);
    gate = login(g, PW);
    eventBody = new LinkedHashMap<>();
    eventBody.put("name", "QA Event " + suffix);
    eventBody.put("nameEn", "QA Event " + suffix);
    eventBody.put("venue", "QA Hall");
    eventBody.put("venueEn", "QA Hall");
    eventBody.put("description", "QA only");
    eventBody.put("descriptionEn", "QA only");
    eventBody.put("category", "TALK");
    eventBody.put("currency", "CNY");
    eventBody.put("departmentId", 1);
    eventBody.put("staff", List.of(gateId));
    eventBody.put("state", "DRAFT");
    Instant now = Instant.now();
    eventBody.put("startsAt", now.plusSeconds(3600).toString());
    eventBody.put("saleStartsAt", now.minusSeconds(3600).toString());
    eventBody.put("saleEndsAt", now.plusSeconds(1800).toString());
    eventBody.put("refundEndsAt", now.plusSeconds(1200).toString());
    event = read(request(post("/api/events"), admin, eventBody, 200)).path("id").asText();
    request(
        post("/api/events/" + event + "/types"),
        admin,
        Map.of(
            "name",
            "QA ticket",
            "nameEn",
            "QA ticket",
            "prefix",
            "A-",
            "price",
            2000,
            "capacity",
            3,
            "columns",
            3),
        200);
    eventBody.put("version", 1);
    eventBody.put("state", "PUBLISHED");
    request(put("/api/events/" + event), admin, eventBody, 200);
    eventBody.put("version", 2);
  }

  String reserve(MockHttpSession owner, int index) throws Exception {
    long seat =
        read(request(get("/api/public/events/" + event), null, null, 200))
            .path("seats")
            .get(index)
            .path("id")
            .asLong();
    return read(request(
            post("/api/events/" + event + "/reserve"),
            owner,
            Map.of("seatId", seat, "requestId", UUID.randomUUID().toString()),
            200))
        .path("id")
        .asText();
  }

  JsonNode pay(String id) throws Exception {
    request(post("/api/wallet/" + id + "/checkout"), buyer, null, 200);
    return read(request(post("/api/wallet/" + id + "/demo-pay"), buyer, null, 200));
  }

  @Test
  void completeIssuanceRecoveryAndDuplicateGate() throws Exception {
    String id = reserve(buyer, 0);
    var paid = pay(id);
    assertEquals("PAID", paid.path("state").asText());
    assertEquals(
        paid.path("code").asText(),
        read(request(get("/api/wallet/" + id), buyer, null, 200)).path("code").asText());
    assertEquals(
        "ADMITTED",
        read(request(
                post("/api/events/" + event + "/check-in"),
                gate,
                Map.of("code", paid.path("code").asText()),
                200))
            .path("decision")
            .asText());
    assertEquals(
        "ALREADY_USED",
        read(request(
                post("/api/events/" + event + "/check-in"),
                gate,
                Map.of("code", paid.path("code").asText()),
                200))
            .path("decision")
            .asText());
    assertEquals(
        "CHECKED_IN",
        read(request(get("/api/wallet/" + id), buyer, null, 200)).path("ticketState").asText());
  }

  @Test
  void refundVoidsAndQueueReleasesOnlyAfterConfirmation() throws Exception {
    String id = reserve(buyer, 0);
    var paid = pay(id);
    request(post("/api/wallet/" + id + "/refund"), buyer, null, 200);
    assertEquals(
        "VOID",
        read(request(
                post("/api/events/" + event + "/check-in"),
                gate,
                Map.of("code", paid.path("code").asText()),
                200))
            .path("decision")
            .asText());
    assertFalse(
        read(request(get("/api/public/events/" + event), null, null, 200))
            .path("seats")
            .get(0)
            .path("available")
            .asBoolean());
    var t =
        read(request(get("/api/events/" + event + "/finance"), admin, null, 200))
            .path("refunds")
            .get(0);
    tasks.refund(t.path("id").asText());
    tasks.refund(t.path("id").asText());
    assertEquals(
        "REFUNDED",
        read(request(get("/api/wallet/" + id), buyer, null, 200)).path("state").asText());
    assertTrue(
        read(request(get("/api/public/events/" + event), null, null, 200))
            .path("seats")
            .get(0)
            .path("available")
            .asBoolean());
    assertFalse(
        read(request(get("/api/events/" + event + "/operations"), admin, null, 200))
            .toString()
            .contains("td1."));
  }

  @Test
  void otherAttendeeCannotReadPayCancelOrRefund() throws Exception {
    String id = reserve(buyer, 0);
    for (var req :
        List.of(
            get("/api/wallet/" + id),
            post("/api/wallet/" + id + "/checkout"),
            post("/api/wallet/" + id + "/cancel"),
            post("/api/wallet/" + id + "/refund"))) {
      int status =
          mvc.perform(req.session(other).with(csrf())).andReturn().getResponse().getStatus();
      assertTrue(status >= 400 && status < 500);
    }
    assertEquals(0, read(request(get("/api/wallet"), other, null, 200)).size());
  }

  @Test
  void staffWithoutEventAssignmentCannotScan() throws Exception {
    String id = reserve(buyer, 0);
    var ticket = pay(id);
    String user = "qa-unassigned-" + UUID.randomUUID().toString().substring(0, 6);
    account(user, "DEPARTMENT", "checkin");
    var session = login(user, PW);
    int status =
        mvc.perform(
                post("/api/events/" + event + "/check-in")
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("code", ticket.path("code").asText()))))
            .andReturn()
            .getResponse()
            .getStatus();
    assertTrue(status >= 400 && status < 500);
    assertEquals(0, read(request(get("/api/catalog"), session, null, 200)).path("events").size());
  }

  @Test
  void roleAndAdminEndpointsAreProtectedAndCsrfRequired() throws Exception {
    request(get("/api/admin/users"), buyer, null, 403);
    request(get("/api/catalog"), buyer, null, 403);
    request(get("/api/wallet"), null, null, 401);
    var result =
        mvc.perform(
                post("/api/events")
                    .session(admin)
                    .contentType("application/json")
                    .content(json.writeValueAsString(eventBody)))
            .andReturn();
    assertEquals(403, result.getResponse().getStatus());
    var users = read(request(get("/api/admin/users"), admin, null, 200)).toString();
    assertFalse(users.contains("passwordHash"));
    assertFalse(users.contains(PW));
  }

  @Test
  void cancelledHoldIsReusableAndCannotIssueLate() throws Exception {
    String id = reserve(buyer, 0);
    request(post("/api/wallet/" + id + "/checkout"), buyer, null, 200);
    request(post("/api/wallet/" + id + "/cancel"), buyer, null, 200);
    String next = reserve(other, 0);
    var old = read(request(post("/api/wallet/" + id + "/demo-pay"), buyer, null, 200));
    assertEquals("EXPIRED", old.path("state").asText());
    assertFalse(old.has("code"));
    assertEquals(
        "HELD", read(request(get("/api/wallet/" + next), other, null, 200)).path("state").asText());
    var t =
        read(request(get("/api/events/" + event + "/finance"), admin, null, 200))
            .path("refunds")
            .get(0);
    tasks.refund(t.path("id").asText());
    assertFalse(t.path("releasesSeat").asBoolean());
    assertEquals(
        "HELD", read(request(get("/api/wallet/" + next), other, null, 200)).path("state").asText());
  }

  @Test
  void soldEventRuleAndSeatLayoutChangesAreRejected() throws Exception {
    reserve(buyer, 0);
    var changed = new LinkedHashMap<>(eventBody);
    changed.put("currency", "USD");
    request(put("/api/events/" + event), admin, changed, 409);
    request(
        post("/api/events/" + event + "/types"),
        admin,
        Map.of(
            "name", "x", "nameEn", "x", "prefix", "B-", "price", 1000, "capacity", 1, "columns", 1),
        409);
    request(delete("/api/events/" + event), admin, null, 409);
  }

  @Test
  void versionAndLastAdminProtectionWork() throws Exception {
    var stale = new LinkedHashMap<>(eventBody);
    stale.put("version", 1);
    request(put("/api/events/" + event), admin, stale, 409);
    var users = read(request(get("/api/admin/users"), admin, null, 200));
    JsonNode row = null;
    for (var u : users) if (u.path("username").asText().equals("qaadmin")) row = u;
    assertNotNull(row);
    var b = json.convertValue(row, Map.class);
    b.put("enabled", false);
    request(put("/api/admin/users/" + row.path("id").asLong()), admin, b, 409);
    request(get("/api/auth/me"), admin, null, 200);
  }

  @Test
  void disabledAccountSessionIsRevoked() throws Exception {
    var users = read(request(get("/api/admin/users"), admin, null, 200));
    long id = read(request(get("/api/auth/me"), buyer, null, 200)).path("id").asLong();
    for (var row : users)
      if (row.path("id").asLong() == id) {
        var b = json.convertValue(row, Map.class);
        b.put("enabled", false);
        request(put("/api/admin/users/" + id), admin, b, 200);
      }
    request(get("/api/wallet"), buyer, null, 401);
  }

  @Test
  void closedSalesAndAlreadyAdmittedRefundAreRejected() throws Exception {
    String id = reserve(buyer, 0);
    var paid = pay(id);
    request(
        post("/api/events/" + event + "/check-in"),
        gate,
        Map.of("code", paid.path("code").asText()),
        200);
    request(post("/api/wallet/" + id + "/refund"), buyer, null, 409);
    var closed = new LinkedHashMap<>(eventBody);
    closed.put("state", "CLOSED");
    request(put("/api/events/" + event), admin, closed, 200);
    request(get("/api/public/events/" + event), null, null, 409);
  }

  @Test
  void registrationCannotChooseStaffRole() throws Exception {
    String name = "qa-register-" + UUID.randomUUID().toString().substring(0, 6);
    request(
        post("/api/auth/register"),
        null,
        Map.of(
            "username",
            name,
            "password",
            PW,
            "displayName",
            "QA attendee",
            "email",
            "qa@example.invalid",
            "roleId",
            1,
            "permissions",
            List.of("users")),
        200);
    var s = login(name, PW);
    var profile = read(request(get("/api/auth/me"), s, null, 200));
    assertEquals("CUSTOMER", profile.path("scope").asText());
    assertEquals(1, profile.path("permissions").size());
    request(get("/api/admin/users"), s, null, 403);
  }

  @Test
  void disabledCategoryAndStaffDoNotPreventClosingAnExistingEvent() throws Exception {
    var users = read(request(get("/api/admin/users"), admin, null, 200));
    for (var row : users)
      if (row.path("id").asLong() == gateId) {
        var body = json.convertValue(row, Map.class);
        body.put("enabled", false);
        request(put("/api/admin/users/" + gateId), admin, body, 200);
      }
    JsonNode category = null;
    for (var row : read(request(get("/api/admin/dictionaries"), admin, null, 200)))
      if (row.path("code").asText().equals("TALK")) category = row;
    assertNotNull(category);
    var body = json.convertValue(category, Map.class);
    body.put("enabled", false);
    request(put("/api/admin/dictionaries/" + category.path("id").asLong()), admin, body, 200);
    try {
      var closed = new LinkedHashMap<>(eventBody);
      closed.put("state", "CLOSED");
      request(put("/api/events/" + event), admin, closed, 200);
      var catalog = read(request(get("/api/catalog"), admin, null, 200));
      for (var row : catalog.path("events"))
        if (row.path("id").asText().equals(event)) {
          assertEquals(0, row.path("staff").size());
          assertEquals("CLOSED", row.path("state").asText());
        }
    } finally {
      body.put("enabled", true);
      request(put("/api/admin/dictionaries/" + category.path("id").asLong()), admin, body, 200);
    }
  }

  @Test
  void freeTicketIssuesWithoutDemoMoney() throws Exception {
    var body = new LinkedHashMap<>(eventBody);
    body.remove("version");
    body.put("state", "DRAFT");
    String free = read(request(post("/api/events"), admin, body, 200)).path("id").asText();
    request(
        post("/api/events/" + free + "/types"),
        admin,
        Map.of(
            "name",
            "Free",
            "nameEn",
            "Free",
            "prefix",
            "F-",
            "price",
            0,
            "capacity",
            1,
            "columns",
            1),
        200);
    body.put("version", 1);
    body.put("state", "PUBLISHED");
    request(put("/api/events/" + free), admin, body, 200);
    event = free;
    String id = reserve(buyer, 0);
    assertEquals(
        "FREE",
        read(request(post("/api/wallet/" + id + "/checkout"), buyer, null, 200))
            .path("mode")
            .asText());
    assertEquals(
        "PAID", read(request(get("/api/wallet/" + id), buyer, null, 200)).path("state").asText());
  }
}
