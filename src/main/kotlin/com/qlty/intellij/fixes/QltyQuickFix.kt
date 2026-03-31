package com.qlty.intellij.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.qlty.intellij.model.Suggestion

class QltyQuickFix(
    private val toolAndRule: String,
    private val suggestion: Suggestion,
) : IntentionAction {

    override fun getText(): String {
        val desc = suggestion.description.ifEmpty { "Fix this $toolAndRule issue" }
        return desc
    }

    override fun getFamilyName(): String = "Qlty fixes"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return suggestion.replacements.isNotEmpty()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        WriteCommandAction.runWriteCommandAction(project, "Qlty Fix", "qlty", {
            val lineCount = document.lineCount
            val validReplacements = suggestion.replacements.filter { replacement ->
                val range = replacement.location.range
                val startLine = maxOf(range.startLine - 1, 0)
                val endLine = maxOf(range.endLine - 1, 0)
                startLine < lineCount && endLine < lineCount
            }

            val sortedReplacements = validReplacements.sortedByDescending { replacement ->
                val range = replacement.location.range
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
                    replacement.data
                )
            }
        })
    }

    override fun startInWriteAction(): Boolean = false
}
