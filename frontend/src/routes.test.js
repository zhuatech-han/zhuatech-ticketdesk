// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { routePath, routeSection } from "./routes.js";
test("organizer event route is distinct from public events and survives refresh", () => {
  assert.notEqual(routePath("events"), routePath("public"));
  for (const s of ["public", "portal", "events", "checkin", "reports", "users"])
    assert.equal(routeSection("#" + routePath(s)), s);
});
test("wallet deep links preserve personal entrance without treating order query as a section", () =>
  assert.equal(routeSection("#/wallet?order=qa"), "portal"));
