package com.qlty.intellij

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.qlty.intellij.cli.QltyRunner
import com.qlty.intellij.fixes.QltyFixRuleInFileAction
import com.qlty.intellij.model.Issue
import com.qlty.intellij.util.QltyProjectDetector
import java.io.File

class QltyFixRuleInFileActionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        QltyProjectDetector.clearCache()
    }

    fun testInvokeAppliesMatchingReplacementsToFile() {
        createQltyConfig()
        val psiFile = createProjectFile("src/demo.txt", "hello world\n")
        val action = QltyFixRuleInFileAction(
            tool = "eslint",
            ruleKey = "demo-rule",
            runnerFactory = {
                FakeRunner(
                    checkFileWithFilterOutput = issueJson(
                        relativePath = "src/demo.txt",
                        tool = "eslint",
                        ruleKey = "demo-rule",
                    ),
                )
            },
            backgroundExecutor = { task -> task() },
            uiExecutor = { task -> task() },
        )

        action.invoke(project, myFixture.editor, psiFile)

        assertEquals("hello there\n", myFixture.editor.document.text)
    }

    fun testInvokeSkipsApplyingReplacementsWhenDocumentChangesBeforeUiPhase() {
        createQltyConfig()
        val psiFile = createProjectFile("src/demo.txt", "hello world\n")
        var queuedUiTask: (() -> Unit)? = null
        val action = QltyFixRuleInFileAction(
            tool = "eslint",
            ruleKey = "demo-rule",
            runnerFactory = {
                FakeRunner(
                    checkFileWithFilterOutput = issueJson(
                        relativePath = "src/demo.txt",
                        tool = "eslint",
                        ruleKey = "demo-rule",
                    ),
                )
            },
            backgroundExecutor = { task -> task() },
            uiExecutor = { task -> queuedUiTask = task },
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

    private fun issueJson(
        relativePath: String,
        tool: String,
        ruleKey: String,
    ): String =
        """
        [
          {
            "tool": "$tool",
            "ruleKey": "$ruleKey",
            "message": "Replace world",
            "location": {
              "path": "$relativePath",
              "range": {
                "startLine": 1,
                "startColumn": 7,
                "endLine": 1,
                "endColumn": 12
              }
            },
            "suggestions": [
              {
                "description": "Replace world",
                "replacements": [
                  {
                    "data": "there",
                    "location": {
                      "path": "$relativePath",
                      "range": {
                        "startLine": 1,
                        "startColumn": 7,
                        "endLine": 1,
                        "endColumn": 12
                      }
                    }
                  }
                ]
              }
            ]
          }
        ]
        """.trimIndent()
}

internal class FakeRunner(
    private val issues: List<Issue> = emptyList(),
    private val checkProjectOutput: String? = null,
    private val checkFileWithFilterOutput: String? = null,
) : QltyRunner {
    override fun analyzeFile(filePath: String, workDir: String): List<Issue> = issues

    override fun checkProject(workDir: String): String? = checkProjectOutput

    override fun fixFile(filePath: String, workDir: String) {}

    override fun checkFileWithFilter(
        filePath: String,
        workDir: String,
        tool: String,
    ): String? = checkFileWithFilterOutput

    override fun fixProjectWithFilter(
        workDir: String,
        tool: String,
        ruleKey: String,
    ) {}

    override fun formatFile(
        filePath: String,
        workDir: String,
    ) {}
}
