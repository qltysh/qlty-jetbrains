package com.qlty.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.qlty.intellij.fixes.QltyQuickFix
import com.qlty.intellij.model.Location
import com.qlty.intellij.model.Range
import com.qlty.intellij.model.Replacement
import com.qlty.intellij.model.Suggestion

class QltyQuickFixTest : BasePlatformTestCase() {
    fun testAppliesSingleReplacementUsingOneBasedColumns() {
        val psiFile = myFixture.configureByText("demo.txt", "hello world\n")

        val fix = QltyQuickFix(
            "demo:replace",
            Suggestion(
                description = "Replace world",
                replacements = listOf(
                    Replacement(
                        data = "there",
                        location = Location(
                            path = "demo.txt",
                            range = Range(
                                startLine = 1,
                                startColumn = 7,
                                endLine = 1,
                                endColumn = 12,
                            ),
                        ),
                    ),
                ),
            ),
        )

        fix.invoke(project, myFixture.editor, psiFile)

        assertEquals("hello there\n", myFixture.editor.document.text)
    }

    fun testAppliesMultipleReplacementsFromBottomToTop() {
        val psiFile = myFixture.configureByText("demo.txt", "alpha beta gamma\n")

        val fix = QltyQuickFix(
            "demo:replace-many",
            Suggestion(
                description = "Replace multiple ranges",
                replacements = listOf(
                    Replacement(
                        data = "A",
                        location = Location(
                            path = "demo.txt",
                            range = Range(
                                startLine = 1,
                                startColumn = 1,
                                endLine = 1,
                                endColumn = 6,
                            ),
                        ),
                    ),
                    Replacement(
                        data = "G",
                        location = Location(
                            path = "demo.txt",
                            range = Range(
                                startLine = 1,
                                startColumn = 12,
                                endLine = 1,
                                endColumn = 17,
                            ),
                        ),
                    ),
                ),
            ),
        )

        fix.invoke(project, myFixture.editor, psiFile)

        assertEquals("A beta G\n", myFixture.editor.document.text)
    }
}
