package com.qlty.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class QltyStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = QltyStatusBarWidget.ID

    override fun getDisplayName(): String = "Qlty"

    override fun isAvailable(project: Project): Boolean = true

    override fun isEnabledByDefault(): Boolean = true

    override fun isConfigurable(): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = QltyStatusBarWidget(project)

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
