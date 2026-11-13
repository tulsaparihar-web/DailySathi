# आस-पास: लोकल रिपेयर और पुराना सामान

**A product of Parihar Digital Labs**  
*आपके आस-पास की मदद, भरोसे के साथ।*

आस-पास is a neighbourhood-first everyday utility and local marketplace application for India. It is being developed Android-first with Kotlin, Jetpack Compose and an offline-first data layer.

## Product scope
- Smart reminders with one-time and recurring schedules
- Calendar and task views
- Health, medication and appointment reminders with optional provider, location and preparation details
- Hindi and English interface
- Local Services Finder for electricians, plumbers, masons, carpenters, painters, cleaners, helpers, AC and appliance technicians, and drivers
- JSON backup and restore for reminders, appointment details, and the local provider directory (including favorites, ratings and feedback)

## Explicitly out of scope
- Family task manager, shared family responsibilities and family profiles
- Document management and renewal tracking
- Bills and EMI management

## Local Services Finder
The initial release opens category-based nearby searches in Google Maps. Users can optionally enter an area or city, or leave it blank to search near their current location. Provider listings, availability, ratings and contact information are supplied by Maps and are not independently verified by DailySathi.

## Privacy and data handling
Reminder, appointment and local provider records are stored on the device. Backup files (format version 3) are created only when the user exports them and are saved to a location selected by the user. Restore validates reminder schedules and record identifiers before applying imported data; version 1 and 2 backups remain supported. Local Services searches are handed off to Google Maps. The app does not currently require a DailySathi account or a hosted application backend.

## Technology
- Kotlin + Jetpack Compose + Material 3
- Room / SQLite for local persistence
- Android notifications and background scheduling
- Hindi and English interface
- Google Play Billing and AdMob planned for later milestones; not enabled in the current build

## Build
Open this repository in Android Studio (JDK 17). Allow Gradle sync, install the Android SDK platform/build tools requested by the project, then run the `app` configuration.

GitHub Actions builds a debug APK on pushes and pull requests. APKs are uploaded as workflow artifacts when the build succeeds.

## Brand
- Parent brand: Parihar Digital Labs
- Product: DailySathi
- Tagline: Your Everyday Life Assistant
- Product accent: `#2876C7`
- Parent navy: `#123B50`
- Parent teal: `#168D87`
