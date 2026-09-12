package com.whatsappworkmanager.app.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyDetectorTest {

    private val detector = ReplyDetector()

    @Test
    fun `question mark triggers needs reply`() {
        assertTrue(detector.needsReply("Are we still on for 5pm?"))
    }

    @Test
    fun `arabic question mark triggers needs reply`() {
        assertTrue(detector.needsReply("هنتقابل الساعة كام؟"))
    }

    @Test
    fun `known english phrase triggers needs reply`() {
        assertTrue(detector.needsReply("Any update on this?"))
    }

    @Test
    fun `known egyptian phrase triggers needs reply`() {
        assertTrue(detector.needsReply("محتاجين تحديث بخصوص التارجت"))
    }

    @Test
    fun `plain statement does not need reply`() {
        assertFalse(detector.needsReply("Thanks, sounds good."))
    }

    @Test
    fun `custom phrase is honored`() {
        val custom = ReplyDetector(customPhrases = listOf("ping me"))
        assertTrue(custom.needsReply("ping me when you land"))
    }
}
