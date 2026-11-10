# DailySathi marketplace backend (Supabase)

This folder contains the initial database contract for the shared Local Services marketplace.

## What this provides
- Customers can browse approved listings without login.
- During the initial free launch, providers can submit listings without OTP; submissions start as `pending`.
- Public discovery returns only approved listings.
- Admin moderation remains restricted to trusted admin claims.
- Admin moderation uses a trusted Supabase Auth `app_metadata.role = admin` claim.
- Search indexes cover category/city and area.

## Setup
1. Create a Supabase project.
2. Phone authentication and an SMS provider are not required for the initial free launch.
3. Open SQL Editor and run the updated `schema.sql`. If the table already exists, the script also makes `owner_id` nullable for anonymous submissions.
4. Keep the project URL and anon/publishable key in app configuration. Never put a service-role key in the Android app.
5. Set the trusted `app_metadata.role` claim to `admin` only for designated moderators using a secure server-side admin process. Never let a client set this claim.
6. Test anonymous provider submission, pending status, admin approval, and public listing access before launch.

## Important launch safeguards
- Do not publish provider contact details until the provider has consented and the listing is approved.
- Add a report/block process and a support contact before public launch.
- Establish a moderation and removal process for fraudulent, misleading, unsafe, or outdated listings.
- A provider's phone number is public on approved listings; tell providers this clearly during registration.
- Do not store OTPs or service-role secrets in the app.
- Configure Supabase backups, retention, and project access controls.

## Android integration status
The Android app uses the Supabase project URL and publishable key for public listing reads and pending provider submissions. Phone OTP is intentionally deferred. Keep the service-role key out of the app.


## Local Android build configuration
Create or edit `local.properties` in the repository root on the development machine (this file is ignored by Git) and add:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
```

Do not add the service-role key. The Android build reads these values into BuildConfig. A build without them still compiles, but cloud marketplace requests remain disabled and the app displays a setup notice.

For GitHub Actions builds, add repository Actions secrets named `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`, then map them into a generated local.properties file before the Gradle build. Never commit credentials to source control.
