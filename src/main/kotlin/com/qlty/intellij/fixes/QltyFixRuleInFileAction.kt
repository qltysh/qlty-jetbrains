package com.qlty.intellij.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.qlty.intellij.cli.QltyCliRunner
import com.qlty.intellij.cli.QltyJsonParser
import com.qlty.intellij.model.Replacement
import com.qlty.intellij.util.QltyProjectDetector
import java.io.File

class QltyFixRuleInFileAction(
    private val tool: String,
    private val ruleKey: String,
) : IntentionAction {
    override fun getText(): String = "Fix all $tool:$ruleKey issues in file"

    override fun getFamilyName(): String = "Qlty fixes"

    override fun isAvailable(
        project: Project,
        editor: Editor?,
        file: PsiFile?,
    ): Boolean {
        val vFile = file?.virtualFile ?: return false
        return QltyProjectDetector.findQltyRoot(vFile, project) != null
    }

    override fun invoke(
        project: Project,
        editor: Editor?,
        file: PsiFile?,
    ) {
        file ?: return
        val vFile = file.virtualFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val qltyRoot = QltyProjectDetector.findQltyRoot(vFile, project) ?: return

        FileDocumentManager.getInstance().saveDocument(document)

        val runner = QltyCliRunner(project)
        val output = runner.checkFileWithFilter(vFile.path, qltyRoot, tool) ?: return

        val issues = QltyJsonParser.parseIssues(output)
        val relativePath = File(vFile.path).relativeTo(File(qltyRoot)).path
        val matchingIssues = issues.filter {
            it.location.path == relativePath &&
                it.tool == tool &&
                it.ruleKey == ruleKey &&
                it.suggestions.isNotEmpty()
        }

        val allReplacements = mutableListOf<Replacement>()
        for (issue in matchingIssues) {
            for (suggestion in issue.suggestions) {
                allReplacements.addAll(suggestion.replacements)
            }
        }

        if (allReplacements.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project, "Qlty Fix $tool:$ruleKey", "qlty", {
            val lineCount = document.lineCount
            val sortedReplacements = allReplacements
                .filter { r ->
                    val startLine = maxOf(r.location.range.startLine - 1, 0)
                    val endLine = maxOf(r.location.range.endLine - 1, 0)
                    startLine < lineCount && endLine < lineCount
                }
                .sortedByDescending { r ->
                    val range = r.location.range
                    document.getLineStartOffset(maxOf(range.startLine - 1, 0)) + range.startColumn
                }

            for (replacement in sortedReplacements) {
                val range = replacement.location.range
                val startLine = maxOf(range.startLine - 1, 0)
                val endLine = maxOf(range.endLine - 1, 0)

                val startOffset = document.getLineStartOffset(startLine) + maxOf(range.startColumn - 1, 0)
                val endOffset = if (range.endColumn > 0) {
                    document.getLineStartOffset(endLine) + maxOf(range.endColumn - 1, 0)
                } else {
                    document.getLineEndOffset(endLine)
                }

                document.replaceString(
                    minOf(startOffset, document.textLength),
                    minOf(endOffset, document.textLength),
                    replacement.data,
                )
            }
        })

        com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project).restart(file)
    }

    override fun startInWriteAction(): Boolean = false
}
