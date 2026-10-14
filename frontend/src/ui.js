// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref } from "vue";
export const language = ref("zh");
/** 显示语言不改变业务数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const t = (zh, en) => (language.value === "zh" ? zh : en);
/** 读取业务中英文字段，缺译文时保留原文。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const localized = (r, key = "name") =>
  language.value === "en" ? r[key + "En"] || r[key] : r[key];
/** 整数最小货币单位按指定币种显示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const money = (n, c = "CNY") =>
  new Intl.NumberFormat(language.value === "zh" ? "zh-CN" : "en", {
    style: "currency",
    currency: c,
  }).format(Number(n || 0) / 100);
/** 活动按场地时区显示，普通审计按浏览器时区显示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const dateTime = (v, zone) =>
  v
    ? new Intl.DateTimeFormat(language.value === "zh" ? "zh-CN" : "en", {
        dateStyle: "medium",
        timeStyle: "short",
        ...(zone ? { timeZone: zone } : {}),
      }).format(new Date(v)) + (zone ? " [" + zone + "]" : "")
    : "—";
/** 编辑副本不直接修改已读取的记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const cloneRecord = (r) => JSON.parse(JSON.stringify(r));
/** 输入金额严格转换为整数分，拒绝无声舍入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function minor(value) {
  const s = String(value);
  if (!/^\d{1,7}(\.\d{1,2})?$/.test(s)) throw Error("INVALID_INPUT");
  const [a, b = ""] = s.split(".");
  const n = Number(a) * 100 + Number(b.padEnd(2, "0"));
  if (n > 100000000) throw Error("AMOUNT_LIMIT");
  return n;
}
/** 订单、票和待办状态的人可读标签。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const stateName = (code) =>
  ({
    DRAFT: t("草稿", "Draft"),
    PUBLISHED: t("已发布", "Published"),
    CLOSED: t("停售", "Sales closed"),
    HELD: t("待付款", "Awaiting payment"),
    PAID: t("已出票", "Issued"),
    EXPIRED: t("已关闭", "Closed"),
    REFUND_PENDING: t("退款处理中", "Refund pending"),
    REFUNDED: t("已退款", "Refunded"),
    ISSUED: t("待入场", "Valid"),
    CHECKED_IN: t("已入场", "Checked in"),
    VOID: t("已作废", "Void"),
    PENDING: t("待处理", "Pending"),
    ERROR: t("待重试", "Retry required"),
    FAILED: t("需人工核对", "Review required"),
    DONE: t("完成", "Completed"),
    SENT: t("已发送", "Sent"),
    DISABLED: t("未配置邮件", "Email not configured"),
  })[code] || code;
export const confirmation = ref(null);
/** 应用内确认，不触发系统密码或保存弹窗。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function ask(message) {
  if (confirmation.value) return Promise.resolve(false);
  return new Promise((resolve) => {
    confirmation.value = { message, resolve };
  });
}
const errors = {
  UNAUTHENTICATED: ["请重新登录。", "Please sign in again."],
  FORBIDDEN: ["没有操作权限。", "Permission denied."],
  OUT_OF_SCOPE: ["不在当前账号授权范围内。", "Outside your authorized scope."],
  NOT_OWNER: ["只能操作本人订单。", "Only your own order is permitted."],
  LOGIN_FAILED: ["账号或密码不正确。", "Invalid username or password."],
  LOGIN_THROTTLED: [
    "尝试过多，请稍后再试。",
    "Too many attempts; retry later.",
  ],
  INVALID_INPUT: ["请核对必填项与格式。", "Check required fields and formats."],
  DATE_INVALID: [
    "请核对售票、活动和退票时间。",
    "Check sales, event and refund times.",
  ],
  PASSWORD_WEAK: [
    "密码须12–72字节，含大小写字母和数字。",
    "Use 12–72 bytes with upper/lowercase and a digit.",
  ],
  EMAIL_INVALID: ["请输入有效邮箱格式。", "Enter a valid email format."],
  USERNAME_UNAVAILABLE: ["此账号不可用，请更换。", "Username unavailable."],
  SIGNUP_CLOSED: [
    "注册暂未开放，请联系主办方。",
    "Registration closed; contact the organizer.",
  ],
  VERSION_CONFLICT: [
    "资料已更新，请刷新后再修改。",
    "Record changed; refresh before editing.",
  ],
  CONFLICT: [
    "记录重复或仍被引用，请刷新核对。",
    "Duplicate or referenced record; refresh and verify.",
  ],
  TICKET_TYPE_REQUIRED: [
    "请先添加票种和座位。",
    "Add a ticket type and seats first.",
  ],
  TICKET_LAYOUT_LOCKED: [
    "已有订单或已发布，不能改座位与票价。",
    "Seats and prices are locked after publication or orders.",
  ],
  EVENT_RULES_LOCKED: [
    "已有订单，不能改币种和活动时间规则。",
    "Currency and time rules are locked by order history.",
  ],
  STAFF_INVALID: [
    "工作人员必须启用且属于本组织。",
    "Staff must be active and in this organization.",
  ],
  SALE_CLOSED: [
    "尚未开售或已停售，请刷新活动。",
    "Sales not open or closed; refresh the event.",
  ],
  SEAT_UNAVAILABLE: [
    "此座位已被预留，请重新选择。",
    "Seat reserved; choose another.",
  ],
  ORDER_EXPIRED: [
    "订单已关闭或过期，请重新选座。",
    "Order closed or expired; reserve again.",
  ],
  PAYMENT_DISABLED: [
    "当前环境不能执行此付款操作。",
    "Payment action unavailable in this mode.",
  ],
  PAYMENT_UNAVAILABLE: [
    "支付服务暂不可用，结果可能未知。请在票夹核对后重试。",
    "Payment service unavailable; outcome may be unknown. Verify your wallet before retrying.",
  ],
  CHECKOUT_WINDOW_CLOSED: [
    "付款窗口已关闭，请取消预留后重新选座。",
    "Checkout window closed; cancel the hold and reserve again.",
  ],
  REFUND_WINDOW_CLOSED: ["已超过退票截止时间。", "Refund deadline has passed."],
  REFUND_NOT_ALLOWED: [
    "当前票状态不能退票，可能已经入场。",
    "Refund unavailable; ticket may already be checked in.",
  ],
  ENTRY_CLOSED: [
    "不在此活动的检票时间内。",
    "Outside this event’s check-in window.",
  ],
  SYSTEM_MENU_REQUIRED: [
    "账号、角色和设置菜单须保留启用。",
    "Keep the account, role and settings menus enabled.",
  ],
  TIMEZONE_LOCKED: [
    "已有活动，组织时区不能改换。",
    "Organization timezone is locked after its first event.",
  ],
  LAST_ADMIN: [
    "必须保留至少一个完整管理员。",
    "Keep at least one full administrator.",
  ],
  DEPARTMENT_DISABLED: ["组织已停用。", "Organization disabled."],
  CATEGORY_DISABLED: ["活动类别已停用。", "Category disabled."],
  RESOURCE_LIMIT: [
    "超过当前配置的容量上限。",
    "Configured resource limit exceeded.",
  ],
  STATE_INVALID: [
    "当前状态不能执行该操作。",
    "Action unavailable in this state.",
  ],
  REQUEST_CONFLICT: [
    "请求身份已用于另一项预留，请刷新核对。",
    "Reservation request conflicts; refresh and verify.",
  ],
  RESULT_UNKNOWN: [
    "连接中断，结果未知。请刷新核对后再操作。",
    "Connection lost; result unknown. Refresh and verify before retrying.",
  ],
  NETWORK_ERROR: [
    "无法连接服务器，请稍后刷新。",
    "Server unavailable; retry later.",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确。", "Original password invalid."],
};
/** 网络未知及业务拒绝的明确提示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const errorMessage = (e) =>
  errors[e.message]
    ? t(...errors[e.message])
    : t("操作未完成", "Action failed") + " (" + e.message + ")";
/** 票种内排序真实座位，不以展示顺序当库存依据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const typeSeats = (seats, type) =>
  seats
    .filter((s) => s.label.startsWith(type.prefix))
    .sort(
      (a, b) =>
        Number(a.label.slice(type.prefix.length)) -
        Number(b.label.slice(type.prefix.length)),
    );

/** 将持久化核销及任务代码译成工作人员可理解的结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const decisionName = (s) =>
  ({
    ADMITTED: t("已放行", "Admitted"),
    ALREADY_USED: t("已使用，拒绝重复", "Already used — denied"),
    VOID: t("已作废，拒绝", "Void — denied"),
    WRONG_EVENT: t("活动不符", "Wrong event"),
    INVALID_CODE: t("无效票码", "Invalid ticket"),
  })[s] || s;
/** 队列问题说明，避免工作人员误认为已退款或已投递。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const taskError = (s) =>
  ({
    MAIL_NOT_CONFIGURED: t(
      "邮件服务未配置，未发送",
      "Email not configured; not sent",
    ),
    MAIL_DELIVERY_FAILED: t(
      "邮件投递失败，可重试",
      "Email delivery failed; retry available",
    ),
    PAYMENT_UNAVAILABLE: t(
      "支付平台暂不可用，请核对后重试",
      "Payment service unavailable; verify and retry",
    ),
    PROVIDER_REFUND_FAILED: t(
      "支付平台确认退款失败，需核对后重试",
      "Provider refund failed; review before retry",
    ),
    TASK_TRANSACTION_FAILED: t(
      "处理未提交，可重试",
      "Processing did not commit; retry available",
    ),
  })[s] ||
  s ||
  "—";

/** 生成规范预留请求ID，局域网HTTP也不依赖randomUUID安全上下文。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function requestId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join(
    "",
  );
  return (
    hex.slice(0, 8) +
    "-" +
    hex.slice(8, 12) +
    "-" +
    hex.slice(12, 16) +
    "-" +
    hex.slice(16, 20) +
    "-" +
    hex.slice(20)
  );
}
