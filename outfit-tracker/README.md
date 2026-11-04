# Outfit Tracker

A Hebrew, right-to-left Android app that records which outfits were worn when meeting a person or a forum, so the same outfit is not repeated.

The interface is Hebrew. The on-screen name for a person or forum is the string resource `entity_label` (`איש/פורום`). Rename that resource later; do not hard-code the label.

## Architecture

Each signed-in user has their own data under `users/{uid}/`:

- **entities** — `name`, `type` (`person` or `forum`), `description`, `photoUrl`, `hidden`, and for a forum `memberIds` (people who belong to it). Avatars are stored at `users/{uid}/avatars/{id}.jpg`.
- **categories** — user-defined names with `sortOrder`. Seeded once with pants, shirt, then skirt, dress, jacket, shoes, accessory, and other. They can be renamed and reordered, not deleted. An untouched original seed order is migrated so pants and shirt come first.
- **clothingItems** — `description`, `categoryId`, `photoUrl`, `hidden`
- **meetings** — `date` (`YYYY-MM-DD`), `entityId`, `entityName`, `clothingItemIds`, optional `photoUrl`, `note`

Photos are downscaled on the phone to about 1280px JPEG, then uploaded to Cloud Storage at `users/{uid}/…`. A wardrobe item photo also gets a free on-device background removal (ML Kit Subject Segmentation). The original file stays beside the white-background version. Sign-in is Firebase Auth with the user's Google account through Credential Manager, so the wardrobe survives replacing the phone. Firestore disk persistence is turned on and syncs when the network is back.

Garment detection goes through `GarmentDetector`. It sends the meeting photo and the existing wardrobe to Gemini (`gemini-3.8-flash`) and only suggests ids of items already in the wardrobe. Nothing is added until it is approved. The meeting photo is still saved with the meeting. A failed call shows a fixed Hebrew message. Debug builds install the App Check debug provider. Detection still fails until the Firebase project has Gemini credit.

`app/google-services.json` is committed for this personal debug app. The Google Services plugin still applies only when that file exists, so a checkout without it still builds and shows a setup message instead of crashing. When the file is present, sign-in uses the generated string `default_web_client_id` (the OAuth web client, `client_type` 3).

Debug builds are signed with the committed PKCS12 keystore `app/outfit-debug.keystore` (alias `outfitdebug`). Its certificate SHA-1 is `86:E8:3F:35:71:71:99:32:78:95:34:A0:6F:99:D9:45:B3:44:4E:3D`, which is registered on the Firebase Android app, so Google sign-in works for every local and CI debug APK. This key is for debug installs only. Before any wider distribution, replace it with a private key kept in a secret store, and stop publishing the keystore and its password.

## Setup

The Firebase project, Android app (`com.galmerom.outfittracker`), SHA-1/SHA-256 registration, Google sign-in provider, and Firestore/Storage rules are already in place. Garment detection for project `outfit-tracker-d503e` currently fails because the Gemini prepayment credits are depleted. Restore them in AI Studio (https://ai.studio/projects). If the next error mentions App Check, register the debug token shown on the meeting screen under Firebase Console > App Check > Apps > Manage debug tokens.

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
