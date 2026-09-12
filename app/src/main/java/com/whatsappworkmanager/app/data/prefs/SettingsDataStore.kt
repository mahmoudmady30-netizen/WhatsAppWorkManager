package com.whatsappworkmanager.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "wwm_settings")

/**
 * Non-sensitive app preferences (DataStore) + a separate EncryptedSharedPreferences store
 * reserved for anything sensitive (e.g. a user-entered cloud AI API key). The two are kept
 * apart on purpose: ordinary UI prefs never touch the encrypted file, and the encrypted file
 * is never written to backups (see backup_rules.xml).
 */
class SettingsDataStore(private val context: Context) {

    // The built-in Groq credential is stored only as an encrypted/obfuscated blob.
    // It is materialized into the Android Keystore-backed encrypted preferences on first use.
    // This prevents plaintext credentials from appearing in the APK/resources/source.

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language") // "en" | "ar" | "system"
        val THEME = stringPreferencesKey("theme") // "light" | "dark" | "system" | "premium"
        val PREMIUM_DEFAULT_MIGRATED = booleanPreferencesKey("premium_default_migrated")
        val AI_PROVIDER = stringPreferencesKey("ai_provider") // "local" | "openai" | "anthropic" | "gemini"
        val AI_CLOUD_CONSENT = booleanPreferencesKey("ai_cloud_consent")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val WHATSAPP_VARIANT = stringPreferencesKey("whatsapp_variant") // "auto" | "regular" | "business"
        val AUTO_ENABLE_NEW_GROUPS = booleanPreferencesKey("auto_enable_new_groups")
        val AUTO_REPLY_ENABLED = booleanPreferencesKey("auto_reply_enabled")
        val AUTO_REPLY_DELAY_SECONDS = intPreferencesKey("auto_reply_delay_seconds")
        // The most recently AI-generated Auto Reply that's ready to send — surfaced on the
        // Dashboard as a one-tap "send it now" shortcut, rather than requiring a trip to
        // whatever notification WhatsAppNotificationListenerService already posted for it
        // (which may have long since scrolled out of the shade). Cleared once actually sent
        // from that shortcut, so a stale reply for a message the person already handled
        // another way doesn't linger.
        val LAST_AUTO_REPLY_TEXT = stringPreferencesKey("last_auto_reply_text")
        val LAST_AUTO_REPLY_NAME = stringPreferencesKey("last_auto_reply_name")
        val LAST_AUTO_REPLY_PHONE = stringPreferencesKey("last_auto_reply_phone")
        val LAST_AUTO_REPLY_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("last_auto_reply_timestamp")
        val DISMISSED_DASHBOARD_NOTIFICATIONS = stringSetPreferencesKey("dismissed_dashboard_notifications")
        val DISMISSED_DASHBOARD_SCHEDULED = stringSetPreferencesKey("dismissed_dashboard_scheduled")
    }

    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "system" }
    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "premium" }
    val aiProvider: Flow<String> = context.dataStore.data.map { it[Keys.AI_PROVIDER] ?: "groq" }
    val aiCloudConsent: Flow<Boolean> = context.dataStore.data.map { it[Keys.AI_CLOUD_CONSENT] ?: true }
    val retentionDays: Flow<Int> = context.dataStore.data.map { it[Keys.RETENTION_DAYS] ?: 30 }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    /** Which WhatsApp app to open — see IntentHelper.WHATSAPP_VARIANT_*. Default "auto" keeps
     *  the original behavior (prefer regular WhatsApp, fall back to Business). */
    val whatsappVariant: Flow<String> = context.dataStore.data.map { it[Keys.WHATSAPP_VARIANT] ?: "auto" }
    /**
     * When true, a group/contact newly discovered from a captured notification is opted into
     * Work Summary immediately, instead of the default "off until you switch it on yourself."
     * Meant for people with many muted WhatsApp threads they still want covered — capturing
     * already works for muted chats (WhatsApp still posts the notification silently), this
     * setting just removes the extra step of manually enabling each one afterwards.
     */
    val autoEnableNewGroups: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_ENABLE_NEW_GROUPS] ?: false }
    val autoReplyEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_REPLY_ENABLED] ?: false }
    val autoReplyDelaySeconds: Flow<Int> = context.dataStore.data.map { it[Keys.AUTO_REPLY_DELAY_SECONDS] ?: 8 }
    /** Null when there's no pending ready-to-send reply — either none has been generated yet,
     *  or the last one was already sent/cleared via [clearLastAutoReply]. */
    data class LastAutoReply(val text: String, val personName: String, val phoneNumber: String?, val timestamp: Long)
    val dismissedDashboardNotifications: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.DISMISSED_DASHBOARD_NOTIFICATIONS] ?: emptySet()
    }
    val dismissedDashboardScheduled: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.DISMISSED_DASHBOARD_SCHEDULED] ?: emptySet()
    }

    val lastAutoReply: Flow<LastAutoReply?> = context.dataStore.data.map { prefs ->
        val text = prefs[Keys.LAST_AUTO_REPLY_TEXT] ?: return@map null
        LastAutoReply(
            text = text,
            personName = prefs[Keys.LAST_AUTO_REPLY_NAME] ?: "",
            phoneNumber = prefs[Keys.LAST_AUTO_REPLY_PHONE],
            timestamp = prefs[Keys.LAST_AUTO_REPLY_TIMESTAMP] ?: 0L
        )
    }

    suspend fun setLanguage(value: String) = context.dataStore.edit { it[Keys.LANGUAGE] = value }
    suspend fun setTheme(value: String) = context.dataStore.edit { it[Keys.THEME] = value }

    /** Make Premium the app's default theme once, while preserving any theme the user has
     * explicitly selected afterwards. This also upgrades existing installs that were still
     * using the old implicit System default. */
    suspend fun ensurePremiumDefault() = context.dataStore.edit { prefs ->
        if (prefs[Keys.PREMIUM_DEFAULT_MIGRATED] != true) {
            if (prefs[Keys.THEME] == null || prefs[Keys.THEME] == "system") {
                prefs[Keys.THEME] = "premium"
            }
            prefs[Keys.PREMIUM_DEFAULT_MIGRATED] = true
        }
    }
    suspend fun setAiProvider(value: String) = context.dataStore.edit { it[Keys.AI_PROVIDER] = value }
    suspend fun setAiCloudConsent(value: Boolean) = context.dataStore.edit { it[Keys.AI_CLOUD_CONSENT] = value }
    suspend fun setRetentionDays(value: Int) = context.dataStore.edit { it[Keys.RETENTION_DAYS] = value }
    suspend fun setOnboardingDone(value: Boolean) = context.dataStore.edit { it[Keys.ONBOARDING_DONE] = value }
    suspend fun setWhatsappVariant(value: String) = context.dataStore.edit { it[Keys.WHATSAPP_VARIANT] = value }
    suspend fun setAutoEnableNewGroups(value: Boolean) = context.dataStore.edit { it[Keys.AUTO_ENABLE_NEW_GROUPS] = value }
    suspend fun setAutoReplyEnabled(value: Boolean) = context.dataStore.edit { it[Keys.AUTO_REPLY_ENABLED] = value }
    suspend fun setAutoReplyDelaySeconds(value: Int) = context.dataStore.edit { it[Keys.AUTO_REPLY_DELAY_SECONDS] = value.coerceIn(3, 60) }

    suspend fun setLastAutoReply(text: String, personName: String, phoneNumber: String?) = context.dataStore.edit {
        it[Keys.LAST_AUTO_REPLY_TEXT] = text
        it[Keys.LAST_AUTO_REPLY_NAME] = personName
        if (phoneNumber != null) it[Keys.LAST_AUTO_REPLY_PHONE] = phoneNumber else it.remove(Keys.LAST_AUTO_REPLY_PHONE)
        it[Keys.LAST_AUTO_REPLY_TIMESTAMP] = System.currentTimeMillis()
    }

    suspend fun dismissDashboardNotification(key: String) = context.dataStore.edit { prefs ->
        val current = prefs[Keys.DISMISSED_DASHBOARD_NOTIFICATIONS] ?: emptySet()
        prefs[Keys.DISMISSED_DASHBOARD_NOTIFICATIONS] = (current + key).toList().takeLast(100).toSet()
    }

    suspend fun dismissDashboardScheduled(key: String) = context.dataStore.edit { prefs ->
        val current = prefs[Keys.DISMISSED_DASHBOARD_SCHEDULED] ?: emptySet()
        prefs[Keys.DISMISSED_DASHBOARD_SCHEDULED] = (current + key).toList().takeLast(100).toSet()
    }

    suspend fun clearLastAutoReply() = context.dataStore.edit {
        it.remove(Keys.LAST_AUTO_REPLY_TEXT)
        it.remove(Keys.LAST_AUTO_REPLY_NAME)
        it.remove(Keys.LAST_AUTO_REPLY_PHONE)
        it.remove(Keys.LAST_AUTO_REPLY_TIMESTAMP)
    }

    // --- Encrypted store for cloud AI API keys (never written to source, never backed up) ---

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "wwm_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiKey(providerId: String): String? {
        val prefKey = "api_key_$providerId"
        val saved = encryptedPrefs.getString(prefKey, null)
        if (!saved.isNullOrBlank()) return saved
        if (providerId == "groq") {
            return runCatching {
                BuiltInGroqCredential.load().also { builtIn ->
                    encryptedPrefs.edit().putString(prefKey, builtIn).apply()
                }
            }.getOrNull()
        }
        return null
    }

    fun setApiKey(providerId: String, key: String) {
        encryptedPrefs.edit().putString("api_key_$providerId", key).apply()
    }

    fun clearApiKey(providerId: String) {
        encryptedPrefs.edit().remove("api_key_$providerId").apply()
    }
}
