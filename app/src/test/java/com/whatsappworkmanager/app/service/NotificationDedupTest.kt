package com.whatsappworkmanager.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Covers WhatsAppNotificationListenerService.alreadyProcessed — the fix for duplicates that
 * kept recurring even after the timestamp-tolerance widening: a periodic catch-up scan (or a
 * listener reconnect) re-scans every currently active notification, and when `postTime` is
 * missing/invalid this app's fallback is a *fresh* `currentTimeMillis()` on every call, so a
 * re-scan of the exact same unchanged notification looked like a brand-new message arriving
 * minutes or hours later — comfortably outside any reasonable timestamp window. Each test uses
 * its own random key, since the underlying map is a shared, unbounded-across-tests companion
 * object store with no reset hook (intentionally — nothing else in this app needs one either).
 */
class NotificationDedupTest {

    private fun uniqueKey() = "test-${UUID.randomUUID()}"

    @Test
    fun `the same key and text seen again is treated as already processed`() {
        val key = uniqueKey()
        assertFalse(WhatsAppNotificationListenerService.alreadyProcessed(key, "hello there"))
        assertTrue(WhatsAppNotificationListenerService.alreadyProcessed(key, "hello there"))
    }

    @Test
    fun `the same key with genuinely different text is not blocked`() {
        // The core correctness requirement: a notification updated in place with new content
        // (an appended-message style notification, for instance) must still get through —
        // blocking on key alone, without also checking the text, would have silently
        // swallowed every message after the first one in a thread like that.
        val key = uniqueKey()
        assertFalse(WhatsAppNotificationListenerService.alreadyProcessed(key, "first message"))
        assertFalse(WhatsAppNotificationListenerService.alreadyProcessed(key, "second message"))
    }

    @Test
    fun `a null key never blocks anything`() {
        assertFalse(WhatsAppNotificationListenerService.alreadyProcessed(null, "hello"))
        assertFalse(WhatsAppNotificationListenerService.alreadyProcessed(null, "hello"))
    }

    @Test
    fun `different keys with the same text do not interfere with each other`() {
        val keyA = uniqueKey()
        val keyB = uniqueKey()
        assertFalse(WhatsAppNotificationListenerService.alreadyProcessed(keyA, "same text"))
        assertFalse(WhatsAppNotificationListenerService.alreadyProcessed(keyB, "same text"))
        assertTrue(WhatsAppNotificationListenerService.alreadyProcessed(keyA, "same text"))
        assertTrue(WhatsAppNotificationListenerService.alreadyProcessed(keyB, "same text"))
    }
}
