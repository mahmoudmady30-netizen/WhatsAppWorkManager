package com.whatsappworkmanager.app.utils

import android.content.Context

/**
 * Remembers the last country code the user picked or typed for a scheduled message's phone
 * number, so the next time they open the Add dialog it's already pre-filled instead of
 * defaulting to Egypt every time. Plain synchronous SharedPreferences (like LocaleHelper) so it
 * can be read directly while composing a dialog, no coroutine needed.
 */
object CountryCodePrefs {

    private const val PREFS_NAME = "wwm_country_code_prefs"
    private const val KEY_LAST_CODE = "last_country_code"
    private const val DEFAULT_CODE = "20" // Egypt

    fun getLastUsed(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CODE, DEFAULT_CODE) ?: DEFAULT_CODE

    fun setLastUsed(context: Context, dialCode: String) {
        if (dialCode.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_CODE, dialCode)
            .apply()
    }
}
