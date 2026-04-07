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
import com.qlty.intellij.cli.QltyRunner
import com.qlty.intellij.settings.QltySettings
import com.qlty.intellij.util.QltyProjectDetector

private data class FmtTarget(
    val document: Document,
    val filePath: String,
    val qltyRoot: String,
    val project: Project,
    val modificationStamp: Long,
)

class QltyFmtOnSaveListener(
    private val runnerFactory: (Project) -> QltyRunner = ::QltyCliRunner,
    private val backgroundExecutor: ((() -> Unit) -> Unit) = { task ->
        ApplicationManager.getApplication().executeOnPooledThread(task)
    },
    private val uiExecutor: ((() -> Unit) -> Unit) = { task ->
        ApplicationManager.getApplication().invokeLater(task)
    },
    private val projectResolver: (Document, FileDocumentManager) -> Project? = { document, fdm ->
        val vFile = fdm.getFile(document)
        if (vFile == null) {
            null
        } else {
            ProjectManager.getInstance().openProjects.firstOrNull { proj ->
                if (proj.isDisposed) return@firstOrNull false
                val baseDir = proj.basePath?.let {
                    com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it)
                } ?: return@firstOrNull false
                com.intellij.openapi.vfs.VfsUtilCore.isAncestor(baseDir, vFile, false)
            }
        }
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
    private val documentSaver: (Document) -> Unit = { document ->
        FileDocumentManager.getInstance().saveDocument(document)
    },
) : FileDocumentManagerListener {
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
            val project = projectResolver(document, fdm) ?: continue

            val settings = QltySettings.getInstance(project)
            if (!settings.enabled || !settings.fmtOnSave) continue

            val qltyRoot = QltyProjectDetector.findQltyRoot(vFile, project) ?: continue

            targets.add(FmtTarget(document, vFile.path, qltyRoot, project, document.modificationStamp))
        }

        if (targets.isEmpty()) return

        // Run formatting on a pooled thread after save completes
        backgroundExecutor {
            for (target in targets) {
                logger.info("Running qlty fmt on save: ${target.filePath}")
                val runner = runnerFactory(target.project)
                runner.formatFile(target.filePath, target.qltyRoot)

                // Reload formatted content back into the editor on the EDT
                uiExecutor {
                    if (target.document.modificationStamp != target.modificationStamp) {
                        logger.info("Skipping qlty fmt reload for ${target.filePath}: document modified since save")
                        return@uiExecutor
                    }

                    val newContent = refreshedContentLoader(target.filePath) ?: return@uiExecutor
                    if (newContent != target.document.text) {
                        logger.info("Qlty fmt changed file, reloading: ${target.filePath}")
                        try {
                            formatting.set(true)
                            WriteCommandAction.runWriteCommandAction(target.project, "Qlty Format", "qlty", {
                                target.document.setText(newContent)
                            })
                            documentSaver(target.document)
                        } finally {
                            formatting.set(false)
                        }
                    }
                }
            }
        }
    }
}
