package com.qlty.intellij.actions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiManager
import com.qlty.intellij.cli.QltyCliRunner
import com.qlty.intellij.cli.QltyJsonParser
import com.qlty.intellij.settings.QltySettings
import com.qlty.intellij.util.QltyProjectDetector

class AnalyzeProjectAction : AnAction() {
    private val logger = Logger.getInstance(AnalyzeProjectAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = QltySettings.getInstance(project)
        if (!settings.enabled) return

        ApplicationManager.getApplication().executeOnPooledThread {
            val basePath = project.basePath ?: return@executeOnPooledThread
            val runner = QltyCliRunner(project)
            val output = runner.checkProject(basePath)

            val issueCount = if (output != null) {
                QltyJsonParser.parseIssues(output).size
            } else {
                -1
            }

            ApplicationManager.getApplication().invokeLater {
                // Re-analyze all open editors
                DaemonCodeAnalyzer.getInstance(project).restart()

                val message = if (issueCount >= 0) {
                    "Qlty found $issueCount issue${if (issueCount != 1) "s" else ""} across the project."
                } else {
                    "Qlty project analysis failed. Check the IDE log for details."
                }

                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Qlty Notifications")
                    .createNotification(message, NotificationType.INFORMATION)
                    .notify(project)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
