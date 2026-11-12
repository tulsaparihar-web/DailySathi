// DailySathi Google Play subscription verification.
// Deploy as: supabase functions deploy verify-play-purchase
// Required Edge Function secrets:
// SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_SERVICE_ROLE_KEY,
// GOOGLE_PLAY_SERVICE_ACCOUNT_JSON, GOOGLE_PLAY_PACKAGE_NAME
//
// Android must send the authenticated Supabase access token and set
// obfuscatedExternalAccountId to lowercase SHA-256 hex of the Supabase user UUID.
// Never trust client-supplied price, expiry, order status, user ID, or entitlement.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";
import { hasPremiumEntitlement, isAllowedDailySathiPlan, mapGoogleSubscriptionState } from "../_shared/subscription-state.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });

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

async function googleAccessToken(serviceAccount: {
  client_email: string;
  private_key: string;
  token_uri?: string;
}): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claim = base64Url(new TextEncoder().encode(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: serviceAccount.token_uri || "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  })));
  const unsigned = header + "." + claim;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  const assertion = unsigned + "." + base64Url(new Uint8Array(signature));
  const response = await fetch(serviceAccount.token_uri || "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) throw new Error("Google OAuth token exchange failed");
  const result = await response.json();
  if (typeof result.access_token !== "string") throw new Error("Google OAuth returned no access token");
  return result.access_token;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const packageName = Deno.env.get("GOOGLE_PLAY_PACKAGE_NAME")!;
    const serviceAccountRaw = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON")!;
    if (!supabaseUrl || !anonKey || !serviceRoleKey || !packageName || !serviceAccountRaw) {
      return json(503, { error: "Subscription verification is not configured" });
    }

    const authorization = req.headers.get("Authorization");
    if (!authorization?.startsWith("Bearer ")) return json(401, { error: "Sign-in required" });

    const userClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: userResult, error: userError } = await userClient.auth.getUser();
    if (userError || !userResult.user) return json(401, { error: "Invalid or expired session" });
    const userId = userResult.user.id;

    const input = await req.json();
    const productId = input?.productId;
    const basePlanId = input?.basePlanId;
    const purchaseToken = input?.purchaseToken;
    if (!isAllowedDailySathiPlan(productId, basePlanId) ||
        typeof purchaseToken !== "string" || purchaseToken.length < 20 || purchaseToken.length > 4096) {
      return json(400, { error: "Invalid purchase details" });
    }

    const serviceAccount = JSON.parse(serviceAccountRaw);
    const googleToken = await googleAccessToken(serviceAccount);
    const apiUrl = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" +
      encodeURIComponent(packageName) + "/purchases/subscriptionsv2/tokens/" +
      encodeURIComponent(purchaseToken);
    const playResponse = await fetch(apiUrl, {
      headers: { Authorization: "Bearer " + googleToken },
    });
    if (!playResponse.ok) {
      // Do not echo Google's response or purchase token to the client.
      return json(400, { error: "Google Play could not verify this purchase" });
    }
    const purchase = await playResponse.json();
    const lineItems = Array.isArray(purchase.lineItems) ? purchase.lineItems : [];
    const line = lineItems.find((item: Record<string, unknown>) =>
      item.productId === productId &&
      (item.offerDetails as Record<string, unknown> | undefined)?.basePlanId === basePlanId
    );
    if (!line) return json(403, { error: "Purchase product or plan does not match" });

    const expectedAccountId = await sha256Hex(userId);
    const externalIds = purchase.externalAccountIdentifiers || {};
    if (externalIds.obfuscatedExternalAccountId !== expectedAccountId) {
      return json(403, { error: "Purchase is not linked to this DailySathi account" });
    }

    const purchaseState = mapGoogleSubscriptionState(purchase.subscriptionState);
    const expiry = line.expiryTime ? new Date(line.expiryTime).toISOString() : null;
    const isEntitled = hasPremiumEntitlement(purchaseState, expiry);
    const tokenHash = await sha256Hex(purchaseToken);
    const orderId = typeof purchase.latestOrderId === "string" ? purchase.latestOrderId : null;
    const acknowledgementState = purchase.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
      ? "acknowledged" : "pending";

    const admin = createClient(supabaseUrl, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    // Acknowledge only after Google confirms a real, account-bound purchase.
    if (acknowledgementState === "pending" && purchaseState === "active") {
      const ackUrl = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" +
        encodeURIComponent(packageName) + "/purchases/subscriptions/" +
        encodeURIComponent(productId) + "/tokens/" + encodeURIComponent(purchaseToken) + ":acknowledge";
      const ack = await fetch(ackUrl, {
        method: "POST",
        headers: { Authorization: "Bearer " + googleToken, "Content-Type": "application/json" },
        body: JSON.stringify({}),
      });
      if (!ack.ok) return json(502, { error: "Purchase verified but acknowledgement is pending; retry verification" });
    }

    const { data: existing, error: lookupError } = await admin
      .from("play_subscriptions")
      .select("id")
      .eq("purchase_token_sha256", tokenHash)
      .maybeSingle();
    if (lookupError) throw new Error("Could not read subscription record");

    const record = {
      user_id: userId,
      product_id: productId,
      base_plan_id: basePlanId,
      google_order_id: orderId,
      purchase_token_sha256: tokenHash,
      purchase_state: purchaseState,
      acknowledgement_state: acknowledgementState === "pending" && purchaseState === "active"
        ? "acknowledged" : acknowledgementState,
      started_at: purchase.startTime ? new Date(purchase.startTime).toISOString() : null,
      expires_at: expiry,
      auto_renewing: line.autoRenewingPlan?.autoRenewEnabled === true,
      last_verified_at: new Date().toISOString(),
    };
    const write = existing
      ? await admin.from("play_subscriptions").update(record).eq("id", existing.id)
      : await admin.from("play_subscriptions").insert(record);
    if (write.error) throw new Error("Could not save verified subscription");

    const event = await admin.from("play_purchase_events").insert({
      user_id: userId,
      event_source: "android_client",
      event_type: "purchase_verified_" + purchaseState,
      google_event_id: null,
      order_id: orderId,
      event_payload: {
        product_id: productId,
        base_plan_id: basePlanId,
        subscription_state: purchase.subscriptionState,
        expiry_time: expiry,
        acknowledgement_state: record.acknowledgement_state,
      },
      processed: true,
      processed_at: new Date().toISOString(),
    });
    if (event.error) throw new Error("Verified purchase saved, but audit event could not be recorded");

    return json(200, {
      verified: true,
      premiumActive: isEntitled,
      plan: basePlanId,
      status: purchaseState,
      expiresAt: expiry,
      orderId,
    });
  } catch (_error) {
    // Keep secrets, raw tokens, Google payloads and internal error details out of responses.
    return json(500, { error: "Unable to verify subscription right now" });
  }
});
