package com.qlty.intellij

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.qlty.intellij.cli.QltyRunner
import com.qlty.intellij.fixes.QltyFixFileAction
import com.qlty.intellij.settings.QltySettings
import com.qlty.intellij.util.QltyProjectDetector
import java.io.File

class QltyFixFileActionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        QltyProjectDetector.clearCache()
        QltySettings.getInstance(project).enabled = true
    }

    fun testInvokeReloadsFixedFileContentsIntoEditor() {
        createQltyConfig()
        val psiFile = createProjectFile("src/demo.txt", "hello world\n")
        val runner = RecordingFixFileRunner()
        val action = QltyFixFileAction(
            runnerFactory = { runner },
            backgroundExecutor = { task -> task() },
            uiExecutor = { task -> task() },
            refreshedContentLoader = { "hello there\n" },
            documentSaver = {},
        )

        action.invoke(project, myFixture.editor, psiFile)

        assertEquals(listOf(Pair(myFixture.file.virtualFile.path, requireNotNull(project.basePath))), runner.fixedFiles)
        assertEquals("hello there\n", myFixture.editor.document.text)
    }

    fun testInvokeDoesNotOverwriteDocumentChangedBeforeUiPhase() {
        createQltyConfig()
        val psiFile = createProjectFile("src/demo.txt", "hello world\n")
        val runner = RecordingFixFileRunner()
        var queuedUiTask: (() -> Unit)? = null
        val action = QltyFixFileAction(
            runnerFactory = { runner },
            backgroundExecutor = { task -> task() },
            uiExecutor = { task -> queuedUiTask = task },
            refreshedContentLoader = { "hello there\n" },
            documentSaver = {},
        )

        action.invoke(project, myFixture.editor, psiFile)
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(0, "X")
        }
        requireNotNull(queuedUiTask).invoke()

        assertEquals("Xhello world\n", myFixture.editor.document.text)
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

private class RecordingFixFileRunner : QltyRunner {
    val fixedFiles = mutableListOf<Pair<String, String>>()

    override fun analyzeFile(filePath: String, workDir: String) = emptyList<com.qlty.intellij.model.Issue>()

    override fun checkProject(workDir: String): String? = null

    override fun fixFile(
        filePath: String,
        workDir: String,
    ) {
        fixedFiles += filePath to workDir
    }

    override fun checkFileWithFilter(filePath: String, workDir: String, tool: String): String? = null

    override fun fixProjectWithFilter(workDir: String, tool: String, ruleKey: String) {}

    override fun formatFile(filePath: String, workDir: String) {}
}
