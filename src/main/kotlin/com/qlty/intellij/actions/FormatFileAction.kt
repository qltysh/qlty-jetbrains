package com.qlty.intellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.qlty.intellij.cli.QltyCliRunner
import com.qlty.intellij.util.QltyProjectDetector

class FormatFileAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val document = editor.document
        val vFile = FileDocumentManager.getInstance().getFile(document) ?: return
        val qltyRoot = QltyProjectDetector.findQltyRoot(vFile, project) ?: return

        FileDocumentManager.getInstance().saveDocument(document)

        val runner = QltyCliRunner(project)
        runner.formatFile(vFile.path, qltyRoot)

        vFile.refresh(false, false)

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
        com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.EDITOR) != null
    }
}
