# בוט בקלות

Hebrew, right-to-left Android reader for the last 24 hours of Grok Bot routine chats, plus a catalog of Grok Bot Pages. Messages load only after Google sign-in.

Package: `com.galmerom.botbekalut`.

## Build

```bash
cd bot-bekalut
./gradlew assembleDebug
```

The debug APK is signed with the committed PKCS12 key so it installs over the previous build.

## Install

After a push to this repo that touches `bot-bekalut/`, GitHub Actions publishes:

https://github.com/galmerom/galmerom/releases/download/bot-bekalut-latest/bot-bekalut.apk

Firebase stays on the Spark plan. Do not enable billing, Blaze, Cloud Functions, or Firestore TTL.

See `AGENTS.md` for product behavior and `SCHEMA.md` for the Firestore documents.
