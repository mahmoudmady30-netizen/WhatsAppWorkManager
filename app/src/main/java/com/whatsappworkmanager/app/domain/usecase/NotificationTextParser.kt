package com.whatsappworkmanager.app.domain.usecase

/**
 * Pure, Android-free parsing logic used by WhatsAppNotificationListenerService. Kept separate
 * from the service class specifically so it can be unit-tested without any framework mocks.
 */
class NotificationTextParser {

    data class Parsed(val sender: String?, val message: String)

    /**
     * WhatsApp typically formats a group notification's body as "Sender: message text".
     * A direct-chat notification has no such prefix — its title is the contact name and the
     * body is just the message. We can't reliably tell these apart from the body alone, so we
     * apply a conservative heuristic: only treat "X: Y" as sender/message if X looks like a
     * short name (<= 40 chars) and Y is non-blank. Anything else is returned as-is with a null
     * sender, so a message that happens to contain a colon is never corrupted.
     */
    fun parse(rawText: String): Parsed {
        val separatorIndex = rawText.indexOf(": ")
        if (separatorIndex in 1..40) {
            val possibleSender = rawText.substring(0, separatorIndex)
            val possibleMessage = rawText.substring(separatorIndex + 2)
            if (possibleMessage.isNotBlank()) {
                return Parsed(possibleSender, possibleMessage)
            }
        }
        return Parsed(null, rawText)
    }
}
