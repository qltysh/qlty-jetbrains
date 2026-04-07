package com.qlty.intellij

import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.qlty.intellij.model.Issue
import com.qlty.intellij.model.Location
import com.qlty.intellij.model.Range

class IssueRangeAdjusterTest : BasePlatformTestCase() {
    fun testNonQltyIssueUsesDirectRangeOffsets() {
        val document = EditorFactory.getInstance().createDocument("hello world\n")
        val issue = Issue(
            tool = "eslint",
            location = Location(
                range = Range(
                    startLine = 1,
                    startColumn = 7,
                    endLine = 1,
                    endColumn = 12,
                ),
            ),
        )

        val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

        assertEquals(0, adjusted.startLine)
        assertEquals(0, adjusted.endLine)
        assertEquals(6, adjusted.startCol)
        assertEquals(11, adjusted.endCol)
    }

    fun testNestedControlFlowUsesFirstKeywordLength() {
        val document = EditorFactory.getInstance().createDocument("if (x) {\n}\n")
        val issue = Issue(
            tool = "qlty",
            ruleKey = "nested-control-flow",
            snippet = "  if (x) {",
            location = Location(range = Range(startLine = 1, endLine = 1)),
        )

        val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

        assertEquals(0, adjusted.startCol)
        assertEquals(2, adjusted.endCol)
    }

    fun testFunctionComplexityFallsBackWhenFunctionNameIsMissing() {
        val document = EditorFactory.getInstance().createDocument("fun demo() = Unit\n")
        val issue = Issue(
            tool = "qlty",
            ruleKey = "function-complexity",
            message = "Function complexity for missingName",
            snippet = "fun demo() = Unit",
            location = Location(range = Range(startLine = 1, endLine = 1)),
        )

        val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

        assertEquals(0, adjusted.startCol)
        assertEquals(0, adjusted.endCol)
    }
}
