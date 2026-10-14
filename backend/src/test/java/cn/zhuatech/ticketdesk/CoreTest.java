// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;

/** 真实JPA/Flyway事务与并发，模拟资金输入非银行验证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(CoreTest.TimeConfig.class)
@org.springframework.test.context.TestPropertySource(
    properties = {
      "ticketdesk.admin-username=qaadmin",
      "ticketdesk.admin-password=Aa9TicketTestOnlyPassword",
      "ticketdesk.signing-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "ticketdesk.payment-mode=DEMO",
      "ticketdesk.public-url=http://localhost:8160",
      "ticketdesk.scheduler-enabled=false"
    })
class CoreTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    boolean mysql = System.getenv("CORE_TEST_DATABASE_URL") != null;
    r.add(
        "spring.datasource.url",
        () ->
            mysql
                ? System.getenv("CORE_TEST_DATABASE_URL")
                : "jdbc:h2:mem:ticketlab;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> mysql ? System.getenv("DATABASE_USER") : "sa");
    r.add("spring.datasource.password", () -> mysql ? System.getenv("DATABASE_PASSWORD") : "");
    r.add(
        "spring.jpa.database-platform",
        () ->
            mysql ? "cn.zhuatech.ticketdesk.MySqlJdbcDialect" : "org.hibernate.dialect.H2Dialect");
  }

  /** 可控服务时钟，使边界测试不依赖sleep。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class QaClock extends Clock {
    final AtomicLong millis = new AtomicLong();

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      if (!z.equals(ZoneOffset.UTC)) throw new IllegalArgumentException();
      return this;
    }

    public Instant instant() {
      return Instant.ofEpochMilli(millis.get());
    }

    void reset() {
      millis.set(Instant.parse("2026-10-13T03:00:00Z").toEpochMilli());
    }

    void advance(long delta) {
      millis.addAndGet(delta);
    }
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    QaClock qaClock() {
      return new QaClock();
    }
  }

  @Autowired Core core;
  @Autowired QaClock time;
  String event;
  List<Long> seats;
  final String owner = "QA-OWNER";

  @BeforeEach
  void prepare() {
    time.reset();
    event = id();
    seats = core.createSeats(event, 200, 2000, "CNY", "QA-");
    assertEquals(200, seats.size());
  }

  private String id() {
    return UUID.randomUUID().toString();
  }

  private String hold(long seat) {
    return core.reserve(owner, seat, id());
  }

  private Core.PaymentResult buy(String booking) {
    return core.verifiedPayment(booking, id(), 2000, "CNY");
  }

  private void denied(String code, Runnable op) {
    assertEquals(code, assertThrows(Refusal.class, op::run).getMessage());
  }

  /** 199座已出票，32个独立事务争最后一座，只能成功一个。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void lastSeatHasOneWinner() throws Exception {
    for (int i = 0; i < 199; i++) assertEquals("TICKET", buy(hold(seats.get(i))).status());
    List<String> outcomes =
        race(
            32,
            () -> {
              try {
                return core.reserve("QA-RACER-" + id(), seats.get(199), id());
              } catch (Refusal e) {
                assertEquals("SEAT_UNAVAILABLE", e.getMessage());
                return "REJECTED";
              }
            });
    var winners = outcomes.stream().filter(x -> !x.equals("REJECTED")).toList();
    assertEquals(1, winners.size());
    assertEquals("TICKET", buy(winners.getFirst()).status());
    assertEquals(new Core.Snapshot(200, 200, 0, 200), core.snapshot(event));
  }

  /** 同一已核实回执并发重放，票身份和可恢复票码必须相同。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void duplicatePaymentCreatesOneTicket() throws Exception {
    String booking = hold(seats.getFirst()), receipt = id();
    var results = race(16, () -> core.verifiedPayment(booking, receipt, 2000, "CNY"));
    assertTrue(results.stream().allMatch(x -> x.equals(results.getFirst())));
    assertEquals(new Core.Snapshot(1, 1, 0, 1), core.snapshot(event));
  }

  /** 不同检票员同时核销一票，只记一次成功且其余均已用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void concurrentScanAdmitsOnce() throws Exception {
    var ticket = buy(hold(seats.getFirst()));
    var results = race(24, () -> core.checkIn(event, "QA-GATE-" + id(), ticket.code()));
    assertEquals(1, results.stream().filter("ADMITTED"::equals).count());
    assertEquals(23, results.stream().filter("ALREADY_USED"::equals).count());
    assertEquals(new Core.Snapshot(1, 1, 1, 1), core.snapshot(event));
  }

  /** 申请退款即作废票，确认前不能转售，确认后旧码也不可复用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void refundVoidsBeforeReleaseAndResale() {
    String b = hold(seats.getFirst());
    var ticket = buy(b);
    core.requestRefund(owner, b);
    assertEquals("VOID", core.checkIn(event, "QA-GATE", ticket.code()));
    denied("SEAT_UNAVAILABLE", () -> hold(seats.getFirst()));
    String refund = id();
    core.confirmedRefund(b, refund, 2000, "CNY");
    core.confirmedRefund(b, refund, 2000, "CNY");
    assertEquals(new Core.Snapshot(0, 1, 0, 2), core.snapshot(event));
    var replacement = buy(hold(seats.getFirst()));
    assertNotEquals(ticket.code(), replacement.code());
    assertEquals("VOID", core.checkIn(event, "QA-GATE", ticket.code()));
    assertEquals("ADMITTED", core.checkIn(event, "QA-GATE", replacement.code()));
  }

  /** 已入场不能走首版未核销退票规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void checkedInTicketCannotRefund() {
    String b = hold(seats.getFirst());
    var t = buy(b);
    assertEquals("ADMITTED", core.checkIn(event, "QA-GATE", t.code()));
    denied("REFUND_NOT_ALLOWED", () -> core.requestRefund(owner, b));
    assertEquals(1, core.snapshot(event).occupied());
  }

  /** 过期座位已有新预留，迟到付款只产生待退款记录，不换掉占用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void latePaymentCannotTakeReassignedSeat() {
    String old = hold(seats.getFirst());
    time.advance(35 * 60 * 1000L);
    String next = core.reserve("QA-NEW-OWNER", seats.getFirst(), id());
    var late = buy(old);
    assertEquals("REFUND_REQUIRED", late.status());
    assertNull(late.code());
    assertEquals("TICKET", buy(next).status());
    assertEquals(new Core.Snapshot(1, 1, 0, 2), core.snapshot(event));
  }

  /** 到期时还未转售的付款也不能出票；随后可重新预留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void expirationBoundaryRefusesIssuance() {
    String b = hold(seats.getFirst());
    time.advance(35 * 60 * 1000L);
    assertEquals("REFUND_REQUIRED", buy(b).status());
    assertEquals(0, core.snapshot(event).occupied());
    assertNotEquals(b, hold(seats.getFirst()));
  }

  /** 同一请求不能改本人或座位，但相同请求可恢复原订单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void reservationReplayIsBoundToOwnerAndSeat() {
    String req = id();
    assertEquals(req, core.reserve(owner, seats.getFirst(), req));
    assertEquals(req, core.reserve(owner, seats.getFirst(), req));
    denied("REQUEST_CONFLICT", () -> core.reserve("QA-OTHER", seats.getFirst(), req));
    denied("REQUEST_CONFLICT", () -> core.reserve(owner, seats.get(1), req));
    assertEquals(1, core.snapshot(event).occupied());
  }

  /** 不匹配金额、币种和退款金额不能改变资金或票状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void amountAndCurrencyMustMatchFrozenOrder() {
    String b = hold(seats.getFirst());
    denied("PAYMENT_MISMATCH", () -> core.verifiedPayment(b, id(), 1999, "CNY"));
    denied("PAYMENT_MISMATCH", () -> core.verifiedPayment(b, id(), 2000, "USD"));
    denied("PAYMENT_MISMATCH", () -> core.verifiedPayment(b, id(), 2000, null));
    assertEquals(new Core.Snapshot(1, 0, 0, 0), core.snapshot(event));
    buy(b);
    core.requestRefund(owner, b);
    denied("REFUND_MISMATCH", () -> core.confirmedRefund(b, id(), 2001, "CNY"));
    assertEquals(1, core.snapshot(event).occupied());
  }

  /** 回执复用于另一单被拒绝，第二次独立付款不多发票。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void receiptCannotBeMovedAndExtraChargeNeedsRefund() {
    String a = hold(seats.getFirst()), b = hold(seats.get(1)), ref = id();
    core.verifiedPayment(a, ref, 2000, "CNY");
    denied("RECEIPT_CONFLICT", () -> core.verifiedPayment(b, ref, 2000, "CNY"));
    assertEquals("REFUND_REQUIRED", buy(a).status());
    assertEquals(new Core.Snapshot(2, 1, 0, 2), core.snapshot(event));
  }

  /** 非本人拒绝退票；伪造票码和跨活动检票拒绝，原票仍可核销。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void wrongOwnerEventAndForgedCodeAreRejected() {
    String b = hold(seats.getFirst());
    var ticket = buy(b);
    denied("NOT_OWNER", () -> core.requestRefund("QA-OTHER", b));
    assertEquals("INVALID_CODE", core.checkIn(event, "QA-GATE", "td1." + id() + ".fake"));
    assertEquals("WRONG_EVENT", core.checkIn(id(), "QA-GATE", ticket.code()));
    assertEquals("ADMITTED", core.checkIn(event, "QA-GATE", ticket.code()));
  }

  /** 退票后重放原付款回执只返回VOID，不能恢复票有效性。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void originalPaymentReplayCannotReviveVoidedTicket() {
    String b = hold(seats.getFirst()), ref = id();
    core.verifiedPayment(b, ref, 2000, "CNY");
    core.requestRefund(owner, b);
    assertEquals("VOID", core.verifiedPayment(b, ref, 2000, "CNY").status());
    assertEquals(1, core.snapshot(event).tickets());
  }

  /** 退票与入场并发只能有一个生效，不能既放行又退款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Test
  void concurrentRefundAndScanAreExclusive() throws Exception {
    for (int index = 0; index < 20; index++) {
      String booking = hold(seats.get(index));
      var ticket = buy(booking);
      AtomicLong operation = new AtomicLong();
      var results =
          race(
              2,
              () -> {
                if (operation.getAndIncrement() == 0)
                  return core.checkIn(event, "QA-GATE", ticket.code());
                try {
                  core.requestRefund(owner, booking);
                  return "REFUND_PENDING";
                } catch (Refusal refusal) {
                  assertEquals("REFUND_NOT_ALLOWED", refusal.getMessage());
                  return refusal.getMessage();
                }
              });
      if (results.contains("ADMITTED")) {
        assertEquals(Set.of("ADMITTED", "REFUND_NOT_ALLOWED"), new HashSet<>(results));
        assertEquals("ALREADY_USED", core.checkIn(event, "QA-GATE", ticket.code()));
      } else {
        assertEquals(Set.of("VOID", "REFUND_PENDING"), new HashSet<>(results));
        core.confirmedRefund(booking, id(), 2000, "CNY");
        assertEquals("VOID", core.checkIn(event, "QA-GATE", ticket.code()));
      }
    }
  }

  private <T> List<T> race(int tasks, Callable<T> call) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(12);
    try {
      List<Future<T>> futures = new ArrayList<>();
      for (int i = 0; i < tasks; i++)
        futures.add(
            pool.submit(
                () -> {
                  assertTrue(start.await(10, TimeUnit.SECONDS));
                  return call.call();
                }));
      start.countDown();
      List<T> values = new ArrayList<>();
      for (Future<T> f : futures) values.add(f.get(60, TimeUnit.SECONDS));
      return values;
    } finally {
      start.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }
}
