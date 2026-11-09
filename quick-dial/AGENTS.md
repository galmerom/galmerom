# Quick Dial (חיוג מהיר)

Hebrew, right-to-left Android app for Gal's Pixel. Package `com.galmerom.quickdial`. Kotlin, Jetpack Compose, Material 3, minSdk 26. No account and no cloud. Light theme, blue by default.

## Do not

- Do not edit `outfit-tracker/` or `.github/workflows/outfit-tracker.yml`.
- Keep each change limited to what was asked.
- For a tiny text or color change, skip the local build and tests and let CI publish.

## Where things live

- `ui/HomePane.kt` — the grid, directly under the top bar. Long-press calls (3-second "מתקשר אל:" countdown with a large Cancel). Double-tap opens WhatsApp. Edit mode ("מצב עריכה", not saved; off when the app is left) makes a tap open the tile editor and disables call and WhatsApp. The photo is a square the full tile width; the short name sits under it and scales with the tile and the small/medium/large setting. The slider sets height/width, but the photo never shrinks below a square.
- `ui/ContactsPane.kt` — choose contacts (search, checkbox, one number) and the tile editor (photo preview, short name, gallery or camera, "חזרה לתמונה המקורית" always shown and disabled without a custom photo, remove from the list). A new photo is previewed before save.
- Tiles are keyed by the current `LOOKUP_KEY` (`Contacts.lookupContact`), with a fallback to the normalized phone number. `domain/TileIdentity.kt` merges duplicates and keeps the short name, custom photo, and frozen position. The picker stays checked for a contact that is already on the grid.
- `ui/TilePhotoCapture.kt` — Android photo picker and camera. Create the camera file first and keep its URI in `rememberSaveable` so it survives the camera activity.
- `ui/ReorderPane.kt` — left/right reorder, only while frozen.
- `ui/QuickDialApp.kt` — drawer, themes, call countdown, permission messages.
- `ui/QuickDialViewModel.kt` — screen state.
- `ui/theme/` — light themes. Default is `DialTheme.Blue`.
- `data/SettingsStore.kt` and `domain/Preferences.kt` — DataStore: columns (2, 3, or 4), tile ratio (0.8–1.6, default 1.15), freeze and saved order, call-count badge (off by default), short-name size, theme, chosen contacts.
- `data/CallLogReader.kt`, `domain/CallCounts.kt`, `domain/GridOrder.kt` — order by outgoing calls in the last 6 months, most-called on the right. Freeze keeps that order; new contacts go to the end. Unfreeze returns to automatic order.
- `data/PhoneBook.kt` — contacts and WhatsApp. Open the contact's `vnd.android.cursor.item/vnd.com.whatsapp.profile` data URI with package `com.whatsapp`. If there is no row, use `wa.me` and the selected number in international digits (Israel 972). No row and no usable number: say WhatsApp is not defined.
- `data/TilePhotos.kt` — custom photos in app-private storage, center-cropped to a square. They do not change the phone contact.
- `domain/` — pure logic. Unit tests are in `app/src/test`.

## Build and CI

Debug builds are signed with the committed key `app/quick-dial-debug.keystore` (alias `quickdialdebug`) so each APK installs over the last one.

`.github/workflows/quick-dial.yml` builds the debug APK and publishes `quick-dial.apk` to the pre-release `quick-dial-latest`:

https://github.com/galmerom/galmerom/releases/download/quick-dial-latest/quick-dial.apk
