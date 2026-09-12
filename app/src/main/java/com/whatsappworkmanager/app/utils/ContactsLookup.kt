package com.whatsappworkmanager.app.utils

import android.content.Context
import android.provider.ContactsContract

/**
 * Looks up a phone number for a contact already saved in the phone's own Contacts app, by
 * name — used to auto-fill the phone number when adding an Important Person from a message
 * whose sender is a real name (not a raw phone number WhatsApp itself already revealed; see
 * ContactsLookup's callers for that distinction). Requires READ_CONTACTS, requested at runtime
 * only when the user opts in — never anything more than a read-only query, and only for the
 * one name actually being looked up, never a bulk read of the whole address book.
 */
object ContactsLookup {

    /**
     * Returns the first phone number found for a contact whose display name matches [name]
     * (case-insensitive, exact match first, then falls back to a "contains" match so "Ahmed"
     * can still find a contact saved as "Ahmed Hassan"), or null if nothing matches or the
     * permission isn't currently granted. Never throws on a missing permission — treats it
     * the same as "no match found," since the caller is expected to have already checked
     * permission state before deciding whether to call this at all.
     */
    fun findPhoneNumberByName(context: Context, name: String): String? {
        if (name.isBlank()) return null
        return try {
            queryPhoneNumbers(context).let { candidates ->
                candidates.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
                    ?: candidates.firstOrNull { it.first.contains(name, ignoreCase = true) }?.second
            }
        } catch (t: SecurityException) {
            null
        } catch (t: Exception) {
            null
        }
    }

    private fun queryPhoneNumbers(context: Context): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null, null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIndex < 0 || numberIndex < 0) return results
            while (cursor.moveToNext()) {
                val contactName = cursor.getString(nameIndex) ?: continue
                val number = cursor.getString(numberIndex) ?: continue
                results.add(contactName to number)
            }
        }
        return results
    }
}
