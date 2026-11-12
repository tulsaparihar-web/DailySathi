# DailySathi monetization backend — setup and security contract

This folder defines the initial Supabase data contract for Google Play subscriptions. It does not turn on billing or grant Premium access by itself.

## Subscription catalogue

Create one Google Play subscription product:

- Product ID: `dailysathi_premium`

Create three base plans (IDs must match exactly):

| Base plan ID | Intended price | Billing period |
|---|---:|---|
| `monthly` | ₹99 | P1M |
| `six_months` | ₹499 | P6M |
| `annual` | ₹999 | P1Y |

Configure India availability and actual localized prices in Play Console. Confirm renewal type and cancellation terms before publishing. The app must display the Play-provided price/period at purchase time rather than treating these suggested amounts as guaranteed prices.

## Database setup

1. Create a Supabase project.
2. Run `schema.sql` for the Local Services marketplace if that feature is enabled.
3. Run `monetization.sql` in the Supabase SQL Editor.
4. Enable Supabase Phone Auth only after configuring an SMS provider and testing OTP delivery, rate limits and costs.
5. Keep `SUPABASE_SERVICE_ROLE_KEY` and Google Play service-account credentials in server-side Edge Function secrets only. Never place them in Android, BuildConfig, Git, or a downloadable config file.

## Account and personal data

- Require an authenticated Supabase account before attaching a purchase to a user.
- Ask for display name and mobile number only when needed; explain the purpose and obtain consent.
- Use Supabase Auth's verified phone state; do not trust a client-supplied `mobile_verified` flag.
- Use E.164 phone format (for India, e.g. +91XXXXXXXXXX).
- Do not collect/store UPI PINs, card data, bank credentials or payment screenshots.
- Provide account/data deletion and a privacy notice before production launch.
- Keep profile writes behind a trusted Edge Function; the mobile client has read-only access to its own profile and subscription status.

## Purchase verification Edge Function (source added)

Function source: `functions/verify-play-purchase/index.ts`

Deploy only after the Supabase project and Google Play Developer API access are ready:

```bash
supabase functions deploy verify-play-purchase
supabase secrets set GOOGLE_PLAY_PACKAGE_NAME=com.parihardigitallabs.dailysathi
supabase secrets set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON='<service-account-json>'
```

The Supabase project supplies `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY` to Edge Functions. Keep all server secrets in Supabase secrets; do not add them to the Android project or GitHub source.

The function:
- Requires a valid signed-in Supabase user.
- Accepts only the configured product and known base-plan IDs.
- Calls Google Play Developer API and validates product, base plan, purchase state, expiry and account binding.
- Requires `obfuscatedExternalAccountId` to equal lowercase SHA-256 hex of the authenticated Supabase user UUID. The Android Billing integration must set this value when launching the purchase flow.
- Acknowledges eligible active purchases and writes the verified subscription projection plus an audit event.
- Stores only a SHA-256 fingerprint of the purchase token in the current schema; it never returns or logs the raw token.

The function source is a first server-side verification step, not a production-ready complete billing system. It has not been deployed against a live Play Console or Supabase project. Add and test RTDN handling, encrypted token retention for reconciliation, immutable financial/order records, and operational monitoring before enabling live purchases. A canceled auto-renewing subscription retains access until its verified paid-through expiry.

## Required server-side functions (next implementation)

1. `verify-play-purchase`: accept authenticated user ID, product ID, base-plan ID and purchase token from the Android Billing Library; verify the token with the Google Play Developer API; check package name, product/base plan, purchase state, expiry and account binding; acknowledge eligible purchases; persist only verified entitlement. Store a SHA-256 token fingerprint in the audit projection; if the raw token must be retained for later Google API calls, encrypt it server-side and never return it to the client.
2. `google-play-rtdn`: receive Pub/Sub push notifications, validate the push/authentication envelope, deduplicate message/event IDs, then re-query Google Play Developer API. Treat notifications as triggers, not proof of entitlement.
3. `refresh-subscription`: periodically re-check active subscriptions and reconcile cancellation, renewal, grace period, hold, pause, expiry and revocation.
4. `account-profile`: update display name and contact consent; derive mobile verification from the authenticated Supabase user record.
5. Admin reporting: expose only a role-protected dashboard/API with user name, verified mobile (when consented), plan, order reference, amount/currency, start/expiry and status. Never expose service-role credentials or raw purchase tokens.

## Purchase event and record rules

- One subscription record is a current entitlement projection, not a substitute for a transaction ledger.
- Append each purchase/renewal/cancel/refund/revoke/hold/recovery event to `play_purchase_events`.
- For complete finance reconciliation, add a separate immutable order/financial ledger populated from verified Google Play order data. Do not infer money received from a client callback.
- Do not activate Premium from QR scans, screenshots, manually entered UPI references, or client-side booleans.
- Donations remain separate from subscriptions and do not grant Premium.

## Android integration status

The current Android app has a Premium plan preview only. Purchase buttons are intentionally disabled until Play Console products, Supabase project, server-side Google Play verification and account sign-in are configured. The current marketplace Supabase key does not imply that monetization tables or Edge Functions are deployed.

## Go-live checklist

- [ ] Play Console subscription product and base plans created and reviewed
- [ ] Supabase project configured and SQL migration applied
- [ ] Phone Auth/SMS provider configured and tested (or another supported sign-in method selected)
- [ ] Edge Functions deployed with least-privilege secrets
- [ ] Google Play Developer API access and Pub/Sub RTDN configured
- [ ] Privacy policy, data retention, account deletion and support process published
- [ ] License testers and internal testing track used for purchase/restore/refund tests
- [ ] Server-side entitlement tests pass before enabling purchase buttons
