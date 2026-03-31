package com.qlty.intellij.model

import kotlinx.serialization.Serializable

@Serializable
data class Issue(
    val tool: String = "",
    val driver: String = "",
    val ruleKey: String = "",
    val message: String = "",
    val snippet: String = "",
    val snippetWithContext: String = "",
    val level: String = "LEVEL_UNSPECIFIED",
    val category: String = "CATEGORY_UNSPECIFIED",
    val language: String = "LANGUAGE_UNSPECIFIED",
    val documentationUrl: String = "",
    val replacement: String = "",
    val location: Location = Location(),
    val otherLocations: List<Location> = emptyList(),
    val suggestions: List<Suggestion> = emptyList(),
    val effortMinutes: Int = 0,
    val value: Int = 0,
    val valueDelta: Int = 0,
    val fingerprint: String = "",
    val mode: String = "MODE_UNSPECIFIED",
    val onAddedLine: Boolean = false,
    val tags: List<String> = emptyList(),
)

@Serializable
data class Location(
    val path: String = "",
    val range: Range = Range(),
)

@Serializable
data class Range(
    val startLine: Int = 0,
    val startColumn: Int = 0,
    val endLine: Int = 0,
    val endColumn: Int = 0,
    val startByte: Int? = null,
    val endByte: Int? = null,
)

@Serializable
data class Suggestion(
    val id: String = "",
    val description: String = "",
    val patch: String = "",
    val unsafe: Boolean = false,
    val source: String = "SUGGESTION_SOURCE_UNSPECIFIED",
    val replacements: List<Replacement> = emptyList(),
)

@Serializable
data class Replacement(
    val data: String = "",
    val location: Location = Location(),
)
