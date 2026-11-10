# DailySathi marketplace backend (Supabase)

This folder contains the initial database contract for the shared Local Services marketplace.

## What this provides
- Provider profiles linked to a verified Supabase Auth account.
- Listings start as `pending`; they are not visible to customers until approved.
- Public discovery returns only approved listings.
- Providers can edit or delete their own pending submissions.
- Admin moderation uses a trusted Supabase Auth `app_metadata.role = admin` claim.
- Search indexes cover category/city and area.

## Setup
1. Create a Supabase project.
2. In Authentication > Providers, enable Phone and configure an SMS provider. SMS delivery may incur charges depending on provider and country.
3. Open SQL Editor and run `schema.sql`.
4. Keep the project URL and anon/publishable key in app configuration. Never put a service-role key in the Android app.
5. Set the trusted `app_metadata.role` claim to `admin` only for designated moderators using a secure server-side admin process. Never let a client set this claim.
6. Test provider sign-in, pending submission, admin approval, and public listing access with separate test accounts before launch.

## Important launch safeguards
- Do not publish provider contact details until the provider has consented and the listing is approved.
- Add a report/block process and a support contact before public launch.
- Establish a moderation and removal process for fraudulent, misleading, unsafe, or outdated listings.
- A provider's phone number is public on approved listings; tell providers this clearly during registration.
- Do not store OTPs or service-role secrets in the app.
- Configure Supabase backups, retention, and project access controls.

## Android integration status
The current Android app still uses an on-device provider list. This SQL is the secure shared-backend foundation, not a live connection. The cloud integration requires the project's URL/key and the chosen SMS provider configuration; those values must be supplied through secure build configuration, not committed to Git.


## Local Android build configuration
Create or edit `local.properties` in the repository root on the development machine (this file is ignored by Git) and add:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
```

Do not add the service-role key. The Android build reads these values into BuildConfig. A build without them still compiles, but cloud marketplace requests remain disabled and the app displays a setup notice.

For GitHub Actions builds, add repository Actions secrets named `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`, then map them into a generated local.properties file before the Gradle build. Never commit credentials to source control.
