package com.qlty.intellij.util

import com.intellij.lang.annotation.HighlightSeverity
import com.qlty.intellij.model.Issue

object SeverityMapper {
    private val ERROR_CATEGORIES = setOf(
        "CATEGORY_VULNERABILITY",
        "CATEGORY_SECURITY_HOTSPOT",
        "CATEGORY_TYPE_CHECK",
        "CATEGORY_SECRET",
        "CATEGORY_DEPENDENCY_ALERT",
    )

    fun mapSeverity(issue: Issue): HighlightSeverity {
        if (issue.category in ERROR_CATEGORIES) {
            return HighlightSeverity.ERROR
        }

        return when (issue.level) {
            "LEVEL_HIGH" -> HighlightSeverity.ERROR
            "LEVEL_MEDIUM", "LEVEL_LOW" -> HighlightSeverity.WARNING
            "LEVEL_NOTE", "LEVEL_FMT" -> HighlightSeverity.WEAK_WARNING
            else -> HighlightSeverity.WEAK_WARNING
        }
    }

    fun categoryPrefix(issue: Issue): String =
        when (issue.category) {
            "CATEGORY_DEPENDENCY_ALERT" -> "Dependency: "
            "CATEGORY_SECURITY_HOTSPOT" -> "Security: "
            "CATEGORY_VULNERABILITY" -> "Vulnerability: "
            "CATEGORY_SECRET" -> "Secret: "
            else -> ""
        }
}
