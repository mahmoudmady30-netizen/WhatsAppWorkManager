package com.whatsappworkmanager.app.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageClassifierTest {

    private val classifier = MessageClassifier(KeywordScoring(), ReplyDetector())

    @Test
    fun `urgent question is important and needs reply`() {
        val result = classifier.classify("Urgent - can you confirm the target numbers?")
        assertTrue(result.isImportant)
        assertTrue(result.needsReply)
    }

    @Test
    fun `routine update is neither important nor needing reply`() {
        val result = classifier.classify("Just finished the weekly report, all good.")
        assertFalse(result.isImportant)
        assertFalse(result.needsReply)
    }

    @Test
    fun `high priority threshold requires stacked keywords`() {
        val result = classifier.classify("Escalation: customer complaint from the branch manager")
        assertTrue(result.isHighPriority)
    }

    @Test
    fun `message from an important sender is important even with no keywords`() {
        val result = classifier.classify(
            text = "see you at lunch",
            sender = "Ahmed Hassan",
            importantSenderNames = listOf("Ahmed Hassan")
        )
        assertTrue(result.isImportant)
        assertTrue(result.importantBecauseOfSender)
    }

    @Test
    fun `important sender match is a case-insensitive substring match`() {
        val result = classifier.classify(
            text = "quick question",
            sender = "AHMED HASSAN (Branch Manager)",
            importantSenderNames = listOf("ahmed hassan")
        )
        assertTrue(result.isImportant)
    }

    @Test
    fun `sender not in important list does not force importance`() {
        val result = classifier.classify(
            text = "see you at lunch",
            sender = "Sam",
            importantSenderNames = listOf("Ahmed Hassan")
        )
        assertFalse(result.isImportant)
        assertFalse(result.importantBecauseOfSender)
    }
}
