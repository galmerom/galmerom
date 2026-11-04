# Outfit Tracker

A Hebrew, right-to-left Android app that records which outfits were worn when meeting a person or a forum, so the same outfit is not repeated.

The app currently shows a single Hello screen. The model and features below are the decided architecture; they are not implemented yet.

## Architecture

Data lives in Firebase so it survives replacing or losing the phone.

- **Cloud Firestore** stores the records. Offline persistence keeps a local cache and syncs later.
- **Firebase Storage** stores photos. The phone downscales each photo before upload.
- **Firebase Auth** signs the user in with their Google account.

The developer creates the Firebase project and adds `google-services.json` locally. That file is gitignored and must not be committed.

The on-screen name for a person or forum is the string resource `entity_label` (`איש/פורום`). Rename that resource later; do not hard-code the label.

## Firestore collections

- **entities** — people and forums. Fields: `name`, `type`.
- **clothingItems** — `description`, `category` (shirt, pants, skirt, or dress), `photoUrl`, and a hidden flag.
- **meetings** — `date`, `entityId`, a list of clothing-item ids, an optional `photoUrl`, and a free-text note.

## Planned features

- Wardrobe with categories, plus hide and revive.
- AI garment detection from a photo, with a manual fallback.
- Meeting log with a live-search entity picker that can create a new entity when nothing matches.
- Search page that shows the last 3 meetings and a "show more" button.
- Meeting detail view.
