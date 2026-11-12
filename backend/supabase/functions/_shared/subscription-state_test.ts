import {
  hasPremiumEntitlement,
  isAllowedDailySathiPlan,
  mapGoogleSubscriptionState,
} from "./subscription-state.ts";

import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";

Deno.test("maps all known Google Play subscription states", () => {
  const cases: [string, string][] = [
    ["SUBSCRIPTION_STATE_PENDING", "pending"],
    ["SUBSCRIPTION_STATE_ACTIVE", "active"],
    ["SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "grace_period"],
    ["SUBSCRIPTION_STATE_ON_HOLD", "on_hold"],
    ["SUBSCRIPTION_STATE_PAUSED", "paused"],
    ["SUBSCRIPTION_STATE_CANCELED", "canceled"],
    ["SUBSCRIPTION_STATE_EXPIRED", "expired"],
  ];
  for (const [google, expected] of cases) {
    assertEquals(mapGoogleSubscriptionState(google), expected);
  }
});

Deno.test("unknown, revoked-like and malformed states fail closed", () => {
  assertEquals(mapGoogleSubscriptionState("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED"), "revoked");
  assertEquals(mapGoogleSubscriptionState("UNRECOGNIZED_STATE"), "revoked");
  assertEquals(mapGoogleSubscriptionState(null), "revoked");
  assertEquals(mapGoogleSubscriptionState(42), "revoked");
});

Deno.test("active subscription grants access only before expiry", () => {
  const now = Date.parse("2026-11-12T12:00:00.000Z");
  assertEquals(hasPremiumEntitlement("active", "2026-11-13T12:00:00.000Z", now), true);
  assertEquals(hasPremiumEntitlement("active", "2026-11-11T12:00:00.000Z", now), false);
  assertEquals(hasPremiumEntitlement("active", "not-a-date", now), false);
  assertEquals(hasPremiumEntitlement("active", null, now), false);
});

Deno.test("grace period and cancelled paid-through access are retained", () => {
  const now = Date.parse("2026-11-12T12:00:00.000Z");
  assertEquals(hasPremiumEntitlement("grace_period", "2026-11-13T00:00:00Z", now), true);
  assertEquals(hasPremiumEntitlement("canceled", "2026-11-13T00:00:00Z", now), true);
  assertEquals(hasPremiumEntitlement("canceled", "2026-11-12T11:59:59Z", now), false);
});

Deno.test("pending, hold, paused, expired and revoked never grant access", () => {
  const now = Date.parse("2026-11-12T12:00:00.000Z");
  for (const state of ["pending", "on_hold", "paused", "expired", "revoked"] as const) {
    assertEquals(hasPremiumEntitlement(state, "2026-12-12T12:00:00Z", now), false);
  }
});

Deno.test("only the approved product and base plans are accepted", () => {
  assertEquals(isAllowedDailySathiPlan("dailysathi_premium", "monthly"), true);
  assertEquals(isAllowedDailySathiPlan("dailysathi_premium", "six_months"), true);
  assertEquals(isAllowedDailySathiPlan("dailysathi_premium", "annual"), true);
  assertEquals(isAllowedDailySathiPlan("other_product", "monthly"), false);
  assertEquals(isAllowedDailySathiPlan("dailysathi_premium", "weekly"), false);
  assertEquals(isAllowedDailySathiPlan(null, "annual"), false);
});
