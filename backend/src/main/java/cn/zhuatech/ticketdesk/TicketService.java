// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 活动权限、目录和观众订单；票码只返回本人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class TicketService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final Core core;
  final TicketCodes codes;
  final Clock clock;
  @PersistenceContext EntityManager em;

  public TicketService(
      Store db,
      AccessService access,
      AdminService admin,
      Core core,
      TicketCodes codes,
      Clock clock) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.core = core;
    this.codes = codes;
    this.clock = clock;
  }

  /** 公共只返回已发布的活动资料，无工作人员或观众身份。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> publicEvents() {
    return db.all(TicketEvent.class).stream()
        .filter(
            e -> e.state.equals("PUBLISHED") && db.get(Department.class, e.departmentId).enabled)
        .map(this::publicRow)
        .toList();
  }

  /** 活动详情和逐座即时可选状态；不输出持有人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> publicEvent(String id) {
    var e = event(id, false);
    Rules.check(
        e.state.equals("PUBLISHED") && db.get(Department.class, e.departmentId).enabled,
        "EVENT_UNAVAILABLE");
    var out = publicRow(e);
    out.put("seats", seatRows(e.id));
    return out;
  }

  /** 当前岗位只看允许的活动；管理员可管理全部组织。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> catalog() {
    if (Collections.disjoint(
        access.role().permissions,
        Set.of("events", "sales", "refunds", "checkin", "reports", "audit")))
      throw new Problem(403, "FORBIDDEN");
    var a = access.current();
    var all = access.role().scope.equals("ALL");
    var out = new LinkedHashMap<String, Object>();
    out.put(
        "events",
        db.all(TicketEvent.class).stream()
            .filter(e -> all || (access.department(e.departmentId) && e.staff.contains(a.id)))
            .map(
                e -> {
                  var m = publicRow(e);
                  m.put("departmentId", e.departmentId);
                  m.put("staff", e.staff);
                  m.put("version", e.version);
                  return m;
                })
            .toList());
    out.put("categories", db.all(DictionaryEntry.class));
    if (access.has("events")) {
      out.put(
          "departments",
          db.all(Department.class).stream().filter(d -> access.department(d.id)).toList());
      out.put(
          "staffOptions",
          db.all(Account.class).stream()
              .filter(
                  u ->
                      u.enabled
                          && access.department(u.departmentId)
                          && !db.get(AccessRole.class, u.roleId).scope.equals("CUSTOMER"))
              .map(u -> Map.of("id", u.id, "name", u.displayName, "departmentId", u.departmentId))
              .toList());
    }
    return out;
  }

  /** 活动完整保存与显式版本保护；发布须有票种，已售后时间及币种不可改换。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> saveEvent(String id, Map<String, Object> b) {
    access.require("events");
    admin.lock();
    TicketEvent e = id == null ? new TicketEvent() : authorized(id, "events", true);
    boolean fresh = id == null;
    if (fresh) {
      e.id = UUID.randomUUID().toString();
      e.departmentId = Rules.id(b.get("departmentId"));
      Rules.check(access.department(e.departmentId), "OUT_OF_SCOPE");
    } else Rules.check(e.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
    Rules.check(db.get(Department.class, e.departmentId).enabled, "DEPARTMENT_DISABLED");
    String currency = Rules.text(b.get("currency"), 3, true);
    Rules.check(Set.of("CNY", "USD", "EUR", "GBP", "HKD").contains(currency), "CURRENCY_INVALID");
    long start = epoch(b.get("startsAt")),
        saleStart = epoch(b.get("saleStartsAt")),
        saleEnd = epoch(b.get("saleEndsAt")),
        refundEnd = epoch(b.get("refundEndsAt"));
    Rules.check(saleStart < saleEnd && saleEnd <= start && refundEnd <= start, "DATE_INVALID");
    boolean history = !fresh && !bookings(e.id).isEmpty();
    if (history)
      Rules.check(
          e.currency.equals(currency)
              && e.startsAt == start
              && e.saleStartsAt == saleStart
              && e.saleEndsAt == saleEnd
              && e.refundEndsAt == refundEnd,
          "EVENT_RULES_LOCKED");
    e.name = Rules.text(b.get("name"), 120, true);
    e.nameEn = Rules.text(b.get("nameEn"), 120, true);
    e.venue = Rules.text(b.get("venue"), 120, true);
    e.venueEn = Rules.text(b.get("venueEn"), 120, true);
    e.description = Rules.paragraph(b.get("description"), 3000, false);
    e.descriptionEn = Rules.paragraph(b.get("descriptionEn"), 3000, false);
    String category = Rules.text(b.get("category"), 20, true);
    if (fresh || !Objects.equals(e.category, category))
      Rules.check(
          db.all(DictionaryEntry.class).stream()
              .anyMatch(x -> x.enabled && x.code.equals(category)),
          "CATEGORY_DISABLED");
    e.category = category;
    e.currency = currency;
    e.startsAt = start;
    e.saleStartsAt = saleStart;
    e.saleEndsAt = saleEnd;
    e.refundEndsAt = refundEnd;
    String state = Rules.text(b.get("state"), 20, true);
    Rules.check(Set.of("DRAFT", "PUBLISHED", "CLOSED").contains(state), "INVALID_INPUT");
    if (history) Rules.check(!state.equals("DRAFT"), "STATE_INVALID");
    if (state.equals("PUBLISHED")) {
      Rules.check(!types(e.id).isEmpty(), "TICKET_TYPE_REQUIRED");
      Rules.check(clock.millis() < e.saleEndsAt, "SALE_CLOSED");
    }
    e.state = state;
    Rules.check(b.get("staff") instanceof List<?>, "INVALID_INPUT");
    var priorStaff = new HashSet<>(e.staff);
    e.staff.clear();
    for (Object x : (List<?>) b.get("staff")) {
      var u = db.get(Account.class, Rules.id(x));
      boolean eligible =
          u.enabled
              && u.departmentId.equals(e.departmentId)
              && !db.get(AccessRole.class, u.roleId).scope.equals("CUSTOMER");
      if (!eligible) {
        Rules.check(priorStaff.contains(u.id), "STAFF_INVALID");
        continue;
      }
      e.staff.add(u.id);
    }
    if (!access.role().scope.equals("ALL")) e.staff.add(access.current().id);
    if (fresh) db.save(e);
    else e.version++;
    access.audit("EVENT_SAVE", e.id, e.departmentId);
    return Map.of("id", e.id);
  }

  /** 删除未销售草稿，任何订单历史都会保护活动。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteEvent(String id) {
    var e = authorized(id, "events", true);
    Rules.check(e.state.equals("DRAFT") && bookings(id).isEmpty(), "STATE_INVALID");
    for (var s : seats(id)) em.remove(s);
    for (var t : types(id)) em.remove(t);
    db.delete(e);
    access.audit("EVENT_DELETE", id, e.departmentId);
  }

  /** 票种维护生成座位图；有订单后票价和布局锁定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void saveType(String eventId, Long id, Map<String, Object> b) {
    var e = authorized(eventId, "events", true);
    Rules.check(e.state.equals("DRAFT") && bookings(eventId).isEmpty(), "TICKET_LAYOUT_LOCKED");
    var type = id == null ? new TicketType() : db.get(TicketType.class, id);
    if (id != null) {
      Rules.check(type.eventId.equals(eventId), "OUT_OF_SCOPE");
      Rules.check(type.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
      for (var s : seats(eventId).stream().filter(x -> x.label.startsWith(type.prefix)).toList())
        em.remove(s);
      em.flush();
    }
    String prefix = Rules.text(b.get("prefix"), 5, true);
    Rules.check(prefix.matches("[A-Z]{1,4}-"), "INVALID_INPUT");
    Rules.check(
        types(eventId).stream()
            .noneMatch(t -> !Objects.equals(t.id, id) && t.prefix.equals(prefix)),
        "CONFLICT");
    int capacity = Rules.integer(b.get("capacity"), 1, 500);
    int total =
        capacity
            + types(eventId).stream()
                .filter(t -> !Objects.equals(t.id, id))
                .mapToInt(t -> t.capacity)
                .sum();
    Rules.check(total <= admin.setting("eventSeatLimit"), "RESOURCE_LIMIT");
    type.eventId = eventId;
    type.prefix = prefix;
    type.name = Rules.text(b.get("name"), 60, true);
    type.nameEn = Rules.text(b.get("nameEn"), 60, true);
    type.price = Rules.integer(b.get("price"), 0, 100000000);
    type.capacity = capacity;
    type.columns = Rules.integer(b.get("columns"), 1, 25);
    if (id == null) db.save(type);
    else type.version++;
    core.createSeats(eventId, capacity, type.price, e.currency, prefix);
    access.audit("TYPE_SAVE", type.id, e.departmentId);
  }

  /** 清除未销售草稿票种，同时清理该类座位。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteType(String eventId, Long id) {
    var e = authorized(eventId, "events", true);
    Rules.check(e.state.equals("DRAFT") && bookings(eventId).isEmpty(), "TICKET_LAYOUT_LOCKED");
    var t = db.get(TicketType.class, id);
    Rules.check(t.eventId.equals(eventId), "OUT_OF_SCOPE");
    for (var s : seats(eventId).stream().filter(x -> x.label.startsWith(t.prefix)).toList())
      em.remove(s);
    db.delete(t);
    access.audit("TYPE_DELETE", id, e.departmentId);
  }

  /** 服务端限制销售时间及本人身份，冻结单座订单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> reserve(String eventId, long seatId, String request) {
    access.require("portal");
    var e = event(eventId, true);
    Rules.check(
        e.state.equals("PUBLISHED")
            && db.get(Department.class, e.departmentId).enabled
            && clock.millis() >= e.saleStartsAt
            && clock.millis() < e.saleEndsAt,
        "SALE_CLOSED");
    var s = seat(seatId);
    Rules.check(s.eventId.equals(e.id), "OUT_OF_SCOPE");
    String id = core.reserve(owner(), seatId, request);
    return order(id, true);
  }

  /** 本人票夹，不信任请求体中的持有人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> mine() {
    access.require("portal");
    return db
        .query(Booking.class, "from Booking where ownerId=?1 order by expiresAt desc", owner())
        .stream()
        .map(b -> order(b.id, true))
        .toList();
  }

  /** 单个本人订单恢复及票码；后台无票码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> ownOrder(String id) {
    access.require("portal");
    var b = booking(id, false);
    Rules.check(b.ownerId.equals(owner()), "NOT_OWNER");
    return order(id, true);
  }

  /** 本人关闭预留；即使网关晚到也不会恢复已转售座位。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void cancel(String id) {
    access.require("portal");
    var b = booking(id, false);
    Rules.check(b.ownerId.equals(owner()), "NOT_OWNER");
    core.cancelHold(owner(), id);
    access.audit("HOLD_CANCEL", id, event(seat(b.seatId).eventId, false).departmentId);
  }

  /** 授权工作人员在线检票；返回明确决策但不暴露票码或观众密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, String> checkIn(String eventId, String code) {
    var e = authorized(eventId, "checkin", false);
    Rules.check(
        !e.state.equals("DRAFT")
            && clock.millis() >= e.startsAt - 12 * 3600000L
            && clock.millis() <= e.startsAt + 24 * 3600000L,
        "ENTRY_CLOSED");
    return Map.of("decision", core.checkIn(e.id, access.current().username, code));
  }

  /** 退款先停用票，由任务确认到账后释放，不能绕过截止时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public RefundTask refund(String id, boolean staff) {
    var b = booking(id, false);
    var e = event(seat(b.seatId).eventId, false);
    if (staff) authorized(e.id, "refunds", false);
    else {
      access.require("portal");
      Rules.check(b.ownerId.equals(owner()), "NOT_OWNER");
    }
    Rules.check(clock.millis() < e.refundEndsAt, "REFUND_WINDOW_CLOSED");
    core.requestRefund(b.ownerId, b.id);
    var c = em.find(Checkout.class, b.id);
    Rules.check(c != null && c.paymentIntent != null, "PAYMENT_NOT_FOUND");
    var task = createRefund(b, c.paymentIntent, c.mode, true);
    access.audit("REFUND_REQUEST", id, e.departmentId);
    return task;
  }

  /** 资金异常记录绑定原支付，不重复创建待退款任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public RefundTask createRefund(Booking b, String intent, String mode, boolean release) {
    var existing = db.query(RefundTask.class, "from RefundTask where paymentIntent=?1", intent);
    if (!existing.isEmpty()) return existing.getFirst();
    var t = new RefundTask();
    t.id = UUID.randomUUID().toString();
    t.bookingId = b.id;
    t.paymentIntent = intent;
    t.amount = b.price;
    t.currency = b.currency;
    t.mode = mode;
    t.releasesSeat = release;
    t.nextAttempt = clock.millis();
    db.save(t);
    return t;
  }

  /** 首次出票安排本人票夹通知，重复付款不重复排队。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void queueMail(Booking b) {
    String id = stable("mail:" + b.id);
    if (em.find(MailTask.class, id) != null) return;
    var a = db.get(Account.class, Long.valueOf(b.ownerId));
    if (a.email == null || a.email.isBlank()) return;
    var t = new MailTask();
    t.id = id;
    t.bookingId = b.id;
    t.recipient = a.email;
    t.nextAttempt = clock.millis();
    db.save(t);
  }

  /** 后台订单、审计、核销及任务按组织和活动过滤，不返回票码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> operations(String eventId, String permission) {
    var e = authorized(eventId, permission, false);
    var out = new LinkedHashMap<String, Object>();
    out.put("orders", bookings(eventId).stream().map(b -> order(b.id, false)).toList());
    out.put("scans", db.query(Scan.class, "from Scan where eventId=?1 order by id desc", eventId));
    out.put(
        "refunds",
        db.all(RefundTask.class).stream()
            .filter(t -> seat(booking(t.bookingId, false).seatId).eventId.equals(eventId))
            .toList());
    out.put(
        "mail",
        db.all(MailTask.class).stream()
            .filter(t -> seat(booking(t.bookingId, false).seatId).eventId.equals(eventId))
            .map(
                t ->
                    Map.of(
                        "id",
                        t.id,
                        "bookingId",
                        t.bookingId,
                        "state",
                        t.state,
                        "attempts",
                        t.attempts,
                        "error",
                        t.error))
            .toList());
    out.put("summary", core.snapshot(e.id));
    return out;
  }

  /** 主办查看实际生成的座位，不以静态图代替库存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> layout(String id) {
    var e = authorized(id, "events", false);
    return Map.of("seats", seatRows(e.id));
  }

  /** 按币种分开统计，不跨币种相加；CSV防公式注入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> reports() {
    access.require("reports");
    var out = new ArrayList<Map<String, Object>>();
    for (var e : db.all(TicketEvent.class)) {
      if (!allowed(e)) continue;
      var groups = new LinkedHashMap<String, List<Booking>>();
      for (var b : bookings(e.id)) {
        var c = em.find(Checkout.class, b.id);
        groups.computeIfAbsent(c == null ? "UNPAID" : c.mode, k -> new ArrayList<>()).add(b);
      }
      if (groups.isEmpty()) groups.put("UNPAID", List.of());
      for (var group : groups.entrySet()) {
        long issued = 0, checked = 0, total = 0, refunds = 0;
        for (var b : group.getValue()) {
          var ts = db.query(Ticket.class, "from Ticket where bookingId=?1", b.id);
          if (!ts.isEmpty()) {
            issued++;
            if (ts.getFirst().state.equals("CHECKED_IN")) checked++;
          }
          for (var r : db.query(Receipt.class, "from Receipt where bookingId=?1", b.id)) {
            if (r.kind.equals("PAYMENT")) total += r.amount;
            if (r.kind.equals("REFUND")) refunds += r.amount;
          }
        }
        var row = new LinkedHashMap<String, Object>();
        row.put("id", e.id);
        row.put("name", e.name);
        row.put("nameEn", e.nameEn);
        row.put("mode", group.getKey());
        row.put("currency", e.currency);
        row.put("issued", issued);
        row.put("checked", checked);
        row.put("gross", total);
        row.put("refunded", refunds);
        row.put("net", total - refunds);
        out.add(row);
      }
    }
    return out;
  }

  /** 读取允许的审计范围，字段无密码或票码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(x -> access.department(x.departmentId))
        .sorted(Comparator.comparing((AuditEvent x) -> x.id).reversed())
        .limit(500)
        .toList();
  }

  /** 重试原任务而不改变退款幂等键，不创建新收费。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void retry(String kind, String id) {
    if (kind.equals("refund")) {
      var t = em.find(RefundTask.class, id, LockModeType.PESSIMISTIC_WRITE);
      Rules.check(t != null, "NOT_FOUND");
      authorized(seat(booking(t.bookingId, false).seatId).eventId, "refunds", false);
      Rules.check(!t.state.equals("DONE"), "STATE_INVALID");
      if (t.state.equals("FAILED")) {
        t.generation++;
        t.refundId = null;
      }
      t.state = "PENDING";
      t.nextAttempt = clock.millis();
    } else if (kind.equals("mail")) {
      var t = em.find(MailTask.class, id, LockModeType.PESSIMISTIC_WRITE);
      Rules.check(t != null, "NOT_FOUND");
      authorized(seat(booking(t.bookingId, false).seatId).eventId, "refunds", false);
      Rules.check(!t.state.equals("SENT"), "STATE_INVALID");
      t.state = "PENDING";
      t.nextAttempt = clock.millis();
    } else throw new Problem(404, "NOT_FOUND");
  }

  /** 内部订单视图只由本人/授权后台入口调用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> order(String id, boolean privateTicket) {
    var b = booking(id, false);
    var s = seat(b.seatId);
    var e = event(s.eventId, false);
    var result = new LinkedHashMap<String, Object>();
    result.put("id", b.id);
    result.put("eventId", e.id);
    result.put("eventName", e.name);
    result.put("eventNameEn", e.nameEn);
    result.put("venue", e.venue);
    result.put("venueEn", e.venueEn);
    result.put("startsAt", e.startsAt);
    result.put("zone", db.get(Department.class, e.departmentId).zone);
    result.put("seat", s.label);
    result.put("price", b.price);
    result.put("currency", b.currency);
    result.put("expiresAt", b.expiresAt);
    result.put(
        "state", b.state.equals("HELD") && b.expiresAt <= clock.millis() ? "EXPIRED" : b.state);
    result.put("refundEndsAt", e.refundEndsAt);
    var ts = db.query(Ticket.class, "from Ticket where bookingId=?1", id);
    if (!ts.isEmpty()) {
      var t = ts.getFirst();
      result.put("ticketId", t.id);
      result.put("ticketState", t.state);
      if (privateTicket) result.put("code", codes.encode(t.id));
    }
    var c = em.find(Checkout.class, id);
    result.put("paymentMode", c == null ? null : c.mode);
    if (!privateTicket) {
      var a = db.get(Account.class, Long.valueOf(b.ownerId));
      result.put("buyer", a.displayName);
      result.put("username", a.username);
    }
    return result;
  }

  /** 活动授权会实时读取账户和组织，不信任前端菜单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public TicketEvent authorized(String id, String permission, boolean lock) {
    access.require(permission);
    var e = event(id, lock);
    if (!allowed(e) || !db.get(Department.class, e.departmentId).enabled)
      throw new Problem(403, "OUT_OF_SCOPE");
    return e;
  }

  private boolean allowed(TicketEvent e) {
    return access.role().scope.equals("ALL")
        || (access.department(e.departmentId) && e.staff.contains(access.current().id));
  }

  TicketEvent event(String id, boolean lock) {
    Rules.check(id != null && id.matches("[a-f0-9-]{36}"), "INVALID_INPUT");
    var e =
        em.find(TicketEvent.class, id, lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
    if (e == null) throw new Problem(404, "NOT_FOUND");
    return e;
  }

  Booking booking(String id, boolean lock) {
    Rules.check(id != null && id.matches("[a-f0-9-]{36}"), "INVALID_INPUT");
    var b = em.find(Booking.class, id);
    if (b == null) throw new Problem(404, "NOT_FOUND");
    if (lock) {
      em.find(Seat.class, b.seatId, LockModeType.PESSIMISTIC_WRITE);
      em.refresh(b);
    }
    return b;
  }

  Seat seat(Long id) {
    var s = em.find(Seat.class, id);
    if (s == null) throw new Problem(404, "NOT_FOUND");
    return s;
  }

  List<Seat> seats(String event) {
    return db.query(Seat.class, "from Seat where eventId=?1 order by id", event);
  }

  List<TicketType> types(String event) {
    return db.query(TicketType.class, "from TicketType where eventId=?1 order by id", event);
  }

  List<Booking> bookings(String event) {
    return db.query(
        Booking.class,
        "select b from Booking b,Seat s where b.seatId=s.id and s.eventId=?1 order by b.expiresAt desc",
        event);
  }

  private String owner() {
    return access.current().id.toString();
  }

  private List<Map<String, Object>> seatRows(String id) {
    return seats(id).stream()
        .map(
            s -> {
              boolean available = s.currentBookingId == null;
              if (!available) {
                var b = booking(s.currentBookingId, false);
                available = b.state.equals("HELD") && b.expiresAt <= clock.millis();
              }
              return Map.<String, Object>of(
                  "id", s.id, "label", s.label, "price", s.price, "available", available);
            })
        .toList();
  }

  private Map<String, Object> publicRow(TicketEvent e) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", e.id);
    m.put("name", e.name);
    m.put("nameEn", e.nameEn);
    m.put("venue", e.venue);
    m.put("venueEn", e.venueEn);
    m.put("description", e.description);
    m.put("descriptionEn", e.descriptionEn);
    m.put("category", e.category);
    m.put("currency", e.currency);
    m.put("startsAt", e.startsAt);
    m.put("zone", db.get(Department.class, e.departmentId).zone);
    m.put("saleStartsAt", e.saleStartsAt);
    m.put("saleEndsAt", e.saleEndsAt);
    m.put("refundEndsAt", e.refundEndsAt);
    m.put("state", e.state);
    m.put("types", types(e.id));
    return m;
  }

  private long epoch(Object value) {
    try {
      return Instant.parse(Rules.text(value, 40, true)).toEpochMilli();
    } catch (Exception ex) {
      throw new Problem(400, "DATE_INVALID");
    }
  }

  static String stable(String value) {
    return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .toString();
  }
}
