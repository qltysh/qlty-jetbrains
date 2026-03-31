package com.qlty.intellij

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.qlty.Values
import com.qlty.intellij.cli.QltyCliRunner
import com.qlty.intellij.fixes.QltyFixFileAction
import com.qlty.intellij.fixes.QltyQuickFix
import com.qlty.intellij.model.Issue
import com.qlty.intellij.settings.QltySettings
import com.qlty.intellij.ui.QltyStatusBarWidget
import com.qlty.intellij.util.QltyProjectDetector
import com.qlty.intellij.util.SeverityMapper

private data class AdjustedRange(
    val startLine: Int,
    val endLine: Int,
    val startCol: Int,
    val endCol: Int,
)

data class QltyInput(
    val filePath: String,
    val projectRoot: String,
    val project: com.intellij.openapi.project.Project,
)

data class QltyResult(
    val issues: List<Issue>,
)

class QltyExternalAnnotator : ExternalAnnotator<QltyInput, QltyResult>() {
    private val logger = Logger.getInstance(QltyExternalAnnotator::class.java)

    override fun getPairedBatchInspectionShortName(): String = Values.INSPECTION_SHORT_NAME

    override fun collectInformation(
        file: PsiFile,
        editor: Editor,
        hasErrors: Boolean,
    ): QltyInput? {
        val virtualFile = file.virtualFile ?: return null
        val project = file.project

        val settings = QltySettings.getInstance(project)
        if (!settings.enabled) {
            logger.debug("Qlty disabled in settings, skipping: ${virtualFile.path}")
            QltyStatusBarWidget.getInstance(project)?.updateState(QltyStatusBarWidget.State.DISABLED)
            return null
        }

        val qltyRoot = QltyProjectDetector.findQltyRoot(virtualFile, project)
        if (qltyRoot == null) {
            logger.debug("No .qlty/qlty.toml found for: ${virtualFile.path}")
            QltyStatusBarWidget.getInstance(project)?.updateState(QltyStatusBarWidget.State.NO_CONFIG)
            return null
        }

        logger.debug("Collecting info for ${virtualFile.path} (root: $qltyRoot)")
        return QltyInput(
            filePath = virtualFile.path,
            projectRoot = qltyRoot,
            project = project,
        )
    }

    override fun doAnnotate(input: QltyInput?): QltyResult? {
        input ?: return null

        val widget = QltyStatusBarWidget.getInstance(input.project)
        widget?.updateState(QltyStatusBarWidget.State.ANALYZING)

        logger.info("Starting Qlty analysis: ${input.filePath}")
        return try {
            val runner = QltyCliRunner(input.project)
            val issues = runner.analyzeFile(input.filePath, input.projectRoot)
            widget?.updateState(QltyStatusBarWidget.State.READY)
            logger.info("Qlty analysis complete: ${issues.size} issues found")
            QltyResult(issues)
        } catch (e: Exception) {
            logger.warn("Qlty analysis failed for ${input.filePath}", e)
            widget?.updateState(QltyStatusBarWidget.State.ERROR)
            null
        }
    }

    override fun apply(
        file: PsiFile,
        result: QltyResult?,
        holder: AnnotationHolder,
    ) {
        result ?: return
        val document =
            PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return

        for (issue in result.issues) {
            val (startLine, endLine, startCol, endCol) = adjustRangeForSmells(issue, document)

            if (startLine >= document.lineCount) continue

            val lineStartOffset = document.getLineStartOffset(startLine)
            val startOffset = lineStartOffset + startCol

            val endOffset = if (endLine < document.lineCount) {
                if (endCol > 0) {
                    document.getLineStartOffset(endLine) + endCol
                } else {
                    document.getLineEndOffset(endLine)
                }
            } else {
                document.getLineEndOffset(minOf(endLine, document.lineCount - 1))
            }

            val clampedStart = minOf(startOffset, document.textLength)
            val clampedEnd = minOf(maxOf(endOffset, clampedStart), document.textLength)

            if (clampedStart == clampedEnd && clampedStart == document.textLength) continue

            val severity = SeverityMapper.mapSeverity(issue)
            val prefix = SeverityMapper.categoryPrefix(issue)
            val message = "$prefix${issue.message}"
            val toolAndRule = "${issue.tool}:${issue.ruleKey}"

            var builder =
                holder
                    .newAnnotation(severity, message)
                    .range(com.intellij.openapi.util.TextRange(clampedStart, clampedEnd))
                    .tooltip("[$toolAndRule] $message")

            for (suggestion in issue.suggestions) {
                if (suggestion.replacements.isNotEmpty()) {
                    builder = builder.withFix(QltyQuickFix(toolAndRule, suggestion))
                }
            }

            builder = builder.withFix(QltyFixFileAction())

            builder.create()
        }
    }

    private fun adjustRangeForSmells(
        issue: Issue,
        document: Document,
    ): AdjustedRange {
        val range = issue.location.range
        val startLine = maxOf(range.startLine - 1, 0)

        if (issue.tool != "qlty") {
            return AdjustedRange(
                startLine = startLine,
                endLine = maxOf(range.endLine - 1, startLine),
                startCol = maxOf(range.startColumn - 1, 0),
                endCol = if (range.endColumn > 0) range.endColumn - 1 else 0,
            )
        }

        when (issue.ruleKey) {
            "file-complexity" -> {
                return AdjustedRange(
                    startLine = startLine,
                    endLine = startLine,
                    startCol = 0,
                    endCol = 0,
                )
            }

            "nested-control-flow" -> {
                val snippet = issue.snippet
                val keyword = snippet.trimStart().split("\\s+".toRegex()).firstOrNull() ?: ""
                return AdjustedRange(
                    startLine = startLine,
                    endLine = startLine,
                    startCol = 0,
                    endCol = if (keyword.isNotEmpty()) keyword.length else 0,
                )
            }

            "function-complexity", "return-statements" -> {
                val funcName = issue.message.split(" ").lastOrNull() ?: ""
                val snippet = issue.snippet
                val funcIndex = if (funcName.isNotEmpty()) snippet.indexOf(funcName) else -1
                return if (funcIndex >= 0) {
                    AdjustedRange(
                        startLine = startLine,
                        endLine = startLine,
                        startCol = funcIndex,
                        endCol = funcIndex + funcName.length,
                    )
                } else {
                    AdjustedRange(
                        startLine = startLine,
                        endLine = startLine,
                        startCol = 0,
                        endCol = 0,
                    )
                }
            }

            else -> {
                return AdjustedRange(
                    startLine = startLine,
                    endLine = maxOf(range.endLine - 1, startLine),
                    startCol = maxOf(range.startColumn - 1, 0),
                    endCol = if (range.endColumn > 0) range.endColumn - 1 else 0,
                )
            }
        }
    }
}
