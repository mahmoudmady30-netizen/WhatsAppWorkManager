# WA premium v117 — Secure Local Groq

This version keeps Groq local to the Android app. No Cloudflare Worker, backend, server,
Gradle secret, or external deployment is required.

- Groq credential bootstrap is encrypted/obfuscated in the APK rather than stored as plaintext.
- On first use, the credential is placed into AndroidX EncryptedSharedPreferences backed by Android Keystore.
- Release builds use R8/minification and resource shrinking.
- Users can still override the provider API key from Settings; saved keys are encrypted on-device.

Important: a credential that ultimately runs on a client device cannot be made mathematically
unrecoverable against a determined reverse engineer. This is defense-in-depth for a convenient
standalone APK, not the same security boundary as a server-side secret.
