package com.whatsappworkmanager.app.domain.usecase

/**
 * Combines scoring + reply detection + sender-based importance into a single classification
 * result for a freshly-captured notification's message text.
 */
class MessageClassifier(
    private val keywordScoring: KeywordScoring,
    private val replyDetector: ReplyDetector
) {

    data class Classification(
        val score: Int,
        val isImportant: Boolean,
        val isHighPriority: Boolean,
        val needsReply: Boolean,
        val importantBecauseOfSender: Boolean = false
    )

    /**
     * @param sender the extracted sender name (nullable — direct chats or unparsed notifications
     *   may not have one; see NotificationTextParser).
     * @param importantSenderNames names of people marked "Important" in Settings → Important
     *   People. Matching is case-insensitive substring match, so "Ahmed" matches a sender
     *   string like "Ahmed Hassan".
     */
    fun classify(
        text: String,
        sender: String? = null,
        importantSenderNames: List<String> = emptyList()
    ): Classification {
        val score = keywordScoring.score(text)
        val senderIsImportant = sender != null && importantSenderNames.any { important ->
            important.isNotBlank() && sender.contains(important, ignoreCase = true)
        }
        return Classification(
            score = score,
            isImportant = score >= KeywordScoring.IMPORTANT_THRESHOLD || senderIsImportant,
            isHighPriority = score >= KeywordScoring.HIGH_PRIORITY_THRESHOLD || senderIsImportant,
            needsReply = replyDetector.needsReply(text),
            importantBecauseOfSender = senderIsImportant
        )
    }
}
