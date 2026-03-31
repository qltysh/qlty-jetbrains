package com.qlty.intellij.cli

import com.qlty.intellij.model.Issue
import kotlinx.serialization.json.Json

object QltyJsonParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parseIssues(output: String): List<Issue> {
        val trimmed = output.trim()
        if (trimmed.isEmpty() || trimmed == "[]") {
            return emptyList()
        }
        return json.decodeFromString<List<Issue>>(trimmed)
    }
}
