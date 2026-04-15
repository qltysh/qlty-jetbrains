package com.qlty.intellij

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.qlty.intellij.cli.QltyRunner
import com.qlty.intellij.fixes.QltyFixRuleInProjectAction
import com.qlty.intellij.model.Issue
import com.qlty.intellij.util.QltyProjectDetector
import java.io.File

class QltyFixRuleInProjectActionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        QltyProjectDetector.clearCache()
    }

    fun testGetTextIncludesToolAndRuleKey() {
        val action = QltyFixRuleInProjectAction(tool = "eslint", ruleKey = "no-unused-vars")
        assertEquals("Fix all eslint:no-unused-vars issues in project", action.text)
    }

    fun testIsAvailableReturnsTrueWhenQltyConfigExists() {
        createQltyConfig()
        val psiFile = createProjectFile("src/demo.kt", "fun demo() = Unit\n")
        val action = QltyFixRuleInProjectAction(tool = "eslint", ruleKey = "no-unused-vars")

        assertTrue(action.isAvailable(project, myFixture.editor, psiFile))
    }

    fun testIsAvailableReturnsFalseWhenNoQltyConfig() {
        val psiFile = createProjectFile("src/demo.kt", "fun demo() = Unit\n")
        val action = QltyFixRuleInProjectAction(tool = "eslint", ruleKey = "no-unused-vars")

        assertFalse(action.isAvailable(project, myFixture.editor, psiFile))
    }

    fun testInvokeCallsFixProjectWithFilterAndRefreshes() {
        createQltyConfig()
        val psiFile = createProjectFile("src/demo.kt", "fun demo() = Unit\n")
        val runner = RecordingProjectFixRunner()
        val action = QltyFixRuleInProjectAction(
            tool = "eslint",
            ruleKey = "no-unused-vars",
            runnerFactory = { runner },
            backgroundExecutor = { task -> task() },
            uiExecutor = { task -> task() },
        )

        action.invoke(project, myFixture.editor, psiFile)

        assertEquals(1, runner.fixProjectCalls.size)
        val (workDir, tool, ruleKey) = runner.fixProjectCalls[0]
        assertEquals(requireNotNull(project.basePath), workDir)
        assertEquals("eslint", tool)
        assertEquals("no-unused-vars", ruleKey)
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

private class RecordingProjectFixRunner : QltyRunner {
    data class FixProjectCall(val workDir: String, val tool: String, val ruleKey: String)
    val fixProjectCalls = mutableListOf<FixProjectCall>()

    override fun analyzeFile(filePath: String, workDir: String): List<Issue> = emptyList()
    override fun checkProject(workDir: String): String? = null
    override fun fixFile(filePath: String, workDir: String) {}
    override fun checkFileWithFilter(filePath: String, workDir: String, tool: String): String? = null
    override fun fixProjectWithFilter(workDir: String, tool: String, ruleKey: String) {
        fixProjectCalls += FixProjectCall(workDir, tool, ruleKey)
    }
    override fun formatFile(filePath: String, workDir: String) {}
}
