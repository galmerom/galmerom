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

`google-services.json` is not in git. The Google Services Gradle plugin is applied only when `app/google-services.json` exists, so `./gradlew assembleDebug` passes without it. A build without that file installs and shows a setup message instead of crashing.

## Setup

1. Create a Firebase project and enable Cloud Firestore, Cloud Storage, Authentication with the Google provider, and Firebase AI Logic (Gemini).
2. Add an Android app with package name `com.galmerom.outfittracker`.
3. Add the debug SHA-1. From this folder:

   ```
   ./gradlew signingReport
   ```

   Copy the SHA-1 of the `debug` variant into the Firebase Android app settings. Google sign-in needs it.
4. Download `google-services.json` and place it at `outfit-tracker/app/google-services.json`. Do not commit it.
5. Enabling Google sign-in creates a **Web client** in the file (`default_web_client_id`). The app reads that id. If it is missing, paste the Web client id into `web_client_id` in `app/src/main/res/values/strings.xml`.
6. Deploy the security rules so each user can touch only `users/{uid}/**`. From this folder, with the Firebase CLI:

   ```
   npx firebase-tools deploy --only firestore:rules,storage --project YOUR_PROJECT_ID
   ```

   Or paste `firestore.rules` into Firestore → Rules and `storage.rules` into Storage → Rules in the Firebase console.
7. Build and install:

   ```
   ./gradlew assembleDebug
   ```

## App

Bottom bar: log a meeting, search, wardrobe.

- Wardrobe: add and edit items with a camera or gallery photo, description, category, and a hidden flag. Hidden items stay in meeting history and can be revived from the hidden list. Selection lists omit them.
- Log a meeting: date defaults to today. The entity field searches as you type; if nothing matches exactly, create a person or a forum. Pick clothes from category menus (each row shows a photo), add a photo for AI detection, or add an item by hand. Optional note and meeting photo.
- Search: filter by entity, show the 3 newest meetings, then **הצג עוד**. A meeting opens with its photo when there is one, then the clothes with their photos, and the note.
