# Outfit Tracker

A Hebrew, right-to-left Android app that records which outfits were worn when meeting a person or a forum, so the same outfit is not repeated.

The interface is Hebrew. The on-screen name for a person or forum is the string resource `entity_label` (`איש/פורום`). Rename that resource later; do not hard-code the label.

## Architecture

Each signed-in user has their own data under `users/{uid}/`:

- **entities** — `name`, `type` (`person` or `forum`)
- **clothingItems** — `description`, `category` (`shirt`, `pants`, `skirt`, `dress`, `jacket`, `shoes`, `accessory`, `other`), `photoUrl`, `hidden`
- **meetings** — `date` (`YYYY-MM-DD`), `entityId`, `entityName`, `clothingItemIds`, optional `photoUrl`, `note`

Photos are downscaled on the phone to about 1280px JPEG, then uploaded to Cloud Storage at `users/{uid}/…`. Sign-in is Firebase Auth with the user's Google account through Credential Manager, so the wardrobe survives replacing the phone. Firestore disk persistence is turned on and syncs when the network is back.

Garment detection goes through `GarmentDetector`. The implementation calls Gemini with Firebase AI Logic (`firebase-ai`). If the call fails or returns nothing, the meeting screen stays on manual selection.

`app/google-services.json` is committed for this personal debug app. The Google Services plugin still applies only when that file exists, so a checkout without it still builds and shows a setup message instead of crashing. When the file is present, sign-in uses the generated string `default_web_client_id` (the OAuth web client, `client_type` 3).

Debug builds are signed with the committed PKCS12 keystore `app/outfit-debug.keystore` (alias `outfitdebug`). Its certificate SHA-1 is `86:E8:3F:35:71:71:99:32:78:95:34:A0:6F:99:D9:45:B3:44:4E:3D`, which is registered on the Firebase Android app, so Google sign-in works for every local and CI debug APK. This key is for debug installs only. Before any wider distribution, replace it with a private key kept in a secret store, and stop publishing the keystore and its password.

## Setup

The Firebase project, Android app (`com.galmerom.outfittracker`), SHA-1/SHA-256 registration, Google sign-in provider, and Firestore/Storage rules are already in place. Firebase AI Logic (Gemini) still has to be enabled in that project if garment detection should run.

Build and install a debug APK:

```
./gradlew assembleDebug
```

The debug APK from GitHub Actions is the artifact `outfit-tracker-debug-apk` on the workflow run. Each push also replaces the pre-release tag `outfit-tracker-latest`:

https://github.com/galmerom/galmerom/releases/download/outfit-tracker-latest/outfit-tracker.apk

## App

Bottom bar: log a meeting, search, wardrobe.

- Wardrobe: add and edit items with a camera or gallery photo, description, category, and a hidden flag. Hidden items stay in meeting history and can be revived from the hidden list. Selection lists omit them.
- Log a meeting: date defaults to today. The entity field searches as you type; if nothing matches exactly, create a person or a forum. Pick clothes from category menus (each row shows a photo), add a photo for AI detection, or add an item by hand. Optional note and meeting photo.
- Search: filter by entity, show the 3 newest meetings, then **הצג עוד**. A meeting opens with its photo when there is one, then the clothes with their photos, and the note.
