package com.whatsappworkmanager.app.domain.usecase

/**
 * Detects whether a message text looks like it is asking for a reply/update/confirmation.
 * Phrase list is user-editable from Settings (see [customPhrases]); defaults cover common
 * English + Egyptian-Arabic patterns.
 */
class ReplyDetector(
    private val customPhrases: List<String> = emptyList()
) {

    private val defaultPhrases: List<String> = listOf(
        "ممكن تأكد",
        "محتاج رد",
        "هل تم",
        "any update",
        "please confirm",
        "مين مسؤول",
        "محتاجين تحديث",
        "ينفع تأكد",
        "لسه؟",
        "رد لو سمحت",
        "تقدر ترد",
        "ready?",
        "confirm please",
        "waiting for your reply",
        "في انتظار ردك"
    )

    private val questionMarkers = listOf("?", "؟")

    fun needsReply(text: String): Boolean {
        val normalized = text.lowercase()
        val phraseHit = (defaultPhrases + customPhrases).any {
            normalized.contains(it.lowercase())
        }
        val looksLikeQuestion = questionMarkers.any { normalized.contains(it) }
        return phraseHit || looksLikeQuestion
    }
}
