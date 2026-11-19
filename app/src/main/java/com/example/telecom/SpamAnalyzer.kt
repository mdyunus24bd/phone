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

    /**
     * Checks whether [address] matches a custom [SpamRuleEntity] pattern, supporting:
     * - Exact match (case-insensitive)
     * - Wildcard prefix patterns ending with `*` (e.g. `+1800*`)
     * - Digit-only exact match (ignoring formatting dashes/spaces/parentheses)
     * - Normalized last-10-digit suffix match so `+8801712345678` matches `01712345678`
     */
    fun matchesRule(address: String, rule: SpamRuleEntity): Boolean {
        val trimmed = address.trim()
        val cleanPattern = rule.pattern.trim()
        if (trimmed.isEmpty() || cleanPattern.isEmpty()) return false

        val digitsOnly = trimmed.filter { it.isDigit() }
        val patternDigits = cleanPattern.filter { it.isDigit() }

        return if (cleanPattern.endsWith("*")) {
            val rawPrefix = cleanPattern.dropLast(1).trim()
            val digitPrefix = rawPrefix.filter { it.isDigit() }
            (rawPrefix.isNotEmpty() && trimmed.startsWith(rawPrefix, ignoreCase = true)) ||
                (digitPrefix.isNotEmpty() && digitsOnly.startsWith(digitPrefix))
        } else {
            trimmed.equals(cleanPattern, ignoreCase = true) ||
                (digitsOnly.isNotEmpty() && patternDigits.isNotEmpty() && digitsOnly == patternDigits) ||
                (digitsOnly.length >= 10 && patternDigits.length >= 10 &&
                    digitsOnly.takeLast(10) == patternDigits.takeLast(10))
        }
    }

    /**
     * Returns the first auto-blocking rule matching [address], or null if calls from [address]
     * should not be automatically rejected.
     */
    fun findBlockingRule(
        address: String,
        customRules: List<SpamRuleEntity>
    ): SpamRuleEntity? {
        return customRules.firstOrNull { rule ->
            rule.autoBlock && matchesRule(address, rule)
        }
    }

    fun evaluate(
        address: String,
        isKnownContact: Boolean,
        customRules: List<SpamRuleEntity>
    ): SpamAssessment {
        val trimmed = address.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }

        // 1. Check explicit user-managed blocklist / prefix rules first
        for (rule in customRules) {
            if (matchesRule(trimmed, rule)) {
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
