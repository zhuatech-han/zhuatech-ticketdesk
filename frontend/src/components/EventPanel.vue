<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { localTime, eventTime } from "../event-time.js";
import { api } from "../api.js";
import {
  t,
  localized,
  dateTime,
  money,
  stateName,
  cloneRecord,
  ask,
  minor,
  typeSeats,
} from "../ui.js";
const props = defineProps({
  pending: Boolean,
  message: { type: String, default: "" },
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error"]);
const data = ref({
    events: [],
    departments: [],
    staffOptions: [],
    categories: [],
  }),
  query = ref(""),
  filter = ref("ALL"),
  page = ref(1),
  selected = ref(null),
  layout = ref([]),
  editing = ref(null),
  typeForm = ref(null);
let initial = "";
let originalTimes = {};
const filtered = computed(() =>
  data.value.events
    .filter(
      (e) =>
        (filter.value === "ALL" || e.state === filter.value) &&
        (localized(e) + " " + localized(e, "venue"))
          .toLowerCase()
          .includes(query.value.toLowerCase()),
    )
    .sort((a, b) => b.startsAt - a.startsAt),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * 20, page.value * 20),
);
watch([query, filter], () => (page.value = 1));
async function load() {
  try {
    data.value = await api("/catalog");
    if (selected.value) {
      selected.value =
        data.value.events.find((x) => x.id === selected.value.id) || null;
      if (selected.value)
        layout.value = (
          await api("/events/" + selected.value.id + "/layout")
        ).seats;
    }
  } catch (e) {
    emit("error", e);
  }
}
async function select(e) {
  selected.value = e;
  try {
    layout.value = (await api("/events/" + e.id + "/layout")).seats;
  } catch (error) {
    emit("error", error);
  }
}
function edit(e) {
  originalTimes = e
    ? Object.fromEntries(
        ["startsAt", "saleStartsAt", "saleEndsAt", "refundEndsAt"].map((k) => [
          k,
          e[k],
        ]),
      )
    : {};
  const now = Date.now();
  editing.value = e
    ? cloneRecord(e)
    : {
        name: "",
        nameEn: "",
        venue: "",
        venueEn: "",
        description: "",
        descriptionEn: "",
        category: data.value.categories.find((x) => x.enabled)?.code || "",
        currency: "CNY",
        departmentId: data.value.departments[0]?.id || "",
        staff: [],
        state: "DRAFT",
        startsAt: now + 86400000,
        saleStartsAt: now,
        saleEndsAt: now + 82800000,
        refundEndsAt: now + 79200000,
      };
  for (const key of ["startsAt", "saleStartsAt", "saleEndsAt", "refundEndsAt"])
    editing.value[key] = localTime(editing.value[key]);
  initial = JSON.stringify(editing.value);
}
async function canLeave() {
  if (
    (editing.value || typeForm.value) &&
    !(await ask(t("放弃尚未保存的修改？", "Discard unsaved changes?")))
  )
    return false;
  editing.value = null;
  typeForm.value = null;
  return true;
}
defineExpose({ canLeave });
async function close() {
  if (editing.value && JSON.stringify(editing.value) === initial) {
    editing.value = null;
    return;
  }
  await canLeave();
}
const eventBody = (e) => ({
  ...e,
  ...Object.fromEntries(
    ["startsAt", "saleStartsAt", "saleEndsAt", "refundEndsAt"].map((k) => [
      k,
      eventTime(e[k], originalTimes[k]),
    ]),
  ),
});
async function save() {
  const e = editing.value;
  const result = await props.perform(
    "/events" + (e.id ? "/" + e.id : ""),
    e.id ? "PUT" : "POST",
    eventBody(e),
  );
  if (result) {
    editing.value = null;
    await load();
    const row = data.value.events.find((x) => x.id === result.id);
    if (row) await select(row);
  }
}
async function status(state) {
  if (
    !(await ask(
      state === "PUBLISHED"
        ? t(
            "发布此活动，开放观众选座？",
            "Publish this event for attendee reservations?",
          )
        : t(
            "停止此活动售票？已出票仍保留。",
            "Stop ticket sales? Existing tickets are preserved.",
          ),
    ))
  )
    return;
  const e = selected.value;
  const body = {
    ...e,
    state,
    ...Object.fromEntries(
      ["startsAt", "saleStartsAt", "saleEndsAt", "refundEndsAt"].map((k) => [
        k,
        new Date(e[k]).toISOString(),
      ]),
    ),
  };
  const result = await props.perform("/events/" + e.id, "PUT", body);
  if (result) await load();
}
async function removeEvent() {
  if (
    await ask(
      t("删除未销售草稿及其座位？", "Delete this unsold draft and its seats?"),
    )
  ) {
    const r = await props.perform("/events/" + selected.value.id, "DELETE");
    if (r) {
      selected.value = null;
      await load();
    }
  }
}
function editType(row) {
  typeForm.value = row
    ? { ...row, price: (row.price / 100).toFixed(2) }
    : {
        name: "",
        nameEn: "",
        prefix: "",
        price: "0.00",
        capacity: 50,
        columns: 10,
      };
}
async function saveType() {
  let b;
  try {
    b = { ...typeForm.value, price: minor(typeForm.value.price) };
  } catch (e) {
    emit("error", e);
    return;
  }
  const r = await props.perform(
    "/events/" + selected.value.id + "/types" + (b.id ? "/" + b.id : ""),
    b.id ? "PUT" : "POST",
    b,
  );
  if (r) {
    typeForm.value = null;
    await load();
  }
}
async function deleteType(row) {
  if (
    await ask(
      t(
        "删除此未销售票种与座位？",
        "Delete this unsold ticket type and its seats?",
      ),
    )
  ) {
    const r = await props.perform(
      "/events/" + selected.value.id + "/types/" + row.id,
      "DELETE",
    );
    if (r) await load();
  }
}
onMounted(load);
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>{{ t("活动与座位", "Events & seats") }}</h1>
        <p class="muted">
          {{
            t(
              "管理活动、票种及工作人员",
              "Manage events, ticket types and staff",
            )
          }}
        </p>
      </div>
      <div class="actions">
        <button :disabled="pending" @click="load">
          {{ t("刷新", "Refresh") }}</button
        ><button class="primary" :disabled="pending" @click="edit(null)">
          ＋ {{ t("新建活动", "New event") }}
        </button>
      </div>
    </header>
    <div v-if="!selected" class="inline-stats">
      <span>{{ data.events.length }} {{ t("场活动", "events") }}</span
      ><span
        >{{ data.events.filter((x) => x.state === "PUBLISHED").length }}
        {{ t("已发布", "published") }}</span
      ><span
        >{{
          data.events.reduce(
            (n, e) => n + e.types.reduce((m, t) => m + t.capacity, 0),
            0,
          )
        }}
        {{ t("个座位", "seats") }}</span
      >
    </div>
    <div v-if="!selected" class="panel">
      <div class="toolbar">
        <label class="search-box"
          ><input
            v-model="query"
            :placeholder="t('搜索活动或场地', 'Search event or venue')"
            :aria-label="t('搜索活动', 'Search events')" /></label
        ><select v-model="filter" :aria-label="t('活动状态', 'Event status')">
          <option value="ALL">{{ t("全部状态", "All states") }}</option>
          <option
            v-for="s in ['DRAFT', 'PUBLISHED', 'CLOSED']"
            :key="s"
            :value="s"
          >
            {{ stateName(s) }}
          </option></select
        ><span class="muted"
          >{{ filtered.length }} {{ t("场", "events") }}</span
        >
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t("活动名称", "Event") }}</th>
              <th>{{ t("活动时间", "Date") }}</th>
              <th>{{ t("场地", "Venue") }}</th>
              <th>{{ t("座位", "Seats") }}</th>
              <th>{{ t("状态", "State") }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="e in visible" :key="e.id">
              <td>
                <strong>{{ localized(e) }}</strong>
              </td>
              <td>{{ dateTime(e.startsAt, e.zone) }}</td>
              <td>{{ localized(e, "venue") }}</td>
              <td>{{ e.types.reduce((n, x) => n + x.capacity, 0) }}</td>
              <td>
                <span class="badge" :class="e.state">{{
                  stateName(e.state)
                }}</span>
              </td>
              <td>
                <button @click="select(e)">{{ t("管理", "Manage") }} →</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!filtered.length" class="empty">
          {{
            t(
              "暂无活动，先新建活动并添加票种。",
              "No events. Create an event and add a ticket type.",
            )
          }}
        </div>
      </div>
      <footer class="pagination">
        <span
          >{{ page }} / {{ Math.max(1, Math.ceil(filtered.length / 20)) }}</span
        ><button :disabled="page === 1" @click="page--">
          {{ t("上一页", "Previous") }}</button
        ><button :disabled="page * 20 >= filtered.length" @click="page++">
          {{ t("下一页", "Next") }}
        </button>
      </footer>
    </div>
    <template v-else
      ><button class="text-button back" @click="selected = null">
        ← {{ t("活动列表", "Event list") }}
      </button>
      <header class="event-office-heading">
        <div>
          <span class="badge" :class="selected.state">{{
            stateName(selected.state)
          }}</span>
          <h2>{{ localized(selected) }}</h2>
          <p>
            {{ dateTime(selected.startsAt, selected.zone) }} ·
            {{ localized(selected, "venue") }}
          </p>
        </div>
        <div class="actions">
          <button :disabled="pending" @click="edit(selected)">
            {{ t("编辑资料", "Edit details") }}</button
          ><button
            v-if="selected.state !== 'PUBLISHED'"
            class="primary"
            :disabled="pending || !selected.types.length"
            @click="status('PUBLISHED')"
          >
            {{ t("发布活动", "Publish event") }}</button
          ><button v-else :disabled="pending" @click="status('CLOSED')">
            {{ t("停止售票", "Close sales") }}</button
          ><button
            v-if="selected.state === 'DRAFT'"
            class="danger"
            :disabled="pending"
            @click="removeEvent"
          >
            {{ t("删除草稿", "Delete draft") }}
          </button>
        </div>
      </header>
      <div class="panel">
        <header class="panel-heading">
          <h3>{{ t("票种与座位", "Ticket types & seats") }}</h3>
          <button
            v-if="selected.state === 'DRAFT'"
            :disabled="pending"
            @click="editType(null)"
          >
            ＋ {{ t("添加票种", "Add ticket type") }}
          </button>
        </header>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("票种", "Type") }}</th>
                <th>{{ t("座位前缀", "Prefix") }}</th>
                <th>{{ t("票价", "Price") }}</th>
                <th>{{ t("数量", "Capacity") }}</th>
                <th>{{ t("每行", "Columns") }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="type in selected.types" :key="type.id">
                <td>{{ localized(type) }}</td>
                <td class="mono">{{ type.prefix }}</td>
                <td>{{ money(type.price, selected.currency) }}</td>
                <td>{{ type.capacity }}</td>
                <td>{{ type.columns }}</td>
                <td v-if="selected.state === 'DRAFT'">
                  <div class="actions">
                    <button :disabled="pending" @click="editType(type)">
                      {{ t("编辑", "Edit") }}</button
                    ><button
                      class="danger"
                      :disabled="pending"
                      @click="deleteType(type)"
                    >
                      {{ t("删除", "Delete") }}
                    </button>
                  </div>
                </td>
                <td v-else>—</td>
              </tr>
            </tbody>
          </table>
          <div v-if="!selected.types.length" class="empty">
            {{
              t("添加票种后才能发布。", "Add a ticket type before publishing.")
            }}
          </div>
        </div>
      </div>
      <div class="panel layout-preview">
        <h3>{{ t("当前座位图", "Current seat layout") }}</h3>
        <div class="stage">{{ t("舞台 / 主讲台", "Stage") }}</div>
        <section
          v-for="type in selected.types"
          :key="type.id"
          class="seat-zone"
        >
          <header>
            {{ localized(type) }} · {{ type.capacity }} {{ t("座", "seats") }}
          </header>
          <div class="seat-grid" :style="{ '--columns': type.columns }">
            <span
              v-for="s in typeSeats(layout, type)"
              :key="s.id"
              :class="{ unavailable: !s.available }"
              >{{ s.label }}</span
            >
          </div>
        </section>
      </div></template
    >
    <div v-if="editing" class="modal-backdrop">
      <section
        class="modal wide"
        role="dialog"
        aria-modal="true"
        :aria-label="t('活动资料', 'Event details')"
      >
        <header>
          <h2>
            {{
              editing.id
                ? t("编辑活动", "Edit event")
                : t("新建活动", "New event")
            }}
          </h2>
          <button
            class="icon-button"
            :disabled="pending"
            @click="close"
            :aria-label="t('关闭', 'Close')"
          >
            ×
          </button>
        </header>
        <p v-if="message" class="form-error" role="alert">{{ message }}</p>
        <form @submit.prevent="save">
          <div class="form-grid">
            <label
              >{{ t("中文名称", "Chinese name")
              }}<input v-model="editing.name" required maxlength="120" /></label
            ><label
              >English name<input
                v-model="editing.nameEn"
                required
                maxlength="120" /></label
            ><label
              >{{ t("中文场地", "Chinese venue")
              }}<input
                v-model="editing.venue"
                required
                maxlength="120" /></label
            ><label
              >English venue<input
                v-model="editing.venueEn"
                required
                maxlength="120" /></label
            ><label
              >{{ t("类别", "Category")
              }}<select v-model="editing.category" required>
                <option
                  v-for="c in data.categories.filter(
                    (x) => x.enabled || x.code === editing.category,
                  )"
                  :key="c.id"
                  :value="c.code"
                >
                  {{ localized(c) }}
                </option>
              </select></label
            ><label
              >{{ t("组织", "Organization")
              }}<select
                v-model="editing.departmentId"
                required
                :disabled="!!editing.id"
              >
                <option v-for="d in data.departments" :key="d.id" :value="d.id">
                  {{ d.name }}
                </option>
              </select></label
            ><label
              >{{ t("货币", "Currency")
              }}<select
                v-model="editing.currency"
                required
                :disabled="!!editing.id"
              >
                <option
                  v-for="c in ['CNY', 'USD', 'EUR', 'GBP', 'HKD']"
                  :key="c"
                >
                  {{ c }}
                </option>
              </select></label
            ><label
              >{{ t("活动开始", "Event start")
              }}<input
                v-model="editing.startsAt"
                type="datetime-local"
                required
                :disabled="editing.state !== 'DRAFT'" /></label
            ><label
              >{{ t("开售时间", "Sales open")
              }}<input
                v-model="editing.saleStartsAt"
                type="datetime-local"
                required
                :disabled="editing.state !== 'DRAFT'" /></label
            ><label
              >{{ t("停售时间", "Sales close")
              }}<input
                v-model="editing.saleEndsAt"
                type="datetime-local"
                required
                :disabled="editing.state !== 'DRAFT'" /></label
            ><label
              >{{ t("退票截止", "Refund deadline")
              }}<input
                v-model="editing.refundEndsAt"
                type="datetime-local"
                required
                :disabled="editing.state !== 'DRAFT'"
            /></label>
          </div>
          <label
            >{{ t("中文介绍", "Chinese description")
            }}<textarea
              v-model="editing.description"
              maxlength="3000"
              rows="3"
            ></textarea></label
          ><label
            >English description<textarea
              v-model="editing.descriptionEn"
              maxlength="3000"
              rows="3"
            ></textarea>
          </label>
          <fieldset>
            <legend>{{ t("授权工作人员", "Authorized staff") }}</legend>
            <label
              v-for="u in data.staffOptions.filter(
                (x) => x.departmentId === editing.departmentId,
              )"
              :key="u.id"
              class="check"
              ><input v-model="editing.staff" type="checkbox" :value="u.id" />{{
                u.name
              }}</label
            >
          </fieldset>
          <p class="hint">
            {{
              t(
                "时间使用此浏览器时区；售票结束和退票截止不得晚于活动开始。",
                "Times use this browser’s timezone. Sales and refund deadlines cannot be later than event start.",
              )
            }}
          </p>
          <footer>
            <button type="button" :disabled="pending" @click="close">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("保存", "Save") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
    <div v-if="typeForm" class="modal-backdrop">
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        :aria-label="t('票种配置', 'Ticket type')"
      >
        <header>
          <h2>{{ t("票种配置", "Ticket type") }}</h2>
          <button
            class="icon-button"
            :disabled="pending"
            @click="canLeave"
            :aria-label="t('关闭', 'Close')"
          >
            ×
          </button>
        </header>
        <p v-if="message" class="form-error" role="alert">{{ message }}</p>
        <form @submit.prevent="saveType">
          <div class="form-grid">
            <label
              >{{ t("中文名称", "Chinese name")
              }}<input v-model="typeForm.name" required maxlength="60" /></label
            ><label
              >English name<input
                v-model="typeForm.nameEn"
                required
                maxlength="60" /></label
            ><label
              >{{ t("座位前缀", "Seat prefix")
              }}<input
                v-model="typeForm.prefix"
                pattern="[A-Z]{1,4}-"
                placeholder="A-"
                required
                maxlength="5" /></label
            ><label
              >{{ t("票价", "Price")
              }}<input
                v-model="typeForm.price"
                inputmode="decimal"
                pattern="[0-9]+(\.[0-9]{1,2})?"
                required /></label
            ><label
              >{{ t("座位数量", "Seat count")
              }}<input
                v-model.number="typeForm.capacity"
                type="number"
                min="1"
                max="500"
                required /></label
            ><label
              >{{ t("每行座位", "Seats per row")
              }}<input
                v-model.number="typeForm.columns"
                type="number"
                min="1"
                max="25"
                required
            /></label>
          </div>
          <p class="hint">
            {{
              t(
                "票价为0时可免费领票；座位编号由前缀和序号组成。",
                "A zero price enables free tickets. Seat labels use the prefix plus sequence.",
              )
            }}
          </p>
          <footer>
            <button type="button" :disabled="pending" @click="canLeave">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("保存", "Save") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>
