package com.whatsappworkmanager.app.domain.usecase

import com.whatsappworkmanager.app.domain.model.KeywordRule

/**
 * Rule-based + weighted scoring for message importance.
 * Pure function, no Android dependencies -> fully unit-testable.
 */
class KeywordScoring(
    private val extraRules: List<KeywordRule> = emptyList()
) {

    data class WeightedKeyword(val keyword: String, val weight: Int)

    // Built-in bilingual keyword weights (English + Egyptian Arabic).
    private val defaultKeywords: List<WeightedKeyword> = listOf(
        WeightedKeyword("urgent", 5),
        WeightedKeyword("عاجل", 5),
        WeightedKeyword("complaint", 4),
        WeightedKeyword("شكوى", 4),
        WeightedKeyword("escalation", 4),
        WeightedKeyword("تصعيد", 4),
        WeightedKeyword("manager", 3),
        WeightedKeyword("مدير", 3),
        WeightedKeyword("customer", 3),
        WeightedKeyword("عميل", 3),
        WeightedKeyword("problem", 3),
        WeightedKeyword("مشكلة", 3),
        WeightedKeyword("target", 2),
        WeightedKeyword("تارجت", 2),
        WeightedKeyword("sales", 2),
        WeightedKeyword("مبيعات", 2),
        WeightedKeyword("follow up", 2),
        WeightedKeyword("متابعة", 2),
        WeightedKeyword("?", 2) // question mark bump, combined with question words elsewhere
    )

    companion object {
        const val IMPORTANT_THRESHOLD = 5
        const val HIGH_PRIORITY_THRESHOLD = 8
    }

    /** Returns the total importance score for [text]. */
    fun score(text: String): Int {
        val normalized = text.lowercase()
        var total = 0

        for (kw in defaultKeywords) {
            if (normalized.contains(kw.keyword.lowercase())) {
                total += kw.weight
            }
        }
        for (rule in extraRules) {
            if (!rule.enabled) continue
            if (normalized.contains(rule.keyword.lowercase())) {
                total += rule.priority
            }
        }
        return total
    }

    fun isImportant(text: String): Boolean = score(text) >= IMPORTANT_THRESHOLD

    fun isHighPriority(text: String): Boolean = score(text) >= HIGH_PRIORITY_THRESHOLD
}
