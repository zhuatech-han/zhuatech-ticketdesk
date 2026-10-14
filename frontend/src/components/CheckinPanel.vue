<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, onMounted, onUnmounted } from "vue";
import { BrowserQRCodeReader } from "@zxing/browser";
import { api } from "../api.js";
import { t, localized } from "../ui.js";
const props = defineProps({
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error"]);
const events = ref([]),
  eventId = ref(""),
  code = ref(""),
  decision = ref(""),
  camera = ref(false),
  cameraError = ref(""),
  video = ref(null),
  processing = ref(false);
let controls;
let cameraGeneration = 0;
const reader = new BrowserQRCodeReader();
const decisionText = (s) =>
  ({
    ADMITTED: t("检票成功，可入场", "Admitted"),
    ALREADY_USED: t(
      "此票已使用，请勿再次放行",
      "Already used — do not admit again",
    ),
    VOID: t("此票已作废，不能入场", "Void — do not admit"),
    WRONG_EVENT: t("不属于当前活动", "Wrong event"),
    INVALID_CODE: t("票码无效", "Invalid ticket code"),
  })[s] || s;
function stop() {
  cameraGeneration++;
  controls?.stop();
  controls = null;
  camera.value = false;
}
/** 扫描结果交服务端核销，网络异常不显示成功。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function submit(value = code.value) {
  if (processing.value || !eventId.value || !value) return;
  processing.value = true;
  decision.value = "";
  stop();
  try {
    const result = await props.perform(
      "/events/" + eventId.value + "/check-in",
      "POST",
      { code: value.trim() },
    );
    if (result) {
      decision.value = result.decision;
      code.value = "";
    } else decision.value = "NETWORK_UNKNOWN";
  } finally {
    processing.value = false;
  }
}
async function start() {
  cameraError.value = "";
  decision.value = "";
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    cameraError.value = t(
      "摄像头需要HTTPS；也可上传票码图片或手工输入。",
      "Camera requires HTTPS. Upload a ticket image or enter the code instead.",
    );
    return;
  }
  camera.value = true;
  const generation = ++cameraGeneration;
  try {
    const activeControls = await reader.decodeFromVideoDevice(
      undefined,
      video.value,
      (result, _error, handle) => {
        if (result && !processing.value) {
          controls = handle;
          submit(result.getText());
        }
      },
    );
    if (generation !== cameraGeneration) {
      activeControls.stop();
    } else controls = activeControls;
  } catch {
    stop();
    cameraError.value = t(
      "未能启用摄像头，请核对权限或使用备用方式。",
      "Could not enable camera. Check permission or use an alternative.",
    );
  }
}
async function image(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  if (
    !["image/png", "image/jpeg"].includes(file.type) ||
    file.size > 5 * 1024 * 1024
  ) {
    cameraError.value = t(
      "仅支持JPEG/PNG，最多5MiB。",
      "JPEG/PNG only, maximum 5MiB.",
    );
    return;
  }
  const url = URL.createObjectURL(file);
  try {
    const result = await reader.decodeFromImageUrl(url);
    await submit(result.getText());
  } catch {
    cameraError.value = t(
      "未识别到票码，请更换清晰图片。",
      "No QR code found. Use a clearer image.",
    );
  } finally {
    URL.revokeObjectURL(url);
    event.target.value = "";
  }
}
onMounted(async () => {
  try {
    events.value = (await api("/catalog")).events;
    eventId.value = events.value[0]?.id || "";
  } catch (e) {
    emit("error", e);
  }
});
onUnmounted(stop);
</script>
<template>
  <section class="checkin-content">
    <header class="page-heading">
      <div>
        <h1>{{ t("现场检票", "Check-in") }}</h1>
        <p class="muted">
          {{
            t(
              "选择活动后扫描入场凭证",
              "Choose an event, then scan admission tickets",
            )
          }}
        </p>
      </div>
    </header>
    <label class="event-select"
      >{{ t("当前活动", "Current event")
      }}<select
        v-model="eventId"
        @change="
          stop();
          decision = '';
        "
        :disabled="pending"
      >
        <option value="">{{ t("选择活动", "Choose event") }}</option>
        <option v-for="e in events" :key="e.id" :value="e.id">
          {{ localized(e) }}
        </option>
      </select></label
    >
    <div v-if="!events.length" class="empty">
      {{
        t(
          "暂无授权活动，请联系管理员分配活动。",
          "No authorized events. Ask an administrator to assign one.",
        )
      }}
    </div>
    <div v-else class="checkin-layout">
      <div class="panel camera-panel">
        <video
          ref="video"
          autoplay
          playsinline
          muted
          :class="{ running: camera }"
          :aria-label="t('摄像头取景', 'Camera preview')"
        ></video>
        <div v-if="!camera" class="camera-placeholder">
          <span>▦</span>
          <p>{{ t("在线扫码检票", "Online ticket scanning") }}</p>
        </div>
        <div class="actions">
          <button
            class="primary"
            :disabled="!eventId || pending || processing"
            @click="camera ? stop() : start()"
          >
            {{
              camera
                ? t("停止摄像头", "Stop camera")
                : t("开启摄像头", "Start camera")
            }}</button
          ><label class="upload-button"
            >{{ t("识别票码图片", "Read ticket image")
            }}<input
              type="file"
              accept="image/png,image/jpeg"
              :disabled="!eventId || pending || processing"
              @change="image"
          /></label>
        </div>
        <p v-if="cameraError" class="form-error" role="alert">
          {{ cameraError }}
        </p>
      </div>
      <div>
        <section class="panel scan-result" aria-live="polite" :class="decision">
          <strong v-if="decision">{{
            decision === "NETWORK_UNKNOWN"
              ? t(
                  "结果未知，请核对后重试；不能据此放行。",
                  "Outcome unknown. Verify and retry; do not admit.",
                )
              : decisionText(decision)
          }}</strong>
          <p v-else>{{ t("等待检票", "Waiting for a ticket") }}</p>
        </section>
        <form class="panel manual-scan" @submit.prevent="submit()">
          <label
            >{{ t("手工输入票码", "Manual ticket code")
            }}<textarea
              v-model="code"
              rows="3"
              maxlength="128"
              required
              :disabled="pending || processing"
              autocomplete="off"
            ></textarea></label
          ><button
            class="primary"
            :disabled="!eventId || pending || processing"
          >
            {{ t("核验并检票", "Verify & check in") }}
          </button>
        </form>
      </div>
    </div>
  </section>
</template>
