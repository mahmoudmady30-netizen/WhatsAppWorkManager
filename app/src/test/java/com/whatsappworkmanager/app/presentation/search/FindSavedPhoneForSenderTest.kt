package com.whatsappworkmanager.app.presentation.search

import androidx.test.core.app.ApplicationProvider
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.ImportantContact
import com.whatsappworkmanager.app.domain.model.WorkMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for a real bug: "Use" on a generated reply opened WhatsApp generally instead
 * of the exact chat, specifically for 1:1 conversations. The lookup only ever checked
 * `WorkMessage.sender`, but WhatsApp's own notification format never populates `sender` for a
 * 1:1 chat (no "Sender: " prefix — the notification title, stored as `groupName`, already *is*
 * the contact's name), so a saved phone number could only ever be found for group messages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = WwmApplication::class)
class FindSavedPhoneForSenderTest {

    private lateinit var app: WwmApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // Same defensive reset used across this project's other Robolectric test classes:
        // several tests below save a contact named "Ahmed Hassan" — without wiping it here,
        // whichever test happens to run first leaves it behind for the next one, breaking a
        // test that specifically expects *no* match to exist yet.
        runBlocking { app.importantContactRepository.deleteAll() }
    }

    private fun individualMessage(contactName: String) = WorkMessage(
        groupName = contactName, // 1:1 chat: the notification title IS the contact's name
        sender = null,           // WhatsApp never prefixes a 1:1 notification with a sender
        text = "hello",
        timestamp = System.currentTimeMillis()
    )

    private fun groupMessage(groupName: String, senderName: String) = WorkMessage(
        groupName = groupName,
        sender = senderName,
        text = "hello",
        timestamp = System.currentTimeMillis()
    )

    @Test
    fun `finds the phone number for a 1-1 chat by matching groupName`() = runBlocking {
        app.importantContactRepository.upsert(ImportantContact(name = "Ahmed Hassan", phoneNumber = "201001234567"))

        val phone = findSavedPhoneForSender(app, individualMessage("Ahmed Hassan"))

        assertEquals("201001234567", phone)
    }

    @Test
    fun `finds the phone number for a group message by matching sender, not the group name`() = runBlocking {
        app.importantContactRepository.upsert(ImportantContact(name = "Ahmed Hassan", phoneNumber = "201001234567"))

        val phone = findSavedPhoneForSender(app, groupMessage(groupName = "Sales Team", senderName = "Ahmed Hassan"))

        assertEquals("201001234567", phone)
    }

    @Test
    fun `returns null when no saved contact matches either name`() = runBlocking {
        app.importantContactRepository.upsert(ImportantContact(name = "Someone Else", phoneNumber = "201009999999"))

        assertNull(findSavedPhoneForSender(app, individualMessage("Ahmed Hassan")))
        assertNull(findSavedPhoneForSender(app, groupMessage("Sales Team", "Ahmed Hassan")))
    }

    @Test
    fun `returns null when the matching contact has no phone number saved`() = runBlocking {
        app.importantContactRepository.upsert(ImportantContact(name = "Ahmed Hassan", phoneNumber = null))

        assertNull(findSavedPhoneForSender(app, individualMessage("Ahmed Hassan")))
    }

    @Test
    fun `extractPhoneNumberIfPresent recognizes a raw WhatsApp phone-number name`() {
        assertEquals("971562331154", extractPhoneNumberIfPresent("+971 56 233 1154"))
    }

    @Test
    fun `extractPhoneNumberIfPresent returns null for an ordinary contact or group name`() {
        assertNull(extractPhoneNumberIfPresent("Ahmed Hassan"))
        assertNull(extractPhoneNumberIfPresent("Sales Team"))
        assertNull(extractPhoneNumberIfPresent("+1 Team")) // starts with a digit but has letters after
    }

    @Test
    fun `findSavedPhoneForSender falls back to the sender name itself when it's already a phone number`() = runBlocking {
        // No Important Person saved for this number at all — the fallback should still work
        // since the WhatsApp "name" (no contact saved on the phone) already IS the number.
        val phone = findSavedPhoneForSender(app, individualMessage("+971 56 233 1154"))
        assertEquals("971562331154", phone)
    }

    @Test
    fun `a saved contact whose number matches by digits is found even if its saved name text differs`() = runBlocking {
        // Regression test: a contact saved as "Ahmed Hassan" (a chosen label, not the raw
        // WhatsApp name) with the correct phone number must still be matched for a message
        // whose display name is the raw number itself — name-substring matching alone would
        // miss this ("+971 56 233 1154" does not contain "Ahmed Hassan"), so this relies on
        // the phone-digit fallback instead.
        app.importantContactRepository.upsert(ImportantContact(name = "Ahmed Hassan", phoneNumber = "971562331154"))

        val phone = findSavedPhoneForSender(app, individualMessage("+971 56 233 1154"))

        assertEquals("971562331154", phone)
    }

    @Test
    fun `a saved contact's exact number match is preferred and used correctly for a raw-number display name`() = runBlocking {
        app.importantContactRepository.upsert(ImportantContact(name = "+971 56 233 1154", phoneNumber = "971562331154"))

        val phone = findSavedPhoneForSender(app, individualMessage("+971 56 233 1154"))

        assertEquals("971562331154", phone)
    }

    @Test
    fun `matches when the saved name is longer and more specific than the message's sender name`() = runBlocking {
        // Regression test: this exact case ("Use" falling back to copy-only seemingly at
        // random) was caused by name matching only checking one direction — the message's
        // name had to *contain* the saved name, which silently failed whenever the saved
        // label was longer (e.g. a role or company appended) than what a message actually
        // shows as the sender.
        app.importantContactRepository.upsert(ImportantContact(name = "Eng. Ahmed Hassan - Supplier", phoneNumber = "201234567890"))

        val phone = findSavedPhoneForSender(app, individualMessage("Ahmed Hassan"))

        assertEquals("201234567890", phone)
    }

    @Test
    fun `matches regardless of extra internal whitespace in either name`() = runBlocking {
        app.importantContactRepository.upsert(ImportantContact(name = "Ahmed   Hassan", phoneNumber = "201234567890"))

        val phone = findSavedPhoneForSender(app, individualMessage("Ahmed Hassan"))

        assertEquals("201234567890", phone)
    }
}
