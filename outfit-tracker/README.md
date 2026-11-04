# Outfit Tracker

A Hebrew, right-to-left Android app that records which outfits were worn when meeting a person or a forum, so the same outfit is not repeated.

Everything is stored on the device. There is no server and no account.

## Planned data model

Not implemented yet. The app currently shows a single Hello screen.

- **Entity** — a person or a forum. The on-screen label is the string resource `entity_label` (`איש/פורום`), so it can be renamed later without changing code.
- **ClothingItem** — photo, description, category (shirt, pants, skirt, or dress), and a hidden flag.
- **Meeting** — date, entity, the clothing items worn, an optional photo, and a free-text note.
