package com.qlty.intellij.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.qlty.intellij.cli.QltyCliRunner
import com.qlty.intellij.settings.QltySettings
import com.qlty.intellij.util.QltyProjectDetector

private data class FmtTarget(
    val document: Document,
    val filePath: String,
    val qltyRoot: String,
    val project: Project,
    val modificationStamp: Long,
)

class QltyFmtOnSaveListener : FileDocumentManagerListener {
    private val logger = Logger.getInstance(QltyFmtOnSaveListener::class.java)
    private val formatting = ThreadLocal.withInitial { false }

    override fun beforeAllDocumentsSaving() {
        if (formatting.get()) return

        val fdm = FileDocumentManager.getInstance()
        val unsavedDocuments = fdm.unsavedDocuments
        if (unsavedDocuments.isEmpty()) return

        val targets = mutableListOf<FmtTarget>()

        for (document in unsavedDocuments) {
            val vFile = fdm.getFile(document) ?: continue

            val project = ProjectManager.getInstance().openProjects.firstOrNull { proj ->
                if (proj.isDisposed) return@firstOrNull false
                val baseDir = proj.basePath?.let {
                    com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it)
                } ?: return@firstOrNull false
                com.intellij.openapi.vfs.VfsUtilCore.isAncestor(baseDir, vFile, false)
            } ?: continue

            val settings = QltySettings.getInstance(project)
            if (!settings.enabled || !settings.fmtOnSave) continue

            val qltyRoot = QltyProjectDetector.findQltyRoot(vFile, project) ?: continue

            targets.add(FmtTarget(document, vFile.path, qltyRoot, project, document.modificationStamp))
        }

        if (targets.isEmpty()) return

        // Run formatting on a pooled thread after save completes
        ApplicationManager.getApplication().executeOnPooledThread {
            for (target in targets) {
                logger.info("Running qlty fmt on save: ${target.filePath}")
                val runner = QltyCliRunner(target.project)
                runner.formatFile(target.filePath, target.qltyRoot)

                // Reload formatted content back into the editor on the EDT
                ApplicationManager.getApplication().invokeLater {
                    if (target.document.modificationStamp != target.modificationStamp) {
                        logger.info("Skipping qlty fmt reload for ${target.filePath}: document modified since save")
                        return@invokeLater
                    }

                    val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                        .refreshAndFindFileByPath(target.filePath) ?: return@invokeLater

                    vFile.refresh(false, false)
                    val newContent = String(vFile.contentsToByteArray(), vFile.charset)
                    if (newContent != target.document.text) {
                        logger.info("Qlty fmt changed file, reloading: ${target.filePath}")
                        try {
                            formatting.set(true)
                            WriteCommandAction.runWriteCommandAction(target.project, "Qlty Format", "qlty", {
                                target.document.setText(newContent)
                            })
                            FileDocumentManager.getInstance().saveDocument(target.document)
                        } finally {
                            formatting.set(false)
                        }
                    }
                }
            }
        }
    }
}
