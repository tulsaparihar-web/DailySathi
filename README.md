# आस-पास (DailySathi)

**A product of Parihar Digital Labs**  
*आपकी रोज़मर्रा की ज़िंदगी का सच्चा साथी।*

आस-पास is an Android-first everyday-life companion for people and households across India. Its purpose is practical: help people remember important things, find nearby help, make everyday decisions easier, and access useful tools without needing several complicated apps.

## Product principles

- Solve frequent, real-world problems before adding novelty features.
- Keep essential personal organizer features usable offline.
- Make Hindi and English equally first-class.
- Work well on affordable Android phones and slower connections.
- Ask for location, contacts, microphone and notification permissions only when a feature needs them.
- Keep essential functionality free; monetization must not block basic help.
- Be transparent: never imply a provider, listing, review or public-information source is verified unless it has actually been checked.

## Product pillars

1. **Daily Life Organizer** — reminders, recurring tasks, calendar, medicine and appointment reminders, household maintenance, vehicle maintenance, notes and lists.
2. **Local Services** — discover nearby electricians, plumbers, masons, carpenters, painters, cleaners, helpers, drivers and appliance technicians; compare indicative rates and contact providers.
3. **Buy / Sell / Swap** — local second-hand household goods, with safe discovery, listing moderation and direct buyer/seller contact.
4. **Emergency & Quick Help** — one-tap official emergency calling, user-selected emergency contacts and optional location sharing.
5. **Everyday Tools** — calculators, unit conversion, age/date/discount/GST calculations and shopping checklists.
6. **Neighbourhood Help** — lost and found, give-away/donation posts and community assistance, introduced only with moderation and abuse controls.
7. **Daily Information** — useful official helplines, local weather and public-service links with source/date transparency.
8. **Accessibility & Assistance** — large text, readable Hindi, voice input/output, low-data behavior and optional AI assistance where it demonstrably helps.

## Current implementation status

The current Android app already contains a local reminder/calendar/tasks experience, recurring reminder scheduling, Hindi/English selection, local-services Google Maps hand-off, and JSON backup/restore for reminders and the local provider directory. A Supabase marketplace foundation and Android marketplace API are present in the repository, but this does **not** mean the complete marketplace user journey is finished.

The app currently has a five-tab navigation structure: Home, Calendar, Tasks, Services and Settings. Existing functionality must be preserved while new modules are added.

## Delivery roadmap

See [docs/PRODUCT_ROADMAP.md](docs/PRODUCT_ROADMAP.md) for milestone scope, acceptance criteria and release gates.

## Explicitly out of scope

- Family task manager, shared family responsibilities and family profiles.
- Document management and renewal tracking.
- Bills, EMI management or financial-account aggregation.
- An unrestricted social-media feed.
- Mandatory AI chatbot usage for ordinary app operations.
- In-app escrow, shipping or payments between marketplace users in the initial local resale release.

## Technology

- Kotlin, Jetpack Compose and Material 3
- Room / SQLite for local persistence
- Android notifications and background scheduling
- Supabase / PostgreSQL for optional marketplace capabilities
- Hindi and English interface
- Google Maps hand-off for local discovery
- Google Play Billing and AdMob are separate, later release work; never assume they are live just because backend scaffolding exists.

## Privacy and safety

Reminder, appointment and personal organizer records remain on-device unless the user explicitly exports them. Backup files are created only on user request and saved to a location selected by the user. Nearby search should use location only while the feature is in use, return approximate distance, and never expose private home coordinates. Provider verification labels must be evidence-backed. Public listings need moderation, reporting, blocking and clear anti-scam guidance before launch.

## Build

Open this repository in Android Studio with JDK 17. Allow Gradle sync, install the Android SDK platform/build tools requested by the project, then run the `app` configuration.

GitHub Actions builds a debug APK and runs JVM unit tests. A successful CI build is not a substitute for testing notifications, permissions, location behavior, marketplace backend integration, or the app on physical Android devices.

## Brand

- Parent brand: Parihar Digital Labs
- Product: DailySathi / आस-पास
- Tagline: आपकी रोज़मर्रा की ज़िंदगी का सच्चा साथी।
- Product accent: `#2876C7`
- Parent navy: `#123B50`
- Parent teal: `#168D87`
