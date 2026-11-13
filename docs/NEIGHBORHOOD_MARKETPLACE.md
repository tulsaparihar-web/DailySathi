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


## Android implementation status — 13 November 2026

Implemented in the current release-candidate branch:
- Dedicated bilingual Buy · Sell and Repair discovery experience in the Services tab.
- Location permission is requested only after the user taps Use location; search radius is constrained to 2, 3 or 5 km.
- Nearby repair results show distance, community rating and the stored verification level; call and WhatsApp hand-off use explicit user taps.
- Resale browsing is read-only until phone OTP verification. Posting and seller-contact disclosure require a verified Supabase session.
- Public nearby listing RPC returns neighbourhood, approximate distance and item details, but not the seller phone number. The phone is fetched through an authenticated contact RPC only after the user taps Contact.
- Signed-in users can view their own listings and mark active listings reserved or sold.
- Users can report listings with a reason and optional details.
- PostgreSQL CI applies the additive migration and checks RLS, RPC presence and that the public listing function does not return the seller contact_phone field.

Configuration and release gates still required:
- Add the Supabase project URL and publishable key to the local build configuration / GitHub secrets and enable phone OTP with an approved SMS provider.
- Apply schema.sql first and then neighborhood_marketplace.sql to the target Supabase project.
- Configure and test administrator moderation for pending service-provider and resale listings.
- Add multi-photo upload, listing edit/delete, account/session lifecycle, rate-card and review detail UI.
- Verify permission denial, location accuracy, OTP delivery, moderation, report handling and call/WhatsApp flows on physical Android devices.

Do not treat a successful debug build or local PostgreSQL migration test as proof that the production Supabase project is configured or that SMS OTP delivery works.
