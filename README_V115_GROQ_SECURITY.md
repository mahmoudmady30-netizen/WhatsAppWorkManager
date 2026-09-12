# WA premium v115 — Groq credential hardening

- Removed the plaintext Groq API key from source code and DataStore.
- The built-in credential is shipped only as an encrypted/obfuscated payload.
- On first use it is decrypted in memory and written to `EncryptedSharedPreferences` protected by AndroidX Security / Android Keystore.
- Backup rules already exclude shared preferences, so the encrypted credential is not included in cloud backup.
- Release builds keep R8 shrinking/minification enabled.

## Important security limitation
A secret used directly by an Android client cannot be made mathematically unrecoverable from a determined attacker who controls the device/runtime. For a production public release, Groq's recommended architecture is a trusted backend proxy: the Groq key stays server-side and the app authenticates to your backend. See Groq's production security guidance.

## Installation / Play Protect
Credential encryption does not itself control Android Play Protect. If Play Protect asks to disable protection for a sideloaded APK, use a properly signed release build and investigate the warning rather than disabling protection. The project keeps release signing external via `keystore.properties` so the private signing key is not shipped in the source ZIP.
