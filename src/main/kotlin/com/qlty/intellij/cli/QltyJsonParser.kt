package com.qlty.intellij.cli

import com.intellij.openapi.diagnostic.Logger
import com.qlty.intellij.model.Issue
import kotlinx.serialization.json.Json

object QltyJsonParser {
    private val logger = Logger.getInstance(QltyJsonParser::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun parseIssues(output: String): List<Issue> {
        val trimmed = output.trim()
        if (trimmed.isEmpty() || trimmed == "[]") {
            return emptyList()
        }
        return try {
            json.decodeFromString<List<Issue>>(trimmed)
        } catch (e: Exception) {
            logger.warn("Failed to parse Qlty JSON output: ${e.message}")
            emptyList()
        }
    }
}
