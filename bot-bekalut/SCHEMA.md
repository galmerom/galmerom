# Firestore and publish schema — בוט בקלות

Firebase project: `bot-bekalut` (Spark, no billing). Database `(default)`, Standard edition, `me-west1`.

Bots publish with the Firebase Admin / MCP tools (they bypass security rules). The Android app uses the client SDK and can only read `messages` and `pages`, and read/write its own `users/{uid}/...` docs.

Allowed reader: verified Google account `energy2@gmail.com`.

## `messages/{messageId}`

One document per chat message (or one document per routine run if the bot sends a single combined message; if a run also sends a leftover/separate chat message, that is a second document). Store the text *exactly* as it appears in the Grok Bot chat, including markdown, links, tables, emoji, and image URLs.

Fields:

- `botId` (string, required): stable id, lowercase ASCII. Known ids: `ilan`, `analyst`, `ayelet`, `groki`. New bots pick a new stable id; the app must display them without a code change.
- `botName` (string, required): display name, e.g. `אילן`, `האנליסט`, `איילת`, `גרוקי`.
- `botColor` (string, optional): `#RRGGBB` used for that bot's button. Fallback colors if missing: ilan `#1565C0`, analyst `#2E7D32`, ayelet `#6A1B9A`, groki `#EF6C00`. Any other bot: derive a stable color from `botId`.
- `routineId` (string, required): a stable routine folder or slug.
- `routineName` (string, required): the human title, used as the sticky heading.
- `publishedAt` (timestamp, required).
- `content` (string, required): markdown / chat body.
- `images` (array of maps, optional): `{ "url": string, "alt": string }`.
- `sourceMessageId` (string, optional): id in the originating chat, if any, for de-duplication.

The app queries messages with `publishedAt` greater than now minus 24 hours, optionally also filtered by `botId`, ordered by `publishedAt` ascending for display (older at the top).

### 48-hour deletion

There is no TTL and no Cloud Functions. On **every** publish, before or after writing the new document, delete all `messages` documents whose `publishedAt` is older than 48 hours (not only the current bot's). Do not delete `pages`.

### FCM on publish (chat only, never for pages)

After writing a `messages` document, send FCM to topic `bot_{botId}` (example: `bot_ilan`) with:

- Notification title: `botName`
- Notification body: a short plain-text preview of `content`
- Data: `botId`, `messageId`, `type=chat`
- Android: default channel, do not set bypass-DND; use a group/thread key such as `bot_bekalut_updates` so several bot updates appear as one group; include a count suitable for a launcher badge when the API allows it.

Do not send a notification when only a Page catalog row is created or updated.

## `pages/{canvasId}`

Use the Grok Bot canvas id as the document id so updates refresh the same row (no duplicates).

Fields:

- `canvasId` (string)
- `title` (string)
- `description` (string): short description
- `url` (string): official URL of the form `https://cursor.com/canvas/store-e0679216-46ae-4dd4-9bc5-1314570c8ed2/{canvasId}`
- `botId` (string): `ilan`, `analyst`, `ayelet`, `groki`, `sharon`, or another stable id. If the author is not yet known, use `unknown` rather than guessing.
- `botName` (string)
- `createdAt` (timestamp)
- `updatedAt` (timestamp)

These documents are **not** deleted after 48 hours. Hidden pages are a per-user list, not a delete.

## `users/{uid}/fcmTokens/{tokenId}`

The app writes the device FCM registration token so a targeted send is possible if needed. Primary delivery is still the per-bot topic.

## `users/{uid}/hiddenPages/{canvasId}`

Exists if the user hid that page. Unhide = delete the doc.

## `users/{uid}/settings/{docId}`

App preferences that should roam (optional): bot button order, last bot viewed, per-bot mute, font size, theme id, per-bot scroll anchors, read cursors. Local DataStore is also fine for most of these; use Firestore if it is simple. Mute must also drive FCM topic subscribe/unsubscribe.

## Known routine sources (first version)

Publish only these unless Gal asks otherwise. Use the exact routine names as `routineName`.

- `ilan` / אילן: `סיכום X פעמיים ביום` (morning and evening). Also publish leftover Elon posts that did not fit the regular summary, as a separate message, same `botId`, with a distinct `routineName` such as the heading used in chat.
- `analyst` / האנליסט: `ריצת 10:00 ו-19:00 – חדשות + KORU + תזכורות + אג״ח + מדדים`; and `ריצת 12:00 – KORU + אג״ח + מדדים`. Do not publish `מתכנן יומן מדדים כלכליים שבועי`.
- `ayelet` / איילת: `חדשות לאיילת: בוקר, צהריים וערב`.
- `groki` / גרוקי: `חדשות Grok Bot מ-X (צהריים)`. Do not publish `התראה שעתיים לפני איפוס מכסת Grok Bot` or `עדכון לוח בקרת צריכת הבוטים`.

## Existing Pages to seed (do not guess missing authors)

The Android app only *reads* this catalog. Seeding is done separately. Known rows:

| title | canvasId | botId | botName |
| --- | --- | --- | --- |
| לוח בקרה: צריכת השימוש של הבוטים | f29e8ebd-8603-48a3-b372-a10ef4b75fd3 | groki | גרוקי |
| מפת חום: משקל ממוצע בכל תרגיל לפי חודש | ec28c8d5-3bb7-4a82-92ed-3af1897934d1 | groki | גרוקי |
| מודל SpaceX: תמונת מצב | e563c85a-8ce5-40cb-9813-4be64f72c5ba | unknown | |
| מודל Tesla: תוכנית אסטרטגית 2026-2031 | e79b881e-0fc7-44a9-81f8-4e2ef2427fbf | unknown | |
| לוח מעקב: צי הרכבים של Waymo וטסלה | 1208d222-03d8-4a2f-9d6f-7b839254eb32 | unknown | |
| גיוסי סטארטאפים בנורטק 2026 | 99b07e5c-ea1f-4b38-b5cf-5d802f79c6f0 | unknown | |
