export type PurchaseState =
  | "pending" | "active" | "grace_period" | "on_hold"
  | "paused" | "canceled" | "expired" | "revoked";

const GOOGLE_STATE_MAP: Record<string, PurchaseState> = {
  SUBSCRIPTION_STATE_PENDING: "pending",
  SUBSCRIPTION_STATE_ACTIVE: "active",
  SUBSCRIPTION_STATE_IN_GRACE_PERIOD: "grace_period",
  SUBSCRIPTION_STATE_ON_HOLD: "on_hold",
  SUBSCRIPTION_STATE_PAUSED: "paused",
  SUBSCRIPTION_STATE_CANCELED: "canceled",
  SUBSCRIPTION_STATE_EXPIRED: "expired",
};

export function mapGoogleSubscriptionState(value: unknown): PurchaseState {
  return typeof value === "string" ? (GOOGLE_STATE_MAP[value] ?? "revoked") : "revoked";
}

export function hasPremiumEntitlement(
  state: PurchaseState,
  expiryTime: string | null | undefined,
  nowMillis = Date.now(),
): boolean {
  if (!["active", "grace_period", "canceled"].includes(state) || !expiryTime) return false;
  const expiryMillis = Date.parse(expiryTime);
  return Number.isFinite(expiryMillis) && expiryMillis > nowMillis;
}

export function isAllowedDailySathiPlan(productId: unknown, basePlanId: unknown): boolean {
  return productId === "dailysathi_premium" &&
    ["monthly", "six_months", "annual"].includes(String(basePlanId));
}
