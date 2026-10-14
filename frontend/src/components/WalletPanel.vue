<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from "vue";
import QRCode from "qrcode";
import { api } from "../api.js";
import { t, localized, dateTime, money, stateName, ask } from "../ui.js";
const props = defineProps({
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error", "events"]);
const rows = ref([]),
  selected = ref(null),
  qr = ref(""),
  filter = ref("ALL"),
  ready = ref(false),
  privateView = ref(false),
  page = ref(1),
  now = ref(Date.now());
let timer;
const filtered = computed(() =>
  rows.value.filter((x) => filter.value === "ALL" || x.state === filter.value),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * 15, page.value * 15),
);
watch(filter, () => (page.value = 1));
async function load() {
  ready.value = false;
  try {
    rows.value = await api("/wallet");
    if (!selected.value && rows.value.length) selected.value = rows.value[0];
    if (selected.value)
      selected.value =
        rows.value.find((x) => x.id === selected.value.id) || null;
  } catch (e) {
    emit("error", e);
  } finally {
    ready.value = true;
  }
}
async function open(row) {
  try {
    selected.value = await api("/wallet/" + row.id);
    if (window.matchMedia("(max-width:720px)").matches) {
      await nextTick();
      document
        .querySelector(".ticket-detail")
        ?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  } catch (e) {
    emit("error", e);
  }
}
watch(
  () => selected.value?.code,
  async (code) => {
    qr.value = "";
    if (code) {
      const image = await QRCode.toDataURL(code, {
        width: 280,
        margin: 2,
        errorCorrectionLevel: "M",
      });
      if (selected.value?.code === code) qr.value = image;
    }
  },
);
async function pay() {
  const result = await props.perform(
    "/wallet/" + selected.value.id + "/checkout",
  );
  if (!result) return;
  if (result.mode === "STRIPE") {
    location.assign(result.url);
    return;
  }
  if (
    result.mode === "DEMO" &&
    (await ask(
      t(
        "模拟付款不会收取费用，确认继续？",
        "No money will be collected. Simulate payment?",
      ),
    ))
  ) {
    await props.perform("/wallet/" + selected.value.id + "/demo-pay");
  }
  await load();
}
async function action(kind) {
  if (
    !(await ask(
      kind === "refund"
        ? t(
            "退票后此票立即作废，退款确认后才释放座位。继续？",
            "The ticket is void immediately; the seat is released after refund confirmation. Continue?",
          )
        : t(
            "关闭此订单并释放预留座位？",
            "Close this order and release the seat?",
          ),
    ))
  )
    return;
  const result = await props.perform(
    "/wallet/" + selected.value.id + "/" + kind,
  );
  if (result) await load();
}
const printTicket = () => window.print();
const remaining = computed(() =>
  selected.value
    ? Math.max(0, Math.ceil((selected.value.expiresAt - now.value) / 60000))
    : 0,
);
onMounted(async () => {
  await load();
  const id = new URLSearchParams(location.hash.split("?")[1] || "").get(
    "order",
  );
  if (id) {
    const row = rows.value.find((x) => x.id === id);
    if (row) await open(row);
  }
  timer = setInterval(() => {
    now.value = Date.now();
  }, 1000);
});
onUnmounted(() => clearInterval(timer));
</script>
<template>
  <section class="public-content">
    <header class="page-heading">
      <div>
        <h1>{{ t("我的票夹", "My tickets") }}</h1>
        <p class="muted">
          {{
            t(
              "订单、入场凭证与退款状态",
              "Orders, admission tickets and refunds",
            )
          }}
        </p>
      </div>
      <div class="actions">
        <button :aria-pressed="privateView" @click="privateView = !privateView">
          {{
            privateView
              ? t("显示票码", "Show ticket code")
              : t("隐私展示", "Privacy view")
          }}
        </button>
        <button @click="load" :disabled="pending">
          {{ t("刷新", "Refresh") }}</button
        ><button class="primary" @click="emit('events')">
          {{ t("选活动", "Find events") }}
        </button>
      </div>
    </header>
    <div class="wallet-layout" :class="{ hasTicket: !!selected }">
      <div class="panel">
        <div class="toolbar">
          <select v-model="filter" :aria-label="t('订单状态', 'Order status')">
            <option value="ALL">{{ t("全部状态", "All states") }}</option>
            <option
              v-for="s in [
                'HELD',
                'PAID',
                'REFUND_PENDING',
                'REFUNDED',
                'EXPIRED',
              ]"
              :key="s"
              :value="s"
            >
              {{ stateName(s) }}
            </option></select
          ><span class="muted"
            >{{ filtered.length }} {{ t("单", "orders") }}</span
          >
        </div>
        <div v-if="!ready" class="empty">{{ t("读取中…", "Loading…") }}</div>
        <div v-else-if="!filtered.length" class="empty">
          {{ t("暂无订单", "No orders yet") }}
        </div>
        <button
          v-for="r in visible"
          :key="r.id"
          class="wallet-row"
          :class="{ active: selected?.id === r.id }"
          @click="open(r)"
        >
          <div>
            <strong>{{ localized(r, "eventName") }}</strong>
            <p>{{ dateTime(r.startsAt, r.zone) }} · {{ r.seat }}</p>
            <small v-if="!privateView" class="mono">{{
              r.id.slice(0, 8)
            }}</small>
          </div>
          <div class="align-right">
            <span class="badge" :class="r.state">{{ stateName(r.state) }}</span>
            <p>{{ money(r.price, r.currency) }}</p>
          </div>
        </button>
        <footer v-if="filtered.length > 15" class="pagination">
          <button :disabled="page === 1" @click="page--">
            {{ t("上一页", "Previous") }}</button
          ><span>{{ page }}</span
          ><button :disabled="page * 15 >= filtered.length" @click="page++">
            {{ t("下一页", "Next") }}
          </button>
        </footer>
      </div>
      <article v-if="selected" class="ticket-detail panel">
        <div class="print-ticket">
          <p class="eyebrow">
            {{ t("入场凭证", "Admission ticket") }}
          </p>
          <h2>{{ localized(selected, "eventName") }}</h2>
          <p>
            {{ dateTime(selected.startsAt, selected.zone) }}<br />{{
              localized(selected, "venue")
            }}
          </p>
          <div class="ticket-seat">
            <span>{{ t("座位", "Seat") }}</span
            ><strong>{{ selected.seat }}</strong
            ><span
              class="badge"
              :class="selected.ticketState || selected.state"
              >{{ stateName(selected.ticketState || selected.state) }}</span
            >
          </div>
          <div
            v-if="qr && !privateView"
            class="ticket-qr"
            :class="{ void: selected.ticketState === 'VOID' }"
          >
            <img :src="qr" :alt="t('当前票二维码', 'Ticket QR code')" />
            <p v-if="selected.ticketState === 'VOID'" class="form-error">
              {{ t("此票已作废，不能入场", "Void ticket — no admission") }}
            </p>
          </div>
          <p v-if="privateView" class="hint">
            {{
              t(
                "票码和订单编号已隐藏。入场或打印前，请点击“显示票码”。",
                "Ticket codes and order references are hidden. Show the ticket code before admission or printing.",
              )
            }}
          </p>
          <p v-if="selected.code && !privateView" class="hint">
            {{
              t(
                "请向工作人员出示此票，使用本系统检票页核验。",
                "Present this ticket to staff for verification in the check-in screen.",
              )
            }}
          </p>
          <div v-if="selected.code && !privateView" class="ticket-code">
            <span>{{ t("票码备用输入", "Manual entry code") }}</span
            ><code>{{ selected.code }}</code>
          </div>
          <p v-if="!privateView" class="mono order-id">{{ selected.id }}</p>
          <div class="ticket-total">
            <span>{{ t("票价", "Ticket price") }}</span
            ><strong>{{ money(selected.price, selected.currency) }}</strong>
          </div>
          <p v-if="selected.paymentMode === 'DEMO'" class="demo-label">
            {{
              t("模拟出票 · 无真实收款", "Simulated issuance · no real charge")
            }}
          </p>
        </div>
        <div class="ticket-actions">
          <template v-if="selected.state === 'HELD'"
            ><p class="hint">
              {{
                remaining > 0
                  ? t("预留剩余约", "Hold remaining: about") +
                    " " +
                    remaining +
                    " " +
                    t("分钟", "minutes")
                  : t("预留已到期，请刷新。", "Hold expired; refresh.")
              }}
            </p>
            <button
              class="primary"
              :disabled="pending || !remaining"
              @click="pay"
            >
              {{
                selected.price === 0
                  ? t("确认领票", "Claim free ticket")
                  : t("继续付款", "Continue payment")
              }}</button
            ><button :disabled="pending" @click="action('cancel')">
              {{ t("取消预留", "Cancel hold") }}
            </button></template
          ><template v-if="selected.code"
            ><button :disabled="privateView" @click="printTicket">
              {{ t("打印 / 保存PDF", "Print / Save PDF") }}</button
            ><button
              v-if="
                selected.state === 'PAID' &&
                selected.ticketState === 'ISSUED' &&
                now < selected.refundEndsAt
              "
              class="danger"
              :disabled="pending"
              @click="action('refund')"
            >
              {{ t("申请退票", "Request refund") }}
            </button></template
          >
          <p v-if="selected.state === 'REFUND_PENDING'" class="hint">
            {{
              t(
                "票已作废。退款处理中，请稍后刷新。",
                "Ticket void. Refund processing; refresh later.",
              )
            }}
          </p>
          <p class="hint">
            {{ t("退票截止", "Refund deadline") }}
            {{ dateTime(selected.refundEndsAt, selected.zone) }}
          </p>
        </div>
      </article>
      <div v-else class="ticket-placeholder">
        {{ t("选择订单查看入场凭证", "Select an order to view your ticket") }}
      </div>
    </div>
  </section>
</template>
