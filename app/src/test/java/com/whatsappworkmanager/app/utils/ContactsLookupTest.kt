package com.whatsappworkmanager.app.utils

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers ContactsLookup's guard clauses. Deliberately doesn't attempt to simulate real
 * ContactsContract data (RawContacts/Data tables are notably heavy to fake reliably in
 * Robolectric) — what matters most here is that missing permission or a blank name degrade
 * safely to null rather than crashing, which is exactly what a caller that's already checked
 * permission state needs to be able to rely on.
 */
@RunWith(RobolectricTestRunner::class)
class ContactsLookupTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `a blank name returns null without attempting any query`() {
        assertNull(ContactsLookup.findPhoneNumberByName(context, ""))
    }

    @Test
    fun `no matching contact returns null rather than throwing`() {
        // No READ_CONTACTS permission granted in this test environment, and no contacts
        // exist either way — either reason should degrade to null, never an exception.
        assertNull(ContactsLookup.findPhoneNumberByName(context, "Someone Definitely Not Saved"))
    }
}
