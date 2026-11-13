# DailySathi QA and pre-release test plan

This checklist separates automated checks from real-device checks. A green GitHub Actions run proves that the debug APK builds and JVM unit tests pass; it does not prove notification delivery, device-specific behavior, Play Billing, or live Supabase integration.

## Automated checks currently available

- GitHub Actions Android CI: assemble debug APK and run JVM unit tests.
- Reminder model and recurrence tests.
- Backup encoding/decoding and legacy-format tests.
- Supabase Edge Function Deno type-check for purchase verification and Google Play RTDN.
- SQL schema verification in the configured Supabase project: the three monetization tables exist with RLS enabled, and `has_active_daily_sathi_premium` exists.

## Manual Android device checklist

### Installation and data preservation
- [ ] Install the current debug APK on a test device.
- [ ] Confirm app opens and navigates between all six tabs (Home, Calendar, Tasks, Services, Tools, Settings).
- [ ] Create sample reminders and local service entries.
- [ ] Install a later build over the current build and confirm app data remains.
- [ ] Confirm app data survives force-stop and device restart.

> Upgrade installation requires the same application ID, a higher versionCode, and the same signing certificate. GitHub-hosted debug signing may not be stable between runs. Do not use repeated CI debug APKs as an upgrade test until a persistent signing key is configured. Do not uninstall an existing app containing data just to work around a signature mismatch.

### Reminders, tasks and calendar
- [ ] Create, edit and delete a one-time reminder.
- [ ] Create daily and weekly reminders.
- [ ] Create a monthly reminder on the 29th, 30th or 31st; verify month-end behavior.
- [ ] Mark a reminder complete from Home, Calendar and Tasks; verify the state stays consistent.
- [ ] Reopen a completed recurring reminder and verify the next due date/alarm.
- [ ] Verify date and time pickers show the saved values while editing.
- [ ] Verify past-due reminders do not create immediate stale alarms.
- [ ] Verify notification permission flow on Android 13 and newer.
- [ ] Verify notification delivery at the scheduled time with battery optimization/device settings documented.
- [ ] Reboot the device and verify pending reminders are rescheduled.
- [ ] Change device time/time zone and verify schedules remain sensible.
- [ ] Keep the app across midnight and verify calendar date refresh.

### Local Services
- [ ] Open every service category and verify Google Maps hand-off.
- [ ] Search with an area/city and with the area left blank.
- [ ] Add/edit/remove a local provider entry if the current UI supports it.
- [ ] Verify favorites, ratings and feedback persist after app restart.
- [ ] Verify the app clearly distinguishes user-provided provider information from independently verified information.

### Everyday Tools
- [ ] Verify discount calculation at 0%, 100%, and a typical rate; reject negative amounts and rates outside 0–100%.
- [ ] Verify GST amount and total at common rates (5%, 12%, 18%, 28%).
- [ ] Verify percentage calculation with decimal values and zero values.
- [ ] Verify unit conversion output for kg→g, km→m and litres→mL; confirm the input unit label is clear.
- [ ] Add, complete, uncomplete and remove shopping checklist items.
- [ ] Force-stop and reopen the app; verify shopping checklist entries and completion states persist.
- [ ] Tap emergency shortcuts and verify they open the dialer (not an automatic call); cancel without dialing.
- [ ] Verify emergency numbers and explanatory disclaimer in Hindi and English.
- [ ] Verify the Tools tab is usable on a small screen with the keyboard open and with large font settings.

### Language, backup and restore
- [ ] Switch English to Hindi and back; verify selection persists after restart.
- [ ] Export a JSON backup containing reminders and provider data.
- [ ] Restore the backup on the same device or a clean test profile.
- [ ] Test supported legacy backup formats (versions 1 and 2) and current format (version 3).
- [ ] Try malformed JSON and invalid schedules; verify restore is rejected safely without losing existing data.
- [ ] Verify a restore failure leaves the previous data intact.

### Support and monetization preview
- [ ] Open Settings and verify support email draft opens with the correct recipient and subject.
- [ ] Verify UPI ID copy action works; do not treat a UPI transfer as a subscription.
- [ ] Verify Premium plans are displayed as preview only.
- [ ] Verify Subscribe and Restore Purchases remain disabled until billing is configured.
- [ ] Verify no ad SDK or live payment flow is active in this build.

## Backend checks before enabling paid features

- [ ] Apply the Supabase monetization SQL migration and verify RLS on all three tables.
- [ ] Configure Supabase Edge Function secrets outside source control.
- [ ] Deploy and test `verify-play-purchase` with Play license testers and a real test purchase token.
- [ ] Configure authenticated Pub/Sub OIDC push and test `google-play-rtdn`.
- [ ] Test duplicate RTDN delivery, renewal, cancellation, grace period, account hold, pause, expiry and refund/revocation.
- [ ] Implement secure reconciliation for missed notifications; the current schema stores a token hash only.
- [ ] Implement Android account sign-in and Billing Library integration with account obfuscation.
- [ ] Add verified financial/order ledger and admin reporting before launch.
- [ ] Test restore and entitlement on a second device/account session.
- [ ] Publish privacy policy, account deletion and support process.

## Release gate

Do not enable subscriptions or publish a production release until all applicable device, account, purchase, notification, restore, cancellation, refund and privacy tests have passed. Play Console registration and release signing are separate prerequisites and may be completed later.
