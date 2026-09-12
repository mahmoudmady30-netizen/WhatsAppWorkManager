package com.whatsappworkmanager.app.utils

/** One selectable entry in the country-code picker: display name in both languages this app
 *  supports, dial code (digits only, no "+"), flag emoji. */
data class CountryCode(val name: String, val nameAr: String, val dialCode: String, val flag: String) {
    /** The name to actually show, following the app's current language — "ar" shows [nameAr],
     *  anything else (including "system" already resolved to a concrete language by the
     *  caller) shows the English [name]. */
    fun displayName(languageCode: String): String = if (languageCode == "ar") nameAr else name
}

object CountryCodes {

    // Arabic-speaking countries first (most likely for this app's users), then other major
    // countries. Dial codes are digits only — no "+", matching what IntentHelper expects.
    val ALL: List<CountryCode> = listOf(
        CountryCode("Egypt", "مصر", "20", "🇪🇬"),
        CountryCode("Saudi Arabia", "السعودية", "966", "🇸🇦"),
        CountryCode("United Arab Emirates", "الإمارات", "971", "🇦🇪"),
        CountryCode("Kuwait", "الكويت", "965", "🇰🇼"),
        CountryCode("Qatar", "قطر", "974", "🇶🇦"),
        CountryCode("Bahrain", "البحرين", "973", "🇧🇭"),
        CountryCode("Oman", "عُمان", "968", "🇴🇲"),
        CountryCode("Jordan", "الأردن", "962", "🇯🇴"),
        CountryCode("Lebanon", "لبنان", "961", "🇱🇧"),
        CountryCode("Iraq", "العراق", "964", "🇮🇶"),
        CountryCode("Syria", "سوريا", "963", "🇸🇾"),
        CountryCode("Palestine", "فلسطين", "970", "🇵🇸"),
        CountryCode("Yemen", "اليمن", "967", "🇾🇪"),
        CountryCode("Libya", "ليبيا", "218", "🇱🇾"),
        CountryCode("Sudan", "السودان", "249", "🇸🇩"),
        CountryCode("Morocco", "المغرب", "212", "🇲🇦"),
        CountryCode("Algeria", "الجزائر", "213", "🇩🇿"),
        CountryCode("Tunisia", "تونس", "216", "🇹🇳"),
        CountryCode("Mauritania", "موريتانيا", "222", "🇲🇷"),
        CountryCode("Somalia", "الصومال", "252", "🇸🇴"),
        CountryCode("Djibouti", "جيبوتي", "253", "🇩🇯"),
        CountryCode("Comoros", "جزر القمر", "269", "🇰🇲"),
        CountryCode("United States / Canada", "الولايات المتحدة / كندا", "1", "🇺🇸"),
        CountryCode("United Kingdom", "المملكة المتحدة", "44", "🇬🇧"),
        CountryCode("Germany", "ألمانيا", "49", "🇩🇪"),
        CountryCode("France", "فرنسا", "33", "🇫🇷"),
        CountryCode("Italy", "إيطاليا", "39", "🇮🇹"),
        CountryCode("Spain", "إسبانيا", "34", "🇪🇸"),
        CountryCode("Turkey", "تركيا", "90", "🇹🇷"),
        CountryCode("India", "الهند", "91", "🇮🇳"),
        CountryCode("Pakistan", "باكستان", "92", "🇵🇰"),
        CountryCode("China", "الصين", "86", "🇨🇳"),
        CountryCode("Russia", "روسيا", "7", "🇷🇺"),
        CountryCode("Brazil", "البرازيل", "55", "🇧🇷"),
        CountryCode("Australia", "أستراليا", "61", "🇦🇺"),
        CountryCode("Japan", "اليابان", "81", "🇯🇵"),
        CountryCode("South Korea", "كوريا الجنوبية", "82", "🇰🇷"),
        CountryCode("Indonesia", "إندونيسيا", "62", "🇮🇩"),
        CountryCode("Nigeria", "نيجيريا", "234", "🇳🇬"),
        CountryCode("South Africa", "جنوب أفريقيا", "27", "🇿🇦")
    )

    /**
     * Splits a full, digits-only phone number (country code + local number concatenated, e.g.
     * "201001234567") back into its [CountryCode] and the remaining local part — used when
     * editing an existing scheduled message. Matches the *longest* dial code first (so "20"
     * Egypt isn't wrongly matched inside a "216" Tunisia number, say) and falls back to
     * [fallback] with the whole string as the local part if nothing matches.
     */
    fun split(fullDigits: String, fallback: CountryCode): Pair<CountryCode, String> {
        val match = ALL
            .sortedByDescending { it.dialCode.length }
            .firstOrNull { fullDigits.startsWith(it.dialCode) }
        return if (match != null) {
            match to fullDigits.removePrefix(match.dialCode)
        } else {
            fallback to fullDigits
        }
    }

    fun byDialCode(dialCode: String): CountryCode? = ALL.firstOrNull { it.dialCode == dialCode }

    /**
     * Whether [query] matches [country] for the picker's search box — checks the English
     * name, the Arabic name, and the dial code all at once (with or without a leading "+"),
     * so a search works regardless of which language the person happens to type in, rather
     * than only matching whichever single name the picker happens to display right now.
     */
    fun matches(country: CountryCode, query: String): Boolean {
        if (query.isBlank()) return true
        val normalizedQuery = query.trim().removePrefix("+")
        return country.name.contains(query, ignoreCase = true) ||
            country.nameAr.contains(query) ||
            country.dialCode.contains(normalizedQuery)
    }

    /**
     * Parses a phone number exactly as returned by the system Contacts picker — formatting is
     * inconsistent (may have "+", spaces, dashes, or none of those; may or may not include a
     * country code at all). Best-effort: if it starts with "+", treat the rest as a full
     * international number and split normally. Otherwise, assume it's already in local format
     * and just strip a single leading trunk "0" (common in many countries' local dialing
     * format, e.g. Egyptian mobile numbers written as "01001234567"), defaulting to
     * [fallback] for the country code — the user can still correct it via the country-code
     * field afterwards, since this is a starting point, not a guarantee.
     */
    fun parsePickedContactNumber(raw: String, fallback: CountryCode): Pair<CountryCode, String> {
        val trimmed = raw.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) {
            split(digitsOnly, fallback)
        } else {
            fallback to digitsOnly.removePrefix("0")
        }
    }
}
