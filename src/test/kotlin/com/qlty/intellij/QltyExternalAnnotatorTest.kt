package com.qlty.intellij

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.qlty.intellij.cli.QltyRunner
import com.qlty.intellij.model.Issue
import com.qlty.intellij.settings.QltySettings
import com.qlty.intellij.util.QltyProjectDetector
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
