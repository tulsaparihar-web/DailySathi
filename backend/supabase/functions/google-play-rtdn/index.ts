// Google Play Real-time Developer Notifications (RTDN) Pub/Sub push handler.
// Configure Pub/Sub push authentication with a dedicated service account.
// Set RTDN_AUDIENCE and RTDN_PUSH_EMAIL to the expected JWT audience and caller email.
// The notification is only a trigger: entitlement is always re-read from Google Play.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";
import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";
import { mapGoogleSubscriptionState } from "../_shared/subscription-state.ts";

const jwks = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));
const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
function pemToBytes(pem: string): ArrayBuffer {
  const body = pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}
async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
async function googleAccessToken(account: { client_email: string; private_key: string; token_uri?: string }) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claim = base64Url(new TextEncoder().encode(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: account.token_uri || "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  })));
  const unsigned = header + "." + claim;
  const key = await crypto.subtle.importKey("pkcs8", pemToBytes(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const response = await fetch(account.token_uri || "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: unsigned + "." + base64Url(new Uint8Array(signature)),
    }),
  });
  if (!response.ok) throw new Error("Google OAuth exchange failed");
  const result = await response.json();
  if (typeof result.access_token !== "string") throw new Error("Google OAuth token missing");
  return result.access_token as string;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });
  try {
    const audience = Deno.env.get("RTDN_AUDIENCE");
    const expectedEmail = Deno.env.get("RTDN_PUSH_EMAIL");
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const packageName = Deno.env.get("GOOGLE_PLAY_PACKAGE_NAME");
    const accountRaw = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON");
    if (!audience || !expectedEmail || !supabaseUrl || !serviceRoleKey || !packageName || !accountRaw) {
      return json(503, { error: "RTDN is not configured" });
    }

    const auth = req.headers.get("Authorization") || "";
    const match = auth.match(/^Bearer\s+(.+)$/i);
    if (!match) return json(401, { error: "Missing Pub/Sub identity token" });
    const { payload } = await jwtVerify(match[1], jwks, {
      issuer: ["https://accounts.google.com", "accounts.google.com"],
      audience,
    });
    if (payload.email !== expectedEmail || payload.email_verified !== true) {
      return json(403, { error: "Unexpected Pub/Sub caller" });
    }

    const envelope = await req.json();
    const message = envelope?.message;
    if (!message || typeof message.messageId !== "string" || typeof message.data !== "string") {
      return json(400, { error: "Invalid Pub/Sub envelope" });
    }
    const decoded = atob(message.data);
    const bytes = Uint8Array.from(decoded, (c) => c.charCodeAt(0));
    const notification = JSON.parse(new TextDecoder().decode(bytes));
    if (notification.packageName !== packageName) return json(200, { ignored: true });

    const admin = createClient(supabaseUrl, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const eventId = "rtdn:" + message.messageId;
    const eventType = notification.subscriptionNotification
      ? "subscription_notification_" + notification.subscriptionNotification.notificationType
      : notification.testNotification ? "test_notification" : "unknown_notification";

    // Insert first to make Pub/Sub retries idempotent. Do not persist raw purchase tokens.
    // If an earlier attempt failed after inserting the event, resume that unprocessed
    // event instead of acknowledging the retry and leaving the entitlement stale.
    const eventInsert = await admin.from("play_purchase_events").insert({
      event_source: "google_rtdn",
      event_type: eventType,
      google_event_id: eventId,
      event_payload: {
        package_name: notification.packageName,
        event_time_millis: notification.eventTimeMillis || null,
        subscription_id: notification.subscriptionNotification?.subscriptionId || null,
        notification_type: notification.subscriptionNotification?.notificationType || null,
        has_purchase_token: typeof notification.subscriptionNotification?.purchaseToken === "string",
      },
      processed: false,
    }).select("id").single();

    let eventRecordId: string;
    if (eventInsert.error?.code === "23505") {
      const priorEvent = await admin.from("play_purchase_events")
        .select("id,processed")
        .eq("google_event_id", eventId)
        .maybeSingle();
      if (priorEvent.error || !priorEvent.data) throw new Error("Could not recover prior RTDN event");
      if (priorEvent.data.processed) return json(200, { duplicate: true });
      eventRecordId = priorEvent.data.id;
    } else if (eventInsert.error || !eventInsert.data) {
      throw new Error("Could not record RTDN event");
    } else {
      eventRecordId = eventRecordId;
    }

    const sub = notification.subscriptionNotification;
    if (!sub || typeof sub.purchaseToken !== "string" || typeof sub.subscriptionId !== "string") {
      await admin.from("play_purchase_events").update({
        processed: true, processed_at: new Date().toISOString(),
      }).eq("id", eventRecordId);
      return json(200, { accepted: true, informational: true });
    }

    const tokenHash = await sha256Hex(sub.purchaseToken);
    const { data: existing, error: lookupError } = await admin
      .from("play_subscriptions")
      .select("id,user_id,product_id,base_plan_id")
      .eq("purchase_token_sha256", tokenHash)
      .maybeSingle();
    if (lookupError) throw new Error("Subscription lookup failed");

    // A token not yet linked by the authenticated purchase flow cannot grant access.
    if (!existing || existing.product_id !== sub.subscriptionId) {
      await admin.from("play_purchase_events").update({
        processed: true, processed_at: new Date().toISOString(),
        processing_error: "Purchase token is not yet linked to a verified account",
      }).eq("id", eventRecordId);
      return json(200, { accepted: true, linked: false });
    }

    const account = JSON.parse(accountRaw);
    const googleToken = await googleAccessToken(account);
    const apiUrl = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" +
      encodeURIComponent(packageName) + "/purchases/subscriptionsv2/tokens/" +
      encodeURIComponent(sub.purchaseToken);
    const playResponse = await fetch(apiUrl, { headers: { Authorization: "Bearer " + googleToken } });
    if (!playResponse.ok) throw new Error("Google Play recheck failed");
    const purchase = await playResponse.json();
    if (purchase.externalAccountIdentifiers?.obfuscatedExternalAccountId !==
        await sha256Hex(existing.user_id)) {
      throw new Error("Google Play account binding mismatch");
    }
    const line = (purchase.lineItems || []).find((item: Record<string, unknown>) =>
      item.productId === existing.product_id &&
      (item.offerDetails as Record<string, unknown> | undefined)?.basePlanId === existing.base_plan_id
    );
    if (!line) throw new Error("Verified purchase line item missing");

    const state = mapGoogleSubscriptionState(purchase.subscriptionState);
    const expiry = line.expiryTime ? new Date(line.expiryTime).toISOString() : null;
    const update = await admin.from("play_subscriptions").update({
      purchase_state: state,
      google_order_id: purchase.latestOrderId || null,
      started_at: purchase.startTime ? new Date(purchase.startTime).toISOString() : null,
      expires_at: expiry,
      auto_renewing: line.autoRenewingPlan?.autoRenewEnabled === true,
      acknowledgement_state: purchase.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
        ? "acknowledged" : "pending",
      last_verified_at: new Date().toISOString(),
    }).eq("id", existing.id);
    if (update.error) throw new Error("Subscription projection update failed");

    const eventUpdate = await admin.from("play_purchase_events").update({
      subscription_id: existing.id,
      user_id: existing.user_id,
      order_id: purchase.latestOrderId || null,
      event_payload: {
        package_name: packageName,
        notification_type: sub.notificationType,
        verified_subscription_state: purchase.subscriptionState,
        expiry_time: expiry,
      },
      processed: true,
      processed_at: new Date().toISOString(),
      processing_error: null,
    }).eq("id", eventRecordId);
    if (eventUpdate.error) throw new Error("RTDN audit update failed");
    return json(200, { accepted: true, reconciled: true });
  } catch (_error) {
    // Return non-2xx so Pub/Sub retries transient failures. Never echo token or secrets.
    return json(500, { error: "Unable to process Google Play notification" });
  }
});
