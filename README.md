# DailySathi

**A product of Parihar Digital Labs**  
*Technology that makes life easier.*

DailySathi is an India-focused personal productivity and everyday utility application. It is being developed Android-first with Kotlin, Jetpack Compose and an offline-first data layer.

## Product scope
- Smart reminders and recurring schedules
- Local Services Finder: electricians, plumbers, masons, carpenters, painters, cleaners, helpers, AC and appliance technicians, drivers and more
- Family task manager and shared responsibilities
- Calendar and important dates
- Health and appointment reminders
- Family profiles and backup / restore
- Hindi and English interface

## Explicitly out of scope
- Document management and renewal tracking
- Bills and EMI management

## Local Services Finder
The initial release opens category-based nearby searches in Google Maps. Users can optionally enter an area or city, or leave it blank to search near their current location. Provider listings, availability, ratings and contact information are supplied by Maps and are not independently verified by DailySathi.

## Technology
- Kotlin + Jetpack Compose + Material 3
- Room / SQLite for local persistence
- Android notifications and background scheduling
- Hindi and English interface
- Google Play Billing and AdMob planned for later milestones

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
