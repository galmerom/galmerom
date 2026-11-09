# חיוג מהיר

A Hebrew, right-to-left Android app for Gal's Pixel. Long-press a tile to call after a three-second cancel window. Double-tap opens WhatsApp. The grid is the speed-dial list.

Nothing leaves the phone. Chosen contacts, short names, column count, freeze order, and theme live in DataStore. Photos, phone numbers, WhatsApp rows, and outgoing-call counts are read from the device.

Debug builds are signed with the committed PKCS12 keystore `app/quick-dial-debug.keystore` (alias `quickdialdebug`). This key is for debug installs only, so each published APK updates the previous one. Before any wider distribution, replace it with a private key kept in a secret store, and stop publishing the keystore and its password.

```
./gradlew assembleDebug
```

The debug APK from GitHub Actions is the artifact `quick-dial-debug-apk` on the workflow run. Each push also replaces the pre-release tag `quick-dial-latest`:

https://github.com/galmerom/galmerom/releases/download/quick-dial-latest/quick-dial.apk
