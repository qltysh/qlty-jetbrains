package com.qlty.intellij.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.qlty.intellij.cli.QltyCliRunner
import com.qlty.intellij.cli.QltyRunner
import com.qlty.intellij.util.QltyProjectDetector

class QltyFixFileAction(
    private val runnerFactory: (Project) -> QltyRunner = ::QltyCliRunner,
    private val backgroundExecutor: ((() -> Unit) -> Unit) = { task ->
        ApplicationManager.getApplication().executeOnPooledThread(task)
    },
    private val uiExecutor: ((() -> Unit) -> Unit) = { task ->
        ApplicationManager.getApplication().invokeLater(task)
    },
    private val refreshedContentLoader: (String) -> String? = { filePath ->
        val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(filePath)
        if (vFile == null) {
            null
        } else {
            vFile.refresh(false, false)
            String(vFile.contentsToByteArray(), vFile.charset)
        }
    },
    private val documentSaver: (com.intellij.openapi.editor.Document) -> Unit = { document ->
        FileDocumentManager.getInstance().saveDocument(document)
    },
) : IntentionAction {
    override fun getText(): String = "Fix all Qlty issues in file"

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

        FileDocumentManager.getInstance().saveAllDocuments()
        val modStamp = document.modificationStamp

        backgroundExecutor {
            val runner = runnerFactory(project)
            runner.fixFile(vFile.path, qltyRoot)

            uiExecutor {
                if (document.modificationStamp != modStamp) return@uiExecutor

                val newContent = refreshedContentLoader(vFile.path) ?: return@uiExecutor
                if (newContent != document.text) {
                    WriteCommandAction.runWriteCommandAction(project, "Qlty Fix File", "qlty", {
                        document.setText(newContent)
                    })
                    documentSaver(document)
                }

                com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project).restart(file)
            }
        }
    }

    override fun startInWriteAction(): Boolean = false
}
