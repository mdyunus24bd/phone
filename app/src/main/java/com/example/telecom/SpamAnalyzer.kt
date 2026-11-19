package com.example.telecom

import com.example.data.local.SpamRuleEntity

enum class SpamRiskLevel(val label: String) {
    SAFE("Verified / Safe"),
    CAUTION("Suspicious Pattern"),
    HIGH_RISK("High Spam Risk"),
    BLOCKED("Blocked Rule Match")
}

data class SpamAssessment(
    val score: Int, // 0..100
    val level: SpamRiskLevel,
    val reason: String,
    val matchedRuleId: Long? = null
)

/**
 * Multi-factor local + user-rule spam risk analyzer for SIM phone numbers.
 */
object SpamAnalyzer {

    private val suspiciousPrefixes = listOf(
        "1900", "+1900", "0900", "900", "0870", "0871"
    )

    fun evaluate(
        address: String,
        isKnownContact: Boolean,
        customRules: List<SpamRuleEntity>
    ): SpamAssessment {
        val trimmed = address.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }

        // 1. Check explicit user-managed blocklist / prefix rules first
        for (rule in customRules) {
            val cleanPattern = rule.pattern.trim()
            if (cleanPattern.isEmpty()) continue
            val matched = if (cleanPattern.endsWith("*")) {
                val prefix = cleanPattern.dropLast(1)
                trimmed.startsWith(prefix, ignoreCase = true) ||
                    digitsOnly.startsWith(prefix.filter { it.isDigit() })
            } else {
                trimmed.equals(cleanPattern, ignoreCase = true) ||
                    (digitsOnly.isNotEmpty() && digitsOnly == cleanPattern.filter { it.isDigit() })
            }
            if (matched) {
                return SpamAssessment(
                    score = rule.riskScore.coerceIn(75, 100),
                    level = if (rule.autoBlock) SpamRiskLevel.BLOCKED else SpamRiskLevel.HIGH_RISK,
                    reason = "Matched rule: ${rule.label} (${rule.pattern})",
                    matchedRuleId = rule.id
                )
            }
        }

        if (isKnownContact) {
            return SpamAssessment(
                score = 0,
                level = SpamRiskLevel.SAFE,
                reason = "Saved in your encrypted contacts"
            )
        }

        // 2. Structural heuristic analysis
        var score = 0
        val reasons = mutableListOf<String>()

        if (trimmed.isEmpty() ||
            trimmed.equals("private", ignoreCase = true) ||
            trimmed.equals("unknown", ignoreCase = true) ||
            trimmed.equals("anonymous", ignoreCase = true)
        ) {
            score += 65
            reasons.add("Hidden or anonymous caller ID")
        }

        if (suspiciousPrefixes.any { trimmed.startsWith(it) || digitsOnly.startsWith(it) }) {
            score += 70
            reasons.add("Premium-rate or telemarketing prefix")
        }

        if (digitsOnly.length >= 7 && digitsOnly.toSet().size <= 2) {
            score += 60
            reasons.add("Spoofed repeating digit sequence")
        }

        if (digitsOnly.length in 1..4) {
            score += 35
            reasons.add("Shortcode / automated dialer length")
        }

        val finalScore = score.coerceIn(0, 100)
        val level = when {
            finalScore >= 70 -> SpamRiskLevel.HIGH_RISK
            finalScore >= 35 -> SpamRiskLevel.CAUTION
            else -> SpamRiskLevel.SAFE
        }

        return SpamAssessment(
            score = finalScore,
            level = level,
            reason = if (reasons.isEmpty()) "No spam indicators detected" else reasons.joinToString(" • ")
        )
    }
}
