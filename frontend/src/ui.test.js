// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { minor, typeSeats, errorMessage, requestId } from "./ui.js";
test("amount rejects fractions beyond cents and exponent notation", () => {
  assert.equal(minor("12.30"), 1230);
  for (const s of ["1.234", "1e2", "-1", "NaN"]) assert.throws(() => minor(s));
});
test("seat ordering uses actual type prefix and numeric identity", () => {
  const seats = [{ label: "B-1" }, { label: "A-10" }, { label: "A-2" }];
  assert.deepEqual(
    typeSeats(seats, { prefix: "A-" }).map((x) => x.label),
    ["A-2", "A-10"],
  );
  assert.equal(seats.length, 3);
});
test("unknown write result tells attendee to verify instead of treating as failure", () =>
  assert.match(errorMessage(Error("RESULT_UNKNOWN")), /结果未知/));

test("reservation IDs are canonical UUIDv4 with a fresh random identity", () => {
  const ids = Array.from({ length: 64 }, () => requestId());
  assert.equal(new Set(ids).size, 64);
  for (const id of ids)
    assert.match(
      id,
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
});
