// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { localTime, eventTime } from "./event-time.js";
test("editing metadata must preserve frozen seconds and milliseconds", () => {
  const original = "2026-10-13T03:17:29.456Z";
  assert.equal(eventTime(localTime(original), original), original);
});
test("explicit minute changes and new events produce the chosen actual time", () => {
  const input = "2026-10-14T08:35";
  assert.equal(eventTime(input, null), new Date(input).toISOString());
  assert.notEqual(
    eventTime(input, "2026-10-13T03:17:29.456Z"),
    "2026-10-13T03:17:29.456Z",
  );
});
