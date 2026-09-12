package com.whatsappworkmanager.app.data.prefs

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bootstrap-only Groq credential vault.
 *
 * The credential is not present as plaintext in the APK. It is AES-GCM encrypted and the
 * bootstrap material is split so a casual string search does not reveal the credential.
 * On first use SettingsDataStore immediately moves the plaintext into AndroidX
 * EncryptedSharedPreferences backed by an Android Keystore MasterKey.
 *
 * This is intentionally a local-only convenience/security layer, not a replacement for a
 * server-side secret. A determined APK analyst can still recover client-side credentials.
 */
internal object BuiltInGroqCredential {
    private const val NONCE_B64 = "gfIYzaXGqpppRbgo"
    private const val CIPHERTEXT_B64 = "2AtiVaBpnMQPZoDTHCBKH7WtdPxKnTTQs8/nEW0Nqz7XKhM1XyvJuesONBga3po4DhDjj4mIPiE0xFDE4naubvAuyNiS82j5"
    private const val AAD = "WA_PREMIUM_GROQ_V117"

    // Split bootstrap material; do not join these into a single obvious source literal.
    private const val P1 = "WA-premium|groq|local-vault|"
    private const val P2 = "v117|9f2a7c1d"

    fun load(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val key = digest.digest((P1 + P2).toByteArray(StandardCharsets.UTF_8))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, Base64.decode(NONCE_B64, Base64.DEFAULT))
        )
        cipher.updateAAD(AAD.toByteArray(StandardCharsets.UTF_8))
        return String(
            cipher.doFinal(Base64.decode(CIPHERTEXT_B64, Base64.DEFAULT)),
            StandardCharsets.UTF_8
        )
    }
}
