package com.qlty.intellij

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.qlty.intellij.cli.QltyRunner
import com.qlty.intellij.model.Issue
import com.qlty.intellij.model.Location
import com.qlty.intellij.model.Range
import com.qlty.intellij.model.Replacement
import com.qlty.intellij.model.Suggestion
import com.qlty.intellij.settings.QltySettings
import com.qlty.intellij.util.QltyProjectDetector
import com.qlty.intellij.util.SeverityMapper
import java.io.File

class QltyExternalAnnotatorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        QltyProjectDetector.clearCache()
        QltySettings.getInstance(project).enabled = true
    }

    fun testCollectInformationReturnsInputWhenEnabledAndConfigured() {
        createQltyConfig()
        val psiFile = createProjectFile("src/demo.kt", "fun demo() = Unit\n")
        val annotator = QltyExternalAnnotator()

        val input = annotator.collectInformation(psiFile, myFixture.editor, false)

        assertNotNull(input)
        assertEquals(psiFile.virtualFile.path, input?.filePath)
        assertEquals(project.basePath, input?.projectRoot)
        assertEquals(project, input?.project)
    }

    fun testCollectInformationReturnsNullWhenPluginDisabled() {
        createQltyConfig()
        QltySettings.getInstance(project).enabled = false
        val psiFile = createProjectFile("src/demo.kt", "fun demo() = Unit\n")
        val annotator = QltyExternalAnnotator()

        val input = annotator.collectInformation(psiFile, myFixture.editor, false)

        assertNull(input)
    }

    fun testDoAnnotateReturnsRunnerIssues() {
        val expectedIssue = Issue(message = "demo")
        val annotator = QltyExternalAnnotator(
            runnerFactory = {
                FakeRunner(issues = listOf(expectedIssue))
            },
        )

        val result = annotator.doAnnotate(QltyInput("/tmp/demo.kt", "/tmp", project))

        assertNotNull(result)
        assertEquals(listOf(expectedIssue), result?.issues)
    }

    fun testDoAnnotateReturnsNullWhenRunnerThrows() {
        val annotator = QltyExternalAnnotator(
            runnerFactory = {
                object : QltyRunner {
                    override fun analyzeFile(filePath: String, workDir: String): List<Issue> = error("boom")
                    override fun checkProject(workDir: String): String? = null
                    override fun fixFile(filePath: String, workDir: String) {}
                    override fun checkFileWithFilter(filePath: String, workDir: String, tool: String): String? = null
                    override fun fixProjectWithFilter(workDir: String, tool: String, ruleKey: String) {}
                    override fun formatFile(filePath: String, workDir: String) {}
                }
            },
        )

        val result = annotator.doAnnotate(QltyInput("/tmp/demo.kt", "/tmp", project))

        assertNull(result)
    }

    fun testApplyPipelineDeliversIssuesWithCorrectFieldsToApply() {
        createQltyConfig()
        val issue = Issue(
            tool = "eslint",
            ruleKey = "no-unused-vars",
            message = "Variable is unused",
            level = "LEVEL_MEDIUM",
            category = "CATEGORY_LINT",
            location = Location(
                path = "demo.kt",
                range = Range(
                    startLine = 1,
                    startColumn = 7,
                    endLine = 1,
                    endColumn = 12,
                ),
            ),
            suggestions = listOf(
                Suggestion(
                    description = "Remove variable",
                    replacements = listOf(
                        Replacement(
                            data = "",
                            location = Location(
                                path = "demo.kt",
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
            ),
        )

        val fakeRunner = FakeRunner(issues = listOf(issue))
        val annotator = QltyExternalAnnotator(runnerFactory = { fakeRunner })

        createProjectFile("src/demo.kt", "hello world\nsecond line\n")

        val input = annotator.collectInformation(myFixture.file, myFixture.editor, false)
        assertNotNull("collectInformation should return non-null for configured project", input)

        val result = annotator.doAnnotate(input)
        assertNotNull("doAnnotate should return non-null result", result)
        assertEquals(1, result!!.issues.size)

        val resultIssue = result.issues[0]
        assertEquals("eslint", resultIssue.tool)
        assertEquals("no-unused-vars", resultIssue.ruleKey)
        assertEquals("Variable is unused", resultIssue.message)
    }

    fun testApplyAnnotationOffsetCalculation() {
        val psiFile = myFixture.configureByText("demo.kt", "hello world\nsecond line\n")
        val document = myFixture.editor.document

        val issue = Issue(
            tool = "eslint",
            ruleKey = "no-unused-vars",
            message = "Variable is unused",
            level = "LEVEL_MEDIUM",
            category = "CATEGORY_LINT",
            location = Location(
                path = "demo.kt",
                range = Range(
                    startLine = 1,
                    startColumn = 7,
                    endLine = 1,
                    endColumn = 12,
                ),
            ),
        )

        val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)
        val lineStartOffset = document.getLineStartOffset(adjusted.startLine)
        val startOffset = lineStartOffset + adjusted.startCol
        val endOffset = document.getLineStartOffset(adjusted.endLine) + adjusted.endCol

        assertEquals(0, adjusted.startLine)
        assertEquals(6, adjusted.startCol)
        assertEquals(11, adjusted.endCol)
        assertEquals(6, startOffset)
        assertEquals(11, endOffset)

        val prefix = SeverityMapper.categoryPrefix(issue)
        val toolAndRule = "${issue.tool}:${issue.ruleKey}"
        val message = "$prefix$toolAndRule: ${issue.message}"
        assertEquals("eslint:no-unused-vars: Variable is unused", message)

        val severity = SeverityMapper.mapSeverity(issue)
        assertEquals(com.intellij.lang.annotation.HighlightSeverity.WARNING, severity)
    }

    private fun createQltyConfig() {
        val projectRoot = requireNotNull(project.basePath)
        File(projectRoot, ".qlty").mkdirs()
        File(projectRoot, ".qlty/qlty.toml").writeText("version = 1\n")
    }

    private fun createProjectFile(
        relativePath: String,
        contents: String,
    ) = run {
        val projectRoot = requireNotNull(project.basePath)
        val ioFile = File(projectRoot, relativePath).apply {
            parentFile.mkdirs()
            writeText(contents)
        }
        val virtualFile = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile))
        myFixture.openFileInEditor(virtualFile)
        requireNotNull(PsiManager.getInstance(project).findFile(virtualFile))
    }
}
