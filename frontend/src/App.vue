<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import {
  ref,
  onMounted,
  onUnmounted,
  computed,
  nextTick,
  defineAsyncComponent,
} from "vue";
import { api, resetApi } from "./api.js";
import { t, language, errorMessage, confirmation } from "./ui.js";
import { routePath, routeSection } from "./routes.js";
import { dialogFocus } from "./dialog-focus.js";
import PublicEvents from "./components/PublicEvents.vue";
const WalletPanel = asyncPage(() => import("./components/WalletPanel.vue"));
const EventPanel = asyncPage(() => import("./components/EventPanel.vue"));
const CheckinPanel = asyncPage(() => import("./components/CheckinPanel.vue"));
const OperationsPanel = asyncPage(
  () => import("./components/OperationsPanel.vue"),
);
const AdminPanel = asyncPage(() => import("./components/AdminPanel.vue"));
const me = ref(null),
  section = ref("public"),
  mode = ref(""),
  pending = ref(false),
  message = ref(""),
  auth = ref(null),
  about = ref(false),
  passwordDialog = ref(false),
  child = ref(null),
  identity = ref({ username: "", password: "", displayName: "", email: "" }),
  passwords = ref({ oldPassword: "", newPassword: "" });
const versionChanged = ref(false);
/** 动态页面加载失败提供显式刷新入口，不静默空白或丢弃表单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function asyncPage(loader) {
  return defineAsyncComponent({
    loader,
    onError(_error, _retry, fail) {
      versionChanged.value = true;
      message.value = t(
        "页面加载失败，可能已更新或连接中断。请刷新页面后重试。",
        "Page could not load after an update or connection loss. Refresh and retry.",
      );
      fail();
    },
  });
}
const reloadPage = () => location.reload();
const staff = computed(
  () => me.value && me.value.permissions.some((p) => p !== "portal"),
);
const menus = computed(() => me.value?.menus || []);
let focusCleanup;
/** 每次写入只提交一次，成功再刷新，未知结果明确告知。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function perform(path, method = "POST", body) {
  if (pending.value) return null;
  pending.value = true;
  message.value = "";
  try {
    return await api(path, { method, body });
  } catch (e) {
    failure(e);
    return null;
  } finally {
    pending.value = false;
  }
}
function failure(e) {
  message.value = errorMessage(e);
  if (e.status === 401) {
    me.value = null;
    resetApi();
    auth.value = "login";
  }
}
async function navigate(next) {
  if (pending.value) return;
  if (child.value?.canLeave && !(await child.value.canLeave())) return;
  section.value = next;
  message.value = "";
  location.hash = routePath(next);
  await nextTick();
  window.scrollTo(0, 0);
}
function openLogin() {
  identity.value.password = "";
  message.value = "";
  auth.value = "login";
}
async function login() {
  const result = await perform("/auth/login", "POST", {
    username: identity.value.username,
    password: identity.value.password,
  });
  identity.value.password = "";
  if (result) {
    me.value = result;
    resetApi();
    auth.value = null;
    if (staff.value) section.value = menus.value[0]?.code || "public";
    else if (section.value !== "public") section.value = "portal";
    if (section.value !== "portal" || !location.hash.startsWith("#/wallet"))
      location.hash = routePath(section.value);
  }
}
async function register() {
  const result = await perform("/auth/register", "POST", identity.value);
  identity.value.password = "";
  if (result) {
    message.value = t("注册成功，请登录。", "Account created. Please sign in.");
    auth.value = "login";
  }
}
async function logout() {
  const result = await perform("/auth/logout");
  if (result) {
    me.value = null;
    resetApi();
    section.value = "public";
    location.hash = routePath("public");
  }
}
async function changePassword() {
  const result = await perform("/auth/password", "POST", passwords.value);
  passwords.value = { oldPassword: "", newPassword: "" };
  if (result) {
    me.value = null;
    resetApi();
    passwordDialog.value = false;
    auth.value = "login";
    message.value = t(
      "密码已修改，请重新登录。",
      "Password changed. Sign in again.",
    );
  }
}
function confirm(value) {
  confirmation.value?.resolve(value);
  confirmation.value = null;
}
/** 刷新和浏览器前后退保持实际页面，岗位菜单仍由当前权限决定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function syncRoute() {
  const requested = routeSection(location.hash);
  const allowed =
    requested === "public" ||
    (requested === "portal" &&
      (!me.value || me.value.permissions.includes("portal"))) ||
    menus.value.some((m) => m.code === requested);
  if (!allowed) {
    location.hash = routePath(
      staff.value ? menus.value[0]?.code || "public" : "public",
    );
    return;
  }
  if (requested === section.value) return;
  if (
    pending.value ||
    (child.value?.canLeave && !(await child.value.canLeave()))
  ) {
    history.replaceState(null, "", "#" + routePath(section.value));
    return;
  }
  section.value = requested;
  message.value = "";
  await nextTick();
  window.scrollTo(0, 0);
}
onMounted(async () => {
  section.value = routeSection(location.hash);
  window.addEventListener("hashchange", syncRoute);
  focusCleanup = dialogFocus(() => {
    if (pending.value) return;
    if (confirmation.value) confirm(false);
    else if (auth.value) {
      auth.value = null;
      identity.value.password = "";
    } else if (about.value) about.value = false;
    else if (passwordDialog.value) passwordDialog.value = false;
  });
  try {
    mode.value = (await api("/public/config")).paymentMode;
    me.value = await api("/auth/me");
    if (!location.hash && staff.value) {
      section.value = menus.value[0]?.code || "public";
      location.hash = routePath(section.value);
    } else await syncRoute();
  } catch (e) {
    if (e.status !== 401) failure(e);
  }
});
onUnmounted(() => {
  focusCleanup?.();
  window.removeEventListener("hashchange", syncRoute);
});
</script>
<template>
  <header class="topbar">
    <a class="brand" href="#/events" @click.prevent="navigate('public')"
      ><img src="/brand/logo.jpg" alt="知华科技" /><strong>TicketDesk</strong
      ><span>{{ t("活动票务", "Event ticketing") }}</span></a
    >
    <div class="top-actions">
      <span v-if="mode === 'DEMO'" class="demo-label">{{
        t("演示环境 · 不收取费用", "Demo · no real charges")
      }}</span
      ><button
        class="text-button"
        @click="language = language === 'zh' ? 'en' : 'zh'"
      >
        {{ language === "zh" ? "English" : "中文" }}</button
      ><button class="text-button" @click="about = true">
        {{ t("关于", "About") }}</button
      ><template v-if="me"
        ><span class="user-name">{{ me.displayName }}</span
        ><button class="text-button" @click="passwordDialog = true">
          {{ t("修改密码", "Password") }}</button
        ><button @click="logout" :disabled="pending">
          {{ t("退出", "Sign out") }}
        </button></template
      ><button v-else class="primary" @click="openLogin">
        {{ t("登录 / 注册", "Sign in / Register") }}
      </button>
    </div>
  </header>
  <div class="shell" :class="{ staff }">
    <aside v-if="staff" class="sidebar">
      <p class="side-label">{{ t("主办工作台", "Organizer workspace") }}</p>
      <button
        v-for="m in menus.filter((x) => x.code !== 'portal')"
        :key="m.code"
        :class="{ active: section === m.code }"
        @click="navigate(m.code)"
      >
        {{ language === "en" ? m.nameEn : m.name }}
      </button>
      <div class="side-bottom">
        <button @click="navigate('public')">
          {{ t("观众活动页", "Attendee events") }}</button
        ><button
          v-if="me.permissions.includes('portal')"
          @click="navigate('portal')"
        >
          {{ t("我的票夹", "My tickets") }}</button
        ><a href="https://www.zhuatech.cn/" target="_blank" rel="noreferrer"
          >{{ t("知华科技官网", "ZhiHua website") }} ↗</a
        >
      </div>
    </aside>
    <main :class="{ workspace: staff }">
      <nav v-if="!staff" class="public-nav">
        <button
          :class="{ active: section === 'public' }"
          @click="navigate('public')"
        >
          {{ t("活动", "Events") }}</button
        ><button
          :class="{ active: section === 'portal' }"
          @click="me ? navigate('portal') : openLogin()"
        >
          {{ t("我的票夹", "My tickets") }}
        </button>
      </nav>
      <p
        v-if="message && !auth && !passwordDialog"
        class="notice error"
        role="alert"
      >
        {{ message
        }}<button v-if="versionChanged" class="text-button" @click="reloadPage">
          {{ t("刷新页面", "Reload page") }}</button
        ><button class="text-button" @click="message = ''">
          {{ t("关闭", "Dismiss") }}
        </button>
      </p>
      <PublicEvents
        v-if="section === 'public'"
        :me="me"
        :pending="pending"
        :perform="perform"
        @login="openLogin"
        @wallet="navigate('portal')"
        @error="failure"
      />
      <WalletPanel
        v-else-if="section === 'portal' && me"
        :pending="pending"
        :perform="perform"
        @events="navigate('public')"
        @error="failure"
      />
      <EventPanel
        v-else-if="section === 'events'"
        ref="child"
        :pending="pending"
        :message="message"
        :perform="perform"
        @error="failure"
      />
      <CheckinPanel
        v-else-if="section === 'checkin'"
        :pending="pending"
        :perform="perform"
        @error="failure"
      />
      <OperationsPanel
        v-else-if="['sales', 'refunds', 'reports', 'audit'].includes(section)"
        :section="section"
        :pending="pending"
        :perform="perform"
        @error="failure"
      />
      <AdminPanel
        v-else-if="['users', 'roles', 'settings'].includes(section)"
        ref="child"
        :section="section"
        :pending="pending"
        :message="message"
        :perform="perform"
        @error="failure"
      />
      <div v-else class="empty">
        <p>{{ t("请登录查看本人票夹", "Sign in to view your tickets") }}</p>
        <button class="primary" @click="openLogin">
          {{ t("登录", "Sign in") }}
        </button>
      </div>
    </main>
  </div>
  <footer class="site-footer">
    {{
      t(
        "知华科技 · 公开源码学习版",
        "ZhiHua Technology · Non-commercial source edition",
      )
    }}<a href="https://www.zhuatech.cn/" target="_blank" rel="noreferrer"
      >www.zhuatech.cn</a
    >
  </footer>
  <div v-if="auth" class="modal-backdrop">
    <section
      class="modal auth-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('登录或注册', 'Sign in or register')"
    >
      <header>
        <h2>
          {{
            auth === "register"
              ? t("注册观众账号", "Create attendee account")
              : t("登录", "Sign in")
          }}
        </h2>
        <button
          type="button"
          class="icon-button"
          :disabled="pending"
          @click="
            auth = null;
            identity.password = '';
          "
          :aria-label="t('关闭', 'Close')"
        >
          ×
        </button>
      </header>
      <p v-if="message" class="form-error" role="alert">{{ message }}</p>
      <form
        autocomplete="off"
        @submit.prevent="auth === 'register' ? register() : login()"
      >
        <label
          >{{ t("登录账号", "Username")
          }}<input
            v-model="identity.username"
            maxlength="60"
            pattern="[a-zA-Z0-9_.\-]{3,60}"
            required
            autocomplete="off"
            :disabled="pending" /></label
        ><template v-if="auth === 'register'"
          ><label
            >{{ t("显示名称", "Display name")
            }}<input
              v-model="identity.displayName"
              maxlength="120"
              required
              :disabled="pending" /></label
          ><label
            >{{ t("邮箱", "Email")
            }}<input
              v-model="identity.email"
              type="email"
              maxlength="254"
              required
              :disabled="pending"
              autocomplete="off" /></label></template
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="identity.password"
            type="password"
            autocomplete="new-password"
            :minlength="auth === 'register' ? 12 : 1"
            maxlength="72"
            required
            :disabled="pending"
        /></label>
        <p v-if="auth === 'register'" class="hint">
          {{
            t(
              "12–72字节，含大小写字母和数字。邮箱仅用于订单通知。",
              "12–72 bytes, upper/lowercase and a digit. Email is used for order notifications.",
            )
          }}
        </p>
        <button class="primary full" :disabled="pending">
          {{
            pending
              ? t("处理中…", "Please wait…")
              : auth === "register"
                ? t("注册", "Create account")
                : t("登录", "Sign in")
          }}</button
        ><button
          type="button"
          class="text-button full"
          :disabled="pending"
          @click="
            auth = auth === 'register' ? 'login' : 'register';
            message = '';
            identity.password = '';
          "
        >
          {{
            auth === "register"
              ? t("已有账号，去登录", "Already registered? Sign in")
              : t("没有账号，注册观众", "New attendee? Create account")
          }}
        </button>
      </form>
    </section>
  </div>
  <div v-if="passwordDialog" class="modal-backdrop">
    <section
      class="modal auth-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('修改密码', 'Change password')"
    >
      <header>
        <h2>{{ t("修改密码", "Change password") }}</h2>
        <button
          class="icon-button"
          :disabled="pending"
          @click="passwordDialog = false"
          :aria-label="t('关闭', 'Close')"
        >
          ×
        </button>
      </header>
      <p v-if="message" class="form-error" role="alert">{{ message }}</p>
      <form autocomplete="off" @submit.prevent="changePassword">
        <label
          >{{ t("原密码", "Current password")
          }}<input
            v-model="passwords.oldPassword"
            type="password"
            autocomplete="new-password"
            required /></label
        ><label
          >{{ t("新密码", "New password")
          }}<input
            v-model="passwords.newPassword"
            type="password"
            autocomplete="new-password"
            minlength="12"
            maxlength="72"
            required /></label
        ><button class="primary" :disabled="pending">
          {{ t("保存并重新登录", "Save and sign in again") }}
        </button>
      </form>
    </section>
  </div>
  <div v-if="confirmation" class="modal-backdrop">
    <section
      class="modal auth-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('确认操作', 'Confirm action')"
    >
      <h2>{{ t("确认操作", "Confirm action") }}</h2>
      <p>{{ confirmation.message }}</p>
      <footer>
        <button @click="confirm(false)">{{ t("取消", "Cancel") }}</button
        ><button class="primary" @click="confirm(true)">
          {{ t("确定", "Confirm") }}
        </button>
      </footer>
    </section>
  </div>
  <div v-if="about" class="modal-backdrop">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('关于TicketDesk', 'About TicketDesk')"
    >
      <header>
        <h2>TicketDesk 1.0.0</h2>
        <button
          class="icon-button"
          @click="about = false"
          :aria-label="t('关闭', 'Close')"
        >
          ×
        </button>
      </header>
      <p>
        {{
          t(
            "知华科技（上海如静知华信息科技有限公司）",
            "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
          )
        }}
      </p>
      <p>
        {{
          t(
            "源码公开、非商业使用。仅限个人学习交流，未经公司书面授权不得商用。",
            "Public source for non-commercial learning only. Commercial use requires written company authorization.",
          )
        }}
      </p>
      <p>
        {{
          t(
            "商业授权或深度定制开发请联系知华科技。",
            "Contact ZhiHua for commercial licensing or custom development.",
          )
        }}
      </p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noreferrer"
        >www.zhuatech.cn ↗</a
      >
      <div v-if="language === 'zh'" class="qr-contact">
        <figure>
          <img src="/brand/wechat-zhuatech.png" alt="微信zhuatech" />
          <figcaption>zhuatech</figcaption>
        </figure>
        <figure>
          <img src="/brand/wechat-zhuatech2.png" alt="微信zhuatech2" />
          <figcaption>zhuatech2</figcaption>
        </figure>
      </div>
      <p v-else>
        <a href="mailto:han@zhuatech.cn">han@zhuatech.cn</a> ·
        <a href="mailto:jack@zhuatech.cn">jack@zhuatech.cn</a><br /><a
          href="https://wa.me/8617521234993"
          >WhatsApp +86 17521234993</a
        >
      </p>
    </section>
  </div>
</template>
