# DailySathi / आस-पास — Product Roadmap

## Mission

Make everyday life easier for ordinary people and households in India. Prioritize practical utility, local access, trust, affordability, accessibility and privacy over feature count.

## Delivery rules

- Preserve all working reminder, calendar, task, local-services and backup/restore behavior.
- Build in small milestones; every milestone must compile and have focused tests before it is called complete.
- Keep personal organizer data offline-first. Network-dependent modules must explain when connectivity is required.
- Do not claim a provider is verified without evidence-backed checks.
- Never expose exact private coordinates in public marketplace responses.
- No production payments, ads or subscriptions until their compliance, privacy, support and end-to-end test gates are met.
- Keep English and Hindi strings together as each feature is implemented.
- Do not silently request location, contacts, microphone or notification permissions.

## Milestones

### M0 — Repository and product audit
- [x] Confirm repository, default branch and latest CI state.
- [x] Inventory current Android modules, Room reminder storage, backup format, marketplace API and Supabase SQL.
- [x] Document current-vs-planned functionality and acceptance criteria.
- [ ] Run the full Android build and unit suite on the latest commit in CI.
- [ ] Record physical-device baseline for notifications, reboot recovery, backup/restore and language switching.

### M1 — Stabilize the everyday organizer
- Preserve one-time and recurring reminders, calendar, tasks, medication/appointment metadata, notification scheduling and backup/restore.
- Add household maintenance and vehicle maintenance reminder templates without creating a separate complicated subsystem.
- Add reminder search/filter and a reliable "today / upcoming / overdue" summary.
- Add tests for recurrence edge cases, edits, completion, reboot and time-zone changes.

**Acceptance:** existing reminder and backup tests pass; no data loss on upgrade; reminder alarms are scheduled/cancelled correctly.

### M2 — Everyday tools and quick help
- Add offline calculator, percentage/discount, GST, unit conversion, age/date difference and electricity-use estimate tools.
- Add grocery/shopping checklist and quick notes with local persistence.
- Add emergency screen with one-tap calls to official India emergency numbers (including 112); explain that availability and service coverage vary.
- Let users add/edit emergency contacts locally; request contact/location permissions only for optional actions.
- Add large-text/accessibility support and voice input as a progressive enhancement.

**Acceptance:** tools work offline; calculations have unit tests; emergency calling requires a deliberate user tap; no automatic SOS/call or hidden location sharing.

### M3 — Local Services discovery
- Separate Repair Near Me from other local-services discovery.
- Add category, distance radius, area/city fallback, provider details, indicative rate cards and call/WhatsApp actions.
- Make verification level explicit and evidence-backed.
- Add favorites and reviews with eligibility/anti-abuse controls.
- Preserve Google Maps hand-off as a fallback if the backend is not configured.

**Acceptance:** permission denied and backend unavailable states are understandable; approximate distance only; no false verification claims; provider data and favorites persist correctly.

### M4 — Buy / Sell / Swap marketplace
- Add browse, search, category/price/radius filters, item details and seller contact.
- Add listing creation/editing, multiple photos, reserve/sold states and expiry.
- Keep transactions and pickup directly between users in the initial release; no in-app escrow or shipping promises.
- Require phone verification for posting and moderation approval for public listings.
- Add report/block, prohibited-item rules, anti-scam guidance and privacy-safe area display.

**Acceptance:** unauthenticated browsing is read-only; posting requires verified account; private coordinates/phone data are never exposed by public listing queries; moderation and report flows work end-to-end.

### M5 — Trust, safety and privacy
- Add account lifecycle and secure OTP authentication where required.
- Add block/report, moderation queues, rate limits, spam/duplicate detection and review eligibility.
- Add privacy controls, data export/deletion, support/feedback and transparent verification badges.
- Review Supabase RLS policies and storage upload policies with negative tests.

**Acceptance:** access-control tests prove users cannot read/write another user's private data; abuse reports are actionable; account deletion and data handling are documented.

### M6 — Neighbourhood assistance and useful information
- Add lost/found, give-away/donation and moderated neighbourhood help.
- Add official helplines/public-service links and source/date metadata.
- Add optional local weather only with a reliable source and clear permission/location behavior.
- Do not introduce an unrestricted social feed.

**Acceptance:** moderation, reporting and blocking are active before public posting; public information has provenance and a review/update process.

### M7 — Accessibility, localization and performance
- Complete Hindi/English coverage and verify strings in all screens and notifications.
- Add scalable text, TalkBack labels, contrast, touch-target and keyboard/focus checks.
- Optimize startup, memory, image sizes, network retries and low-connectivity behavior.
- Ensure essential organizer and tools remain usable offline.

**Acceptance:** accessibility checklist passes on small screens; no blocking network dependency for local organizer; no severe startup/performance regressions.

### M8 — Release readiness
- Full unit, integration, UI and physical-device test pass.
- Test Android 13+ notification permission and Android 12+ exact alarm behavior as applicable.
- Test fresh install, upgrade, backup/restore, denied permissions, low connectivity and app data deletion.
- Publish privacy policy, terms, community rules, support contact and account deletion process.
- Configure production signing, Play Console listing, Data Safety disclosures and staged rollout.
- Keep ads/subscriptions disabled until separate billing/ad consent and end-to-end release gates pass.

**Acceptance:** signed release candidate installs/upgrades cleanly; all P0/P1 defects closed; privacy and store compliance reviewed; rollback and support process ready.

## Priority order

M0 → M1 → M2 → M3 → M4 → M5 → M6 → M7 → M8.

M2 utility and emergency tools should be delivered before expanding marketplace breadth. Trust and safety is a release blocker for public marketplace/community posting, not a cosmetic later enhancement.
