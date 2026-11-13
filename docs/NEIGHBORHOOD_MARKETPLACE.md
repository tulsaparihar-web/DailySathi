# DailySathi Neighbourhood Marketplace

## Product decision
DailySathi remains free for every user. The Neighbourhood Marketplace has two sections under the existing Services tab:

1. **Repair Near Me** — approved local technicians, indicative rate cards, community ratings and direct call/WhatsApp contact.
2. **Buy · Sell · Swap** — neighbour-to-neighbour listings for used household goods, with local pickup arranged directly. No commission, shipping workflow or in-app payment.

## Radius and privacy
- Default search radius: 3 km. User may choose 2 km, 3 km or 5 km.
- Search is bounded to 2–5 km; no citywide feed.
- Device location is requested only while the feature is in use.
- Exact latitude/longitude is stored in separate private location tables with RLS enabled and no direct client policies.
- Public search RPCs return approximate distance only, never exact coordinates or a street address.
- If location permission is denied, explain why nearby search needs it and let the user retry; do not silently fall back to a broad feed.

## Trust and safety
- A technician is not labelled verified just because a listing exists. Verification levels are unverified, phone_verified, document_verified, and admin_verified; only evidence-backed levels receive a badge.
- Repair rates are indicative ranges with units (per job/hour/item), not guaranteed quotes.
- Community reviews require an authenticated account and one review per user/provider.
- Resale posts require phone-verified sign-in and start in pending moderation state.
- Add report listing, owner mark-as-reserved/sold, listing expiry and admin moderation before public launch.
- Never show a seller's home coordinates. Show neighbourhood/area and approximate distance only.
- Buyers and sellers arrange inspection and local pickup themselves. Warn users not to pay advance deposits to strangers.

## Backend foundation
Apply neighborhood_marketplace.sql after schema.sql in Supabase SQL Editor. It is additive and preserves the existing local-services tables. It creates rate cards, reviews, private location tables, resale listings, reports and radius-limited RPCs. New resale listings are pending until an administrator approves them.

## Android implementation sequence
1. Add the two-section Services experience and location permission/radius selector.
2. Connect nearby repair providers and rate/review detail screens.
3. Add resale feed, listing detail, create/edit, photo upload, report and mark sold/reserved.
4. Add phone OTP gating for write actions and retain read-only browsing.
5. Test location denial, 2/3/5 km boundaries, coordinate privacy, moderation, review uniqueness, and Hindi/English.
