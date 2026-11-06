# DailySathi

**A product of Parihar Digital Labs**  
*Technology that makes life easier.*

DailySathi is an India-focused personal and family utility application. It is being developed Android-first with Kotlin, Jetpack Compose and an offline-first data layer.

## Product scope
- Smart reminders and recurring schedules
- Document and renewal tracker
- Bills and EMI manager
- Family task manager
- Calendar and important dates
- Health and appointment reminders
- Family profiles and backup

## Technology
- Kotlin + Jetpack Compose + Material 3
- Room / SQLite for local persistence
- Android notifications and background scheduling
- Hindi and English interface
- Google Play Billing and AdMob planned for later milestones

## Build
Open this repository in Android Studio (JDK 17). Allow Gradle sync, install the Android SDK platform/build tools requested by the project, then run the `app` configuration.

GitHub Actions builds a debug APK on pushes and pull requests. APKs are uploaded as workflow artifacts when the build succeeds.

## Status
Initial foundation only. Notification scheduling, calendar data, family profiles, renewal/bill modules, backup, ads and subscriptions are scheduled for subsequent milestones. Do not treat this development build as a production release.

## Brand
- Parent brand: Parihar Digital Labs
- Product: DailySathi
- Tagline: Your Everyday Life Assistant
- Product accent: `#2876C7`
- Parent navy: `#123B50`
- Parent teal: `#168D87`
