package com.qlty.intellij

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.qlty.intellij.cli.QltyRunner
import com.qlty.intellij.model.Issue
import com.qlty.intellij.listeners.QltyFmtOnSaveListener
import com.qlty.intellij.settings.QltySettings
import com.qlty.intellij.util.QltyProjectDetector
import java.io.File

class QltyFmtOnSaveListenerTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        QltyProjectDetector.clearCache()
        QltySettings.getInstance(project).enabled = true
        QltySettings.getInstance(project).fmtOnSave = true
    }

    fun testFormatsAndReloadsDocumentOnSave() {
        createQltyConfig()
        createProjectFile("src/demo.txt", "hello world\n")
        val runner = RecordingRunner()
        val listener = QltyFmtOnSaveListener(
            runnerFactory = { runner },
            backgroundExecutor = { task -> task() },
            uiExecutor = { task -> task() },
            projectResolver = { _, _ -> project },
            refreshedContentLoader = { "hello there\n" },
            documentSaver = {},
        )

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText("hello world\n")
        }

        listener.beforeAllDocumentsSaving()

        assertEquals(listOf(Pair(myFixture.file.virtualFile.path, requireNotNull(project.basePath))), runner.formattedFiles)
        assertEquals("hello there\n", myFixture.editor.document.text)
    }

    fun testDoesNotOverwriteDocumentChangedAfterSaveStarted() {
        createQltyConfig()
        createProjectFile("src/demo.txt", "hello world\n")
        val runner = RecordingRunner()
        var queuedUiTask: (() -> Unit)? = null
        val listener = QltyFmtOnSaveListener(
            runnerFactory = { runner },
            backgroundExecutor = { task -> task() },
            uiExecutor = { task -> queuedUiTask = task },
            projectResolver = { _, _ -> project },
            refreshedContentLoader = { "hello there\n" },
            documentSaver = {},
        )

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText("hello world\n")
        }

        listener.beforeAllDocumentsSaving()
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
    ) {
        val projectRoot = requireNotNull(project.basePath)
        val ioFile = File(projectRoot, relativePath).apply {
            parentFile.mkdirs()
            writeText(contents)
        }
        val virtualFile = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile))
        myFixture.openFileInEditor(virtualFile)
        requireNotNull(PsiManager.getInstance(project).findFile(virtualFile))
        FileDocumentManager.getInstance().reloadFromDisk(myFixture.editor.document)
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText(contents)
        }
    }
}

private class RecordingRunner : QltyRunner {
    val formattedFiles = mutableListOf<Pair<String, String>>()

    override fun analyzeFile(filePath: String, workDir: String): List<Issue> = emptyList()

    override fun checkProject(workDir: String): String? = null

    override fun fixFile(filePath: String, workDir: String) {}

    override fun checkFileWithFilter(filePath: String, workDir: String, tool: String): String? = null

    override fun fixProjectWithFilter(workDir: String, tool: String, ruleKey: String) {}

    override fun formatFile(
        filePath: String,
        workDir: String,
    ) {
        formattedFiles += filePath to workDir
    }
}
