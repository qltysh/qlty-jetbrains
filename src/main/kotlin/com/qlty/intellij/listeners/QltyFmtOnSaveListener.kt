package com.qlty.intellij.listeners

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.qlty.intellij.cli.QltyCliRunner
import com.qlty.intellij.settings.QltySettings
import com.qlty.intellij.util.QltyProjectDetector

class QltyFmtOnSaveListener : FileDocumentManagerListener {
    private val logger = Logger.getInstance(QltyFmtOnSaveListener::class.java)

    override fun beforeDocumentSaving(document: Document) {
        val vFile = FileDocumentManager.getInstance().getFile(document) ?: return

        val project = ProjectManager.getInstance().openProjects.firstOrNull { proj ->
            !proj.isDisposed && vFile.path.startsWith(proj.basePath ?: "")
        } ?: return

        val settings = QltySettings.getInstance(project)
        if (!settings.enabled || !settings.fmtOnSave) return

        val qltyRoot = QltyProjectDetector.findQltyRoot(vFile, project) ?: return

        logger.info("Running qlty fmt on save: ${vFile.path}")
        val runner = QltyCliRunner(project)
        runner.formatFile(vFile.path, qltyRoot)

        vFile.refresh(false, false)
    }
}
