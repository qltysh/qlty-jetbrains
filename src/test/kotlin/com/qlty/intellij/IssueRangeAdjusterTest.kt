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

    fun testFileComplexityCollapseToStartLine() {
        val document = EditorFactory.getInstance().createDocument("class Foo {\n  fun bar() {}\n}\n")
        val issue = Issue(
            tool = "qlty",
            ruleKey = "file-complexity",
            location = Location(range = Range(startLine = 1, endLine = 3)),
        )

        val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

        assertEquals(0, adjusted.startLine)
        assertEquals(0, adjusted.endLine)
        assertEquals(0, adjusted.startCol)
        assertEquals(0, adjusted.endCol)
    }

    fun testSimilarCodeCollapseToStartLine() {
        val document = EditorFactory.getInstance().createDocument("val x = 1\nval y = 1\n")
        val issue = Issue(
            tool = "qlty",
            ruleKey = "similar-code",
            location = Location(range = Range(startLine = 1, endLine = 2)),
        )

        val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

        assertEquals(0, adjusted.startLine)
        assertEquals(0, adjusted.endLine)
        assertEquals(0, adjusted.startCol)
        assertEquals(0, adjusted.endCol)
    }

    fun testIdenticalCodeCollapseToStartLine() {
        val document = EditorFactory.getInstance().createDocument("val x = 1\nval y = 1\n")
        val issue = Issue(
            tool = "qlty",
            ruleKey = "identical-code",
            location = Location(range = Range(startLine = 1, endLine = 2)),
        )

        val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

        assertEquals(0, adjusted.startLine)
        assertEquals(0, adjusted.endLine)
        assertEquals(0, adjusted.startCol)
        assertEquals(0, adjusted.endCol)
    }

    fun testReturnStatementsHighlightsFunctionName() {
        val document = EditorFactory.getInstance().createDocument("fun calculate() = Unit\n")
        val issue = Issue(
            tool = "qlty",
            ruleKey = "return-statements",
            message = "Too many return statements in calculate",
            snippet = "fun calculate() = Unit",
            location = Location(range = Range(startLine = 1, endLine = 1)),
        )

        val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

        assertEquals(0, adjusted.startLine)
        assertEquals(0, adjusted.endLine)
        assertEquals(4, adjusted.startCol)
        assertEquals(13, adjusted.endCol)
    }

    fun testFunctionComplexityHighlightsFunctionNameWhenFound() {
        val document = EditorFactory.getInstance().createDocument("fun doWork() = Unit\n")
        val issue = Issue(
            tool = "qlty",
            ruleKey = "function-complexity",
            message = "Function complexity for doWork",
            snippet = "fun doWork() = Unit",
            location = Location(range = Range(startLine = 1, endLine = 1)),
        )

        val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

        assertEquals(0, adjusted.startLine)
        assertEquals(0, adjusted.endLine)
        assertEquals(4, adjusted.startCol)
        assertEquals(10, adjusted.endCol)
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
