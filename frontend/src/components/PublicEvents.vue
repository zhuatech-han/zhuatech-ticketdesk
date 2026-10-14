<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import { api } from "../api.js";
import { t, localized, dateTime, money, typeSeats, requestId } from "../ui.js";
const props = defineProps({
  me: { type: Object, default: null },
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["login", "wallet", "error"]);
const rows = ref([]),
  event = ref(null),
  chosen = ref(null),
  query = ref(""),
  page = ref(1),
  ready = ref(false);
const filtered = computed(() =>
  rows.value
    .filter((e) =>
      (localized(e) + " " + localized(e, "venue"))
        .toLowerCase()
        .includes(query.value.toLowerCase()),
    )
    .sort((a, b) => a.startsAt - b.startsAt),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * 12, page.value * 12),
);
async function load() {
  ready.value = false;
  try {
    rows.value = await api("/public/events");
  } catch (e) {
    emit("error", e);
  } finally {
    ready.value = true;
  }
}
async function open(id) {
  try {
    event.value = await api("/public/events/" + id);
    chosen.value = null;
  } catch (e) {
    emit("error", e);
  }
}
/** 所选座位只提供ID，价格和持有人由服务端决定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function reserve() {
  if (!props.me) {
    emit("login");
    return;
  }
  if (!chosen.value) return;
  const result = await props.perform(
    "/events/" + event.value.id + "/reserve",
    "POST",
    { seatId: chosen.value.id, requestId: requestId() },
  );
  if (result) {
    emit("wallet");
  } else await open(event.value.id);
}
const clockNow = ref(Date.now());
let clockTimer;
const salesOpen = computed(
  () =>
    event.value &&
    clockNow.value >= event.value.saleStartsAt &&
    clockNow.value < event.value.saleEndsAt,
);
onMounted(() => {
  load();
  clockTimer = setInterval(() => (clockNow.value = Date.now()), 1000);
});
onUnmounted(() => clearInterval(clockTimer));
</script>
<template>
  <section class="public-content">
    <template v-if="!event"
      ><header class="page-heading">
        <div>
          <h1>{{ t("活动", "Events") }}</h1>
          <p class="muted">
            {{
              t(
                "选择活动，查看座位与票务信息。",
                "Choose an event and view seats and ticket details.",
              )
            }}
          </p>
        </div>
        <button @click="load">{{ t("刷新", "Refresh") }}</button>
      </header>
      <label class="search-box"
        ><input
          v-model="query"
          @input="page = 1"
          :placeholder="t('搜索活动或场地', 'Search events or venue')"
          :aria-label="t('搜索活动', 'Search events')"
      /></label>
      <p v-if="!ready" class="empty">{{ t("读取中…", "Loading…") }}</p>
      <div v-else-if="!filtered.length" class="empty">
        {{ t("暂无已发布活动", "No published events") }}
      </div>
      <div class="event-list">
        <button
          v-for="e in visible"
          :key="e.id"
          class="event-row"
          @click="open(e.id)"
        >
          <div class="date-block">
            <strong>{{ new Date(e.startsAt).getDate() }}</strong
            ><span>{{
              new Intl.DateTimeFormat("en", { month: "short" }).format(
                new Date(e.startsAt),
              )
            }}</span>
          </div>
          <div class="event-row-info">
            <h2>{{ localized(e) }}</h2>
            <p>
              {{ dateTime(e.startsAt, e.zone) }} · {{ localized(e, "venue") }}
            </p>
          </div>
          <div class="event-price">
            <strong>{{
              e.types.length
                ? money(Math.min(...e.types.map((x) => x.price)), e.currency)
                : "—"
            }}</strong
            ><span>{{ t("起", "from") }} →</span>
          </div>
        </button>
      </div>
      <footer v-if="filtered.length > 12" class="pagination">
        <button :disabled="page === 1" @click="page--">
          {{ t("上一页", "Previous") }}</button
        ><span>{{ page }} / {{ Math.ceil(filtered.length / 12) }}</span
        ><button :disabled="page * 12 >= filtered.length" @click="page++">
          {{ t("下一页", "Next") }}
        </button>
      </footer></template
    >
    <template v-else
      ><button class="text-button back" @click="event = null">
        ← {{ t("所有活动", "All events") }}
      </button>
      <div class="event-detail-heading">
        <p class="eyebrow">{{ dateTime(event.startsAt, event.zone) }}</p>
        <h1>{{ localized(event) }}</h1>
        <p>{{ localized(event, "venue") }}</p>
        <p class="description">{{ localized(event, "description") }}</p>
        <p class="muted">
          {{ t("退票截止", "Refund deadline") }}
          {{ dateTime(event.refundEndsAt, event.zone) }}
        </p>
      </div>
      <div class="seat-checkout">
        <div class="seat-map">
          <div class="stage">{{ t("舞台 / 主讲台", "Stage") }}</div>
          <section v-for="type in event.types" :key="type.id" class="seat-zone">
            <header>
              <h3>{{ localized(type) }}</h3>
              <span>{{ money(type.price, event.currency) }}</span>
            </header>
            <div class="seat-grid" :style="{ '--columns': type.columns }">
              <button
                v-for="s in typeSeats(event.seats, type)"
                :key="s.id"
                :disabled="!s.available || pending || !salesOpen"
                :class="{
                  chosen: chosen?.id === s.id,
                  unavailable: !s.available,
                }"
                :aria-pressed="chosen?.id === s.id"
                :aria-label="
                  s.label +
                  ' ' +
                  (s.available
                    ? t('可选', 'Available')
                    : t('不可选', 'Unavailable'))
                "
                @click="chosen = s"
              >
                {{ s.label }}
              </button>
            </div>
          </section>
          <div class="seat-legend">
            <span><i></i>{{ t("可选", "Available") }}</span
            ><span><i class="chosen"></i>{{ t("已选", "Selected") }}</span
            ><span
              ><i class="unavailable"></i>{{ t("不可选", "Unavailable") }}</span
            ><button class="text-button" @click="open(event.id)">
              {{ t("刷新座位", "Refresh seats") }}
            </button>
          </div>
        </div>
        <aside class="selection-panel">
          <h2>{{ t("选座", "Select a seat") }}</h2>
          <template v-if="chosen"
            ><div class="selected-seat">{{ chosen.label }}</div>
            <p>{{ money(chosen.price, event.currency) }}</p></template
          >
          <p v-else class="muted">
            {{ t("从座位图选择一个座位。", "Choose one seat from the map.") }}
          </p>
          <p class="hint">
            {{
              t(
                "每单一座，预留35分钟。",
                "One seat per order, held for 35 minutes.",
              )
            }}
          </p>
          <button
            class="primary full"
            :disabled="
              !chosen ||
              pending ||
              !salesOpen ||
              (me && !me.permissions.includes('portal'))
            "
            @click="reserve"
          >
            {{
              me
                ? t("预留并查看订单", "Reserve & view order")
                : t("登录后预留", "Sign in to reserve")
            }}
          </button>
          <p v-if="!salesOpen" class="notice">
            {{ t("尚未开售或已停售", "Sales not open or closed") }}
          </p>
        </aside>
      </div></template
    >
  </section>
</template>
