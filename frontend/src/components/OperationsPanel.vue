<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, watch, onMounted } from "vue";
import { api, download } from "../api.js";
import {
  t,
  localized,
  stateName,
  dateTime,
  money,
  ask,
  decisionName,
  taskError,
} from "../ui.js";
const props = defineProps({
  section: { type: String, required: true },
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error"]);
const events = ref([]),
  eventId = ref(""),
  data = ref({ orders: [], refunds: [], mail: [], scans: [] }),
  reports = ref([]),
  audit = ref([]),
  query = ref(""),
  filter = ref("ALL"),
  sort = ref("latest"),
  page = ref(1),
  tab = ref("refunds");
const title = computed(
  () =>
    ({
      sales: t("订单与入场", "Orders & admission"),
      refunds: t("退款与通知", "Refunds & notifications"),
      reports: t("活动报表", "Event reports"),
      audit: t("操作审计", "Audit"),
    })[props.section],
);
const orders = computed(() =>
  data.value.orders
    .filter(
      (x) =>
        (filter.value === "ALL" || x.state === filter.value) &&
        JSON.stringify(x).toLowerCase().includes(query.value.toLowerCase()),
    )
    .sort((a, b) =>
      sort.value === "latest"
        ? b.expiresAt - a.expiresAt
        : a.expiresAt - b.expiresAt,
    ),
);
const visible = computed(() =>
  orders.value.slice((page.value - 1) * 20, page.value * 20),
);
const auditRows = computed(() =>
  audit.value
    .filter((x) =>
      JSON.stringify(x).toLowerCase().includes(query.value.toLowerCase()),
    )
    .slice((page.value - 1) * 20, page.value * 20),
);
watch([query, filter, sort, eventId], () => (page.value = 1));
async function load() {
  try {
    if (props.section === "reports") {
      reports.value = await api("/reports");
      return;
    }
    if (props.section === "audit") {
      audit.value = await api("/audit");
      return;
    }
    events.value = (await api("/catalog")).events;
    if (!events.value.some((x) => x.id === eventId.value))
      eventId.value = events.value[0]?.id || "";
    if (eventId.value)
      data.value = await api(
        "/events/" +
          eventId.value +
          (props.section === "refunds" ? "/finance" : "/operations"),
      );
    else data.value = { orders: [], refunds: [], mail: [], scans: [] };
  } catch (e) {
    emit("error", e);
  }
}
async function retry(kind, id, state) {
  if (
    state === "FAILED" &&
    !(await ask(
      t(
        "已核对支付平台退款确实失败？将发起新的退款请求。",
        "Confirmed that the provider refund failed? This starts a new refund request.",
      ),
    ))
  )
    return;
  const r = await props.perform("/tasks/" + kind + "/" + id + "/retry");
  if (r) await load();
}
async function refund(row) {
  if (
    await ask(
      t(
        "退票会立即作废此票，确认继续？",
        "This immediately voids the ticket. Continue?",
      ),
    )
  ) {
    const r = await props.perform("/orders/" + row.id + "/refund");
    if (r) await load();
  }
}
async function exportCsv() {
  try {
    await download("/reports.csv", "event-reports.csv");
  } catch (e) {
    emit("error", e);
  }
}
watch(
  () => props.section,
  () => {
    query.value = "";
    filter.value = "ALL";
    page.value = 1;
    load();
  },
);
onMounted(load);
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>{{ title }}</h1>
        <p class="muted">
          {{
            section === "reports"
              ? t(
                  "按活动和币种查看出票、入场及资金记录",
                  "Issuance, admission and funds by event and currency",
                )
              : section === "refunds"
                ? t(
                    "核对退款与通知待办，不将失败当完成",
                    "Review pending refunds and notifications",
                  )
                : t(
                    "查看当前授权范围内的记录",
                    "Records within your authorized scope",
                  )
          }}
        </p>
      </div>
      <div class="actions">
        <button :disabled="pending" @click="load">
          {{ t("刷新", "Refresh") }}</button
        ><button
          v-if="section === 'reports'"
          :disabled="pending"
          @click="exportCsv"
        >
          {{ t("导出CSV", "Export CSV") }}
        </button>
      </div>
    </header>
    <template v-if="['sales', 'refunds'].includes(section)"
      ><label class="event-select"
        >{{ t("活动", "Event")
        }}<select v-model="eventId" @change="load">
          <option value="">{{ t("选择活动", "Choose event") }}</option>
          <option v-for="e in events" :key="e.id" :value="e.id">
            {{ localized(e) }}
          </option>
        </select></label
      >
      <div v-if="!events.length" class="empty">
        {{ t("暂无授权活动", "No authorized events") }}
      </div>
      <template v-else
        ><nav class="sub-tabs">
          <button
            v-for="k in section === 'sales'
              ? ['orders', 'scans']
              : ['refunds', 'mail', 'orders']"
            :key="k"
            :class="{
              active:
                (section === 'sales'
                  ? tab === 'scans'
                    ? 'scans'
                    : 'orders'
                  : tab) === k,
            }"
            @click="tab = k"
          >
            {{
              {
                orders: t("订单", "Orders"),
                scans: t("核销记录", "Check-in log"),
                refunds: t("退款待办", "Refund tasks"),
                mail: t("通知队列", "Notifications"),
              }[k]
            }}
          </button>
        </nav>
        <div
          v-if="(section === 'sales' && tab !== 'scans') || tab === 'orders'"
          class="panel"
        >
          <div class="toolbar">
            <label class="search-box"
              ><input
                v-model="query"
                :placeholder="t('搜索订单或观众', 'Search order or attendee')"
                :aria-label="t('搜索订单', 'Search orders')" /></label
            ><select v-model="filter" :aria-label="t('状态', 'State')">
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
            ><select v-model="sort" :aria-label="t('排序', 'Sort')">
              <option value="latest">
                {{ t("最新在前", "Newest first") }}
              </option>
              <option value="oldest">
                {{ t("最早在前", "Oldest first") }}
              </option>
            </select>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("订单", "Order") }}</th>
                  <th>{{ t("观众", "Attendee") }}</th>
                  <th>{{ t("座位", "Seat") }}</th>
                  <th>{{ t("票价", "Price") }}</th>
                  <th>{{ t("订单状态", "Order state") }}</th>
                  <th>{{ t("票状态", "Ticket state") }}</th>
                  <th>{{ t("支付环境", "Payment mode") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in visible" :key="r.id">
                  <td class="mono" :title="r.id">{{ r.id.slice(0, 8) }}</td>
                  <td>
                    {{ r.buyer }}<small>{{ r.username }}</small>
                  </td>
                  <td>{{ r.seat }}</td>
                  <td>{{ money(r.price, r.currency) }}</td>
                  <td>
                    <span class="badge" :class="r.state">{{
                      stateName(r.state)
                    }}</span>
                  </td>
                  <td>{{ r.ticketState ? stateName(r.ticketState) : "—" }}</td>
                  <td>{{ r.paymentMode || "—" }}</td>
                  <td>
                    <button
                      v-if="
                        section === 'refunds' &&
                        r.state === 'PAID' &&
                        r.ticketState === 'ISSUED'
                      "
                      class="danger"
                      :disabled="pending"
                      @click="refund(r)"
                    >
                      {{ t("退票", "Refund") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!orders.length" class="empty">
              {{ t("没有匹配订单", "No matching orders") }}
            </div>
          </div>
          <footer class="pagination">
            <span
              >{{ page }} /
              {{ Math.max(1, Math.ceil(orders.length / 20)) }}</span
            ><button :disabled="page === 1" @click="page--">
              {{ t("上一页", "Previous") }}</button
            ><button :disabled="page * 20 >= orders.length" @click="page++">
              {{ t("下一页", "Next") }}
            </button>
          </footer>
        </div>
        <div
          v-else-if="section === 'sales' && tab === 'scans'"
          class="panel table-wrap"
        >
          <table>
            <thead>
              <tr>
                <th>{{ t("时间", "Time") }}</th>
                <th>{{ t("序号", "Sequence") }}</th>
                <th>{{ t("检票员", "Operator") }}</th>
                <th>{{ t("票身份", "Ticket ID") }}</th>
                <th>{{ t("结果", "Decision") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in data.scans" :key="s.id">
                <td>{{ dateTime(s.createdAt) }}</td>
                <td>{{ s.id }}</td>
                <td>{{ s.operatorId }}</td>
                <td class="mono">{{ s.ticketId?.slice(0, 8) || "—" }}</td>
                <td>{{ decisionName(s.decision) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="!data.scans.length" class="empty">
            {{ t("暂无核销记录", "No check-in records") }}
          </div>
        </div>
        <div v-else class="panel table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("订单", "Order") }}</th>
                <th v-if="tab === 'refunds'">{{ t("金额", "Amount") }}</th>
                <th>{{ t("状态", "State") }}</th>
                <th>{{ t("尝试次数", "Attempts") }}</th>
                <th>{{ t("问题", "Issue") }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in data[tab] || []" :key="task.id">
                <td class="mono">{{ task.bookingId.slice(0, 8) }}</td>
                <td v-if="tab === 'refunds'">
                  {{ money(task.amount, task.currency)
                  }}<small>{{ task.mode }}</small>
                </td>
                <td>
                  <span class="badge" :class="task.state">{{
                    stateName(task.state)
                  }}</span>
                </td>
                <td>{{ task.attempts }}</td>
                <td class="mono">{{ taskError(task.error) }}</td>
                <td>
                  <button
                    v-if="!['DONE', 'SENT'].includes(task.state)"
                    :disabled="pending"
                    @click="
                      retry(
                        tab === 'refunds' ? 'refund' : 'mail',
                        task.id,
                        task.state,
                      )
                    "
                  >
                    {{ t("重试", "Retry") }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="!data[tab]?.length" class="empty">
            {{ t("暂无待办", "No tasks") }}
          </div>
        </div></template
      ></template
    >
    <div v-else-if="section === 'reports'" class="panel table-wrap">
      <table>
        <thead>
          <tr>
            <th>{{ t("活动", "Event") }}</th>
            <th>{{ t("环境", "Mode") }}</th>
            <th>{{ t("币种", "Currency") }}</th>
            <th>{{ t("出票", "Issued") }}</th>
            <th>{{ t("入场", "Admitted") }}</th>
            <th>{{ t("收款记录", "Recorded payments") }}</th>
            <th>{{ t("退款记录", "Recorded refunds") }}</th>
            <th>{{ t("净额", "Net") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in reports" :key="r.id + '-' + r.mode">
            <td>{{ localized(r) }}</td>
            <td>{{ r.mode }}</td>
            <td>{{ r.currency }}</td>
            <td>{{ r.issued }}</td>
            <td>{{ r.checked }}</td>
            <td>{{ money(r.gross, r.currency) }}</td>
            <td>{{ money(r.refunded, r.currency) }}</td>
            <td>{{ money(r.net, r.currency) }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="!reports.length" class="empty">
        {{ t("暂无活动报表", "No event reports") }}
      </div>
    </div>
    <div v-else class="panel">
      <div class="toolbar">
        <label class="search-box"
          ><input
            v-model="query"
            :placeholder="
              t('搜索动作、账号或对象', 'Search action, actor or object')
            "
            :aria-label="t('搜索审计', 'Search audit')"
        /></label>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t("时间", "Time") }}</th>
              <th>{{ t("操作者", "Actor") }}</th>
              <th>{{ t("动作", "Action") }}</th>
              <th>{{ t("对象", "Object") }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in auditRows" :key="r.id">
              <td>{{ dateTime(r.createdAt) }}</td>
              <td>{{ r.actor }}</td>
              <td>{{ r.action }}</td>
              <td class="mono">{{ r.objectId }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="pagination">
        <span>{{ page }}</span
        ><button :disabled="page === 1" @click="page--">
          {{ t("上一页", "Previous") }}</button
        ><button :disabled="page * 20 >= audit.length" @click="page++">
          {{ t("下一页", "Next") }}
        </button>
      </footer>
    </div>
  </section>
</template>
