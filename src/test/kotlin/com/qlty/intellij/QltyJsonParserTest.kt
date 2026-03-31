package com.qlty.intellij

import com.qlty.intellij.cli.QltyJsonParser
import org.junit.Assert.*
import org.junit.Test

class QltyJsonParserTest {

    @Test
    fun parseEmptyArray() {
        val issues = QltyJsonParser.parseIssues("[]")
        assertTrue(issues.isEmpty())
    }

    @Test
    fun parseEmptyString() {
        val issues = QltyJsonParser.parseIssues("")
        assertTrue(issues.isEmpty())
    }

    @Test
    fun parseSingleIssue() {
        val json = """
        [
            {
                "tool": "eslint",
                "ruleKey": "no-unused-vars",
                "message": "Variable 'x' is defined but never used",
                "level": "LEVEL_MEDIUM",
                "category": "CATEGORY_LINT",
                "documentationUrl": "https://eslint.org/docs/rules/no-unused-vars",
                "location": {
                    "path": "src/index.ts",
                    "range": {
                        "startLine": 10,
                        "startColumn": 5,
                        "endLine": 10,
                        "endColumn": 15
                    }
                },
                "suggestions": []
            }
        ]
        """.trimIndent()

        val issues = QltyJsonParser.parseIssues(json)
        assertEquals(1, issues.size)

        val issue = issues[0]
        assertEquals("eslint", issue.tool)
        assertEquals("no-unused-vars", issue.ruleKey)
        assertEquals("LEVEL_MEDIUM", issue.level)
        assertEquals("CATEGORY_LINT", issue.category)
        assertEquals(10, issue.location.range.startLine)
        assertEquals(5, issue.location.range.startColumn)
        assertEquals(10, issue.location.range.endLine)
        assertEquals(15, issue.location.range.endColumn)
    }

    @Test
    fun parseIssueWithSuggestions() {
        val json = """
        [
            {
                "tool": "clippy",
                "ruleKey": "needless_return",
                "message": "unneeded return statement",
                "level": "LEVEL_LOW",
                "category": "CATEGORY_STYLE",
                "location": {
                    "path": "src/main.rs",
                    "range": {
                        "startLine": 5,
                        "startColumn": 1,
                        "endLine": 5,
                        "endColumn": 20
                    }
                },
                "suggestions": [
                    {
                        "id": "fix-1",
                        "description": "Remove needless return",
                        "patch": "",
                        "source": "SUGGESTION_SOURCE_TOOL",
                        "replacements": [
                            {
                                "data": "value",
                                "location": {
                                    "path": "src/main.rs",
                                    "range": {
                                        "startLine": 5,
                                        "startColumn": 1,
                                        "endLine": 5,
                                        "endColumn": 20
                                    }
                                }
                            }
                        ]
                    }
                ]
            }
        ]
        """.trimIndent()

        val issues = QltyJsonParser.parseIssues(json)
        assertEquals(1, issues.size)
        assertEquals(1, issues[0].suggestions.size)
        assertEquals(1, issues[0].suggestions[0].replacements.size)
        assertEquals("value", issues[0].suggestions[0].replacements[0].data)
        assertEquals("Remove needless return", issues[0].suggestions[0].description)
    }

    @Test
    fun parseIgnoresUnknownFields() {
        val json = """
        [
            {
                "tool": "rubocop",
                "ruleKey": "Style/FrozenStringLiteralComment",
                "message": "Missing frozen string literal comment",
                "level": "LEVEL_LOW",
                "category": "CATEGORY_STYLE",
                "unknownField": "should be ignored",
                "anotherUnknown": 42,
                "location": {
                    "path": "app.rb",
                    "range": {
                        "startLine": 1,
                        "startColumn": 1,
                        "endLine": 1,
                        "endColumn": 1
                    }
                }
            }
        ]
        """.trimIndent()

        val issues = QltyJsonParser.parseIssues(json)
        assertEquals(1, issues.size)
        assertEquals("rubocop", issues[0].tool)
    }

    @Test
    fun parseMultipleIssues() {
        val json = """
        [
            {
                "tool": "eslint",
                "ruleKey": "no-unused-vars",
                "message": "Unused var",
                "level": "LEVEL_LOW",
                "category": "CATEGORY_LINT",
                "location": {"path": "a.ts", "range": {"startLine": 1, "startColumn": 1, "endLine": 1, "endColumn": 5}}
            },
            {
                "tool": "eslint",
                "ruleKey": "no-console",
                "message": "No console",
                "level": "LEVEL_LOW",
                "category": "CATEGORY_LINT",
                "location": {"path": "a.ts", "range": {"startLine": 2, "startColumn": 1, "endLine": 2, "endColumn": 10}}
            }
        ]
        """.trimIndent()

        val issues = QltyJsonParser.parseIssues(json)
        assertEquals(2, issues.size)
    }
}
