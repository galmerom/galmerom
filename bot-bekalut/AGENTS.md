# בוט בקלות (Bot Bekalut)

Hebrew, right-to-left Android reader for Gal's Pixel 6. Package `com.galmerom.botbekalut`. Kotlin, Jetpack Compose, Material 3, minSdk 26. Light theme only — never add a dark mode. Default accent is blue, in the same family as Quick Dial. Color themes in the side menu must change both the accent and a light background tint.

The app shows the last 24 hours of selected Grok Bot routine chats in a large, clear, RTL layout, plus a catalog of Grok Bot Pages. It is mainly a reader, not a composer. Content must appear as it does in the Grok Bot chat (wording, links, images when present, tables, emoji and symbols) — not as a custom headline or summary card. Order is like chat: older at the top, newer at the bottom.

## Do not

- Do not edit `outfit-tracker/`, `quick-dial/`, or their GitHub Actions workflows. You may *read* `quick-dial/` only as a style and stack reference.
- Do not enable Firebase billing, Blaze, Cloud Functions, or Firestore TTL.
- Do not make chat content public. The APK may be published like Quick Dial because messages are loaded only after Google sign-in; never bake personal messages into the APK or a public URL.
- Keep each change limited to what was asked.
- For a later tiny text or color change, skip the local build and tests and let CI publish. On the first build, and on any change that can break compilation, run `./gradlew assembleDebug` in `bot-bekalut/` and fix errors before you finish.
- Do not re-scan the whole repository on every run. Read this file first, then touch only files under `bot-bekalut/` and `.github/workflows/bot-bekalut.yml`.

## Firebase

- Project ID: `bot-bekalut` (Spark / no billing). Firestore Standard edition, location `me-west1`.
- Android app id: `1:797629258178:android:e0e45c6e96443389b11e5b`
- Package: `com.galmerom.botbekalut`
- Commit `app/google-services.json` from the attached file (includes the Android and Web OAuth clients).
- Google Sign-In is required on first launch. Use the Web client ID `797629258178-bg0gokehgjnhubeq10iv3fuj38vm8uls.apps.googleusercontent.com` as `requestIdToken` / server client ID.
- Only `energy2@gmail.com` (verified) may read chat and pages. This is enforced in `firestore.rules`. If a different Google account signs in, show a clear Hebrew message that this account does not have access, and offer sign-out. Do not crash on empty/denied reads.
- Commit and deploy-ready copies of `firestore.rules` and `firestore.indexes.json` also belong in this folder (already created on Firebase; keep the repo copies in sync).
- The app may write only under `users/{uid}/fcmTokens`, `users/{uid}/hiddenPages`, and `users/{uid}/settings`. It must never write `messages` or `pages`.
- Chat documents live at most 48 hours in Firestore (bots delete older ones on publish). The app itself displays only the last 24 hours. The Pages catalog is separate and is not deleted after 48 hours.
- See `SCHEMA.md` for document shapes, FCM, and how bots publish.

## Signing and CI

Debug (and the sideload APK) are signed with the committed key `app/bot-bekalut-debug.keystore` (PKCS12, alias `botbekalutdebug`) so each APK installs over the last one and the SHA matches Google Sign-In. Copy it from the attached base64 and keep the Gradle `storeType` as `pkcs12`. Put the store/key password in `app/keystore.properties` (and wire that file into `build.gradle.kts`), matching the attached password file. Do not commit `debug-key.pem`, `debug-cert.pem`, or raw secret files from the workspace uploads folder other than the intended keystore + `keystore.properties` + `google-services.json`.

`.github/workflows/bot-bekalut.yml` should follow the Quick Dial pattern:

- Path filters: `bot-bekalut/**` and `.github/workflows/bot-bekalut.yml` only.
- JDK 17, `./gradlew assembleDebug`, upload the APK artifact.
- On push, publish `bot-bekalut.apk` to the pre-release tag `bot-bekalut-latest`.
- Download URL: `https://github.com/galmerom/galmerom/releases/download/bot-bekalut-latest/bot-bekalut.apk`
- `permissions: contents: write`, and a concurrency group so overlapping publishes do not clobber each other badly.
- Do not publish on a pull_request event if that would run from a fork with the wrong permissions; match Quick Dial (publish only on `push`).

## First launch

1. Google sign-in.
2. Request notification permission (Android 13+). Respect system Do Not Disturb; do not use a channel that bypasses DND.
3. After sign-in, pull latest messages and pages from Firestore, cache them on the phone (so a later open can show the last 24 hours even if briefly offline; still try to refresh on open), and show the chat.

## Chat UI

- Top bar: horizontally scrolling bot buttons, then a *fixed* narrow circular-arrow refresh icon to the right of the row (not in the drawer, not a wide button). Only bots that have written in the last 24 hours appear. The button color should match that bot (see `SCHEMA.md` for ids and fallback colors). Unread is a *dot* on the button, not a color change of the button itself. Default button order: אילן, האנליסט, איילת, גרוקי. The user can change that order in side-menu edit mode; persist it. The list of bots must be data-driven — future bots that publish must appear without a code change (use a fallback color if none is stored).
- On a normal open, go to the last bot viewed. If that bot has unread messages, jump to the first unread and briefly highlight it. If all are read, restore the saved per-bot scroll position.
- Mark as read only after the user has scrolled to the end, and also provide a «סמן כנקרא» button.
- Swipe left/right between bots, in addition to the top buttons.
- Pull-to-refresh, plus the top refresh icon. After a refresh with nothing new, show «אין עדכונים חדשים».
- Small button at the bottom that appears when the user has scrolled up, and jumps back to the latest message.
- Sticky routine title while scrolling a routine's messages.
- Timestamps like a chat.
- No in-app search.
- No in-app notification banner. Phone notifications only (FCM).
- Long-press a message to show text-selection handles so the user can copy part of it, plus an «העתק הכל» action.
- Links open in Chrome (`chrome` / Custom Tabs / `ACTION_VIEW` preferring Chrome). Links to X (`x.com` or `twitter.com`) open in the X app if installed, otherwise Chrome.
- Tables: horizontal scroll, slightly smaller font, sticky first column. Tapping a table opens it full screen, also with a sticky first column.
- Images: show them when the message includes them; tap to enlarge. Do not over-complicate.
- «עמודים» in the side menu opens a catalog of all bot-created Grok Bot Pages, grouped by bot, each with name and short description. Tapping a row opens the official Page URL (`https://cursor.com/canvas/...`) in the browser or the official Grok Bot app — do not re-render the Page inside this app. Back returns to the chat. New pages just appear; do not badge or notify for new or updated pages. The user can hide a page and unhide it later (store hidden ids under `users/{uid}/hiddenPages`). The pages screen may include bots that are not in the 24-hour chat button row.

## Side menu

- Color themes (accent + background tint). No dark mode.
- Font size.
- Per-bot notification mute. A muted bot still appears in the button row if it wrote in the last 24 hours, but the app unsubscribes from that bot's FCM topic so no phone notification is shown.
- Edit mode to reorder bot buttons.
- «עמודים»
- Sign out of the Google account.

## Notifications

- FCM only, no in-app banner. Text preview is on, including on the lock screen (default / public visibility).
- Respect system Do Not Disturb.
- One grouped notification when several bots update; tapping a single-bot notification opens that bot; tapping the group opens the first bot that still has unread messages.
- Launcher badge shows a number.
- Subscribe to topic `bot_{botId}` for each non-muted bot after sign-in, and also write the device token under `users/{uid}/fcmTokens/{tokenId}`.

## Icon

Launcher icon: the shape of the Grok Bot mascot, a clear friendly blue (similar in spirit to Sharon's bot), looking as if it is reading a book. Light, simple, readable at small sizes. A solid first pass is enough; it may be refined later.

## Build done when

- `bot-bekalut/` is a complete Android project that compiles with `./gradlew assembleDebug`.
- The CI workflow exists and is path-limited as specified.
- `AGENTS.md`, `SCHEMA.md`, `google-services.json`, `firestore.rules`, `firestore.indexes.json`, and the signing key are in the project.
- A PR is open against the repo default branch with this work and nothing unrelated.
