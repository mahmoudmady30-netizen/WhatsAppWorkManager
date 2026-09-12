package com.whatsappworkmanager.app.data.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The local, fully-offline provider deliberately doesn't override `refineReply` — there's no
 * on-device language model to actually do the polishing, so the interface's default
 * implementation (return the draft unchanged) is the honest, correct behavior here rather than
 * pretending to improve text it can't.
 */
class LocalRuleBasedAiProviderTest {

    private val provider = LocalRuleBasedAiProvider()

    @Test
    fun `refineReply returns the draft unchanged since there's no local model to polish it`() = runBlocking {
        val draft = "plz confirm asap thx"
        val result = provider.refineReply(draft, "en")
        assertEquals(draft, result)
    }

    @Test
    fun `refineReply is a safe no-op for Arabic drafts too`() = runBlocking {
        val draft = "تمام هرد عليك بعدين"
        val result = provider.refineReply(draft, "ar")
        assertEquals(draft, result)
    }
}
