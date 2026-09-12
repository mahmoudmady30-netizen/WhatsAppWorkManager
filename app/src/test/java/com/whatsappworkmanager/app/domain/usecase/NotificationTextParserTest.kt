package com.whatsappworkmanager.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTextParserTest {

    private val parser = NotificationTextParser()

    @Test
    fun `standard sender colon message format splits correctly`() {
        val result = parser.parse("Ahmed: Please confirm the target numbers")
        assertEquals("Ahmed", result.sender)
        assertEquals("Please confirm the target numbers", result.message)
    }

    @Test
    fun `direct chat with no sender prefix returns null sender`() {
        val result = parser.parse("Please confirm the target numbers")
        assertNull(result.sender)
        assertEquals("Please confirm the target numbers", result.message)
    }

    @Test
    fun `message containing a colon but no short sender name is not split incorrectly`() {
        val longPrefixText = "This is a very long sentence that happens to contain: a colon in the middle"
        val result = parser.parse(longPrefixText)
        assertNull(result.sender)
        assertEquals(longPrefixText, result.message)
    }

    @Test
    fun `trailing colon with blank remainder falls back to full text`() {
        val result = parser.parse("Ahmed: ")
        assertNull(result.sender)
        assertEquals("Ahmed: ", result.message)
    }

    @Test
    fun `arabic sender name splits correctly`() {
        val result = parser.parse("محمود: محتاج تحديث التارجت")
        assertEquals("محمود", result.sender)
        assertEquals("محتاج تحديث التارجت", result.message)
    }

    @Test
    fun `empty string does not crash`() {
        val result = parser.parse("")
        assertNull(result.sender)
        assertEquals("", result.message)
    }
}
