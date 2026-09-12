package com.whatsappworkmanager.app.utils

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Lets the user switch the app's language (Arabic / English / follow system) from inside
 * Settings, without touching the phone's system-wide language.
 *
 * Uses TWO mechanisms together, deliberately:
 *  1. [wrapContext] — the classic, always-reliable technique: force a `Locale` onto a
 *     `Configuration` and derive a new Context via `createConfigurationContext()`. This is
 *     applied in both `WwmApplication.attachBaseContext()` and `MainActivity.attachBaseContext()`
 *     (see those classes), which runs *before* any resource is ever resolved — guaranteed to
 *     work regardless of Activity base class or AppCompat/OEM quirks.
 *  2. `AppCompatDelegate.setApplicationLocales()` — the modern AndroidX API, kept alongside
 *     (1) for anything that specifically reads from it (e.g. system UI elements, some
 *     framework dialogs) and because it's the officially documented approach — but NOT relied
 *     on as the sole mechanism, since it turned out to not reliably apply to this app's
 *     `ComponentActivity`-based `MainActivity` (the per-app-language "no AppCompatActivity
 *     required" support has had inconsistent real-world behavior across OEM Android builds).
 *
 * Persisted in a tiny plain `SharedPreferences` file (not `SettingsDataStore`, which is
 * DataStore/Flow-based and therefore asynchronous) so the very first frame at app startup —
 * before any coroutine has had a chance to run — can already be wrapped in the right locale.
 * [com.whatsappworkmanager.app.presentation.settings.SettingsViewModel.setLanguage] writes to
 * both this and `SettingsDataStore` together, so they never drift.
 */
object LocaleHelper {

    private const val PREFS_NAME = "wwm_locale_prefs"
    private const val KEY_LANGUAGE = "language"

    /** Call once, as early as possible in Application.onCreate(), before any UI inflates. */
    fun applySavedLocale(context: Context) {
        applyLocale(readSavedLanguage(context))
    }

    /** Persists [languageCode] ("system" | "en" | "ar") and applies it immediately. */
    fun setLanguage(context: Context, languageCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageCode)
            .apply()
        applyLocale(languageCode)
    }

    fun readSavedLanguage(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "system") ?: "system"

    /**
     * Wraps [base] in a Context whose Configuration is forced to [languageCode]'s Locale —
     * call this from `attachBaseContext()` in both `WwmApplication` and `MainActivity`. Returns
     * [base] unchanged for "system" (follow the device's own language, do nothing extra).
     */
    fun wrapContext(base: Context, languageCode: String): Context {
        if (languageCode == "system") return base
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    private fun applyLocale(languageCode: String) {
        val localeList = if (languageCode == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
