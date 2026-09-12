package com.whatsappworkmanager.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordScoringTest {

    private val scoring = KeywordScoring()

    @Test
    fun `plain message scores zero`() {
        assertEquals(0, scoring.score("see you tomorrow"))
        assertFalse(scoring.isImportant("see you tomorrow"))
    }

    @Test
    fun `urgent keyword alone crosses important threshold`() {
        val text = "This is urgent, please handle it"
        assertTrue(scoring.score(text) >= KeywordScoring.IMPORTANT_THRESHOLD)
        assertTrue(scoring.isImportant(text))
    }

    @Test
    fun `arabic urgent keyword scores the same weight`() {
        val text = "الموضوع عاجل جدًا"
        assertTrue(scoring.isImportant(text))
    }

    @Test
    fun `combination of manager and customer reaches high priority`() {
        // manager(3) + customer(3) + complaint(4) = 10 >= 8
        val text = "The manager escalated a customer complaint"
        assertTrue(scoring.isHighPriority(text))
    }

    @Test
    fun `custom keyword rule adds to score`() {
        val custom = KeywordScoring(
            extraRules = listOf(
                com.whatsappworkmanager.app.domain.model.KeywordRule(
                    id = 1, keyword = "invoice", priority = 6, enabled = true
                )
            )
        )
        assertTrue(custom.isImportant("please send the invoice"))
    }

    @Test
    fun `disabled custom rule does not contribute`() {
        val custom = KeywordScoring(
            extraRules = listOf(
                com.whatsappworkmanager.app.domain.model.KeywordRule(
                    id = 1, keyword = "invoice", priority = 6, enabled = false
                )
            )
        )
        assertFalse(custom.isImportant("please send the invoice"))
    }
}
