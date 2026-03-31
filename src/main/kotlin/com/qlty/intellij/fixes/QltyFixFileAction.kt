package com.qlty.intellij.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.qlty.intellij.cli.QltyCliRunner
import com.qlty.intellij.util.QltyProjectDetector

class QltyFixFileAction : IntentionAction {

    override fun getText(): String = "Fix all Qlty issues in file"

    override fun getFamilyName(): String = "Qlty fixes"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val vFile = file?.virtualFile ?: return false
        return QltyProjectDetector.findQltyRoot(vFile, project) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file ?: return
        val vFile = file.virtualFile ?: return
        val qltyRoot = QltyProjectDetector.findQltyRoot(vFile, project) ?: return

        FileDocumentManager.getInstance().saveAllDocuments()

        val runner = QltyCliRunner(project)
        runner.fixFile(vFile.path, qltyRoot)

        VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
        vFile.refresh(false, false)

        com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project).restart(file)
    }

    override fun startInWriteAction(): Boolean = false
}
