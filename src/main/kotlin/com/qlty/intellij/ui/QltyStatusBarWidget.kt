package com.qlty.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

class QltyStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null
    @Volatile var state: State = State.READY
        private set

    enum class State(val display: String, val tooltip: String) {
        READY("Qlty", "Qlty: Ready"),
        ANALYZING("Qlty: Analyzing...", "Qlty: Running analysis on current file"),
        ERROR("Qlty: Error", "Qlty: Analysis failed — check IDE log for details"),
        DISABLED("Qlty: Disabled", "Qlty: Plugin is disabled in settings"),
        NO_CONFIG("Qlty: No Config", "Qlty: No .qlty/qlty.toml found in project"),
    }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getText(): String = state.display

    override fun getTooltipText(): String = state.tooltip

    override fun getAlignment(): Float = java.awt.Component.CENTER_ALIGNMENT

    override fun getClickConsumer(): Consumer<MouseEvent>? = null

    fun updateState(newState: State) {
        state = newState
        statusBar?.updateWidget(ID)
    }

    companion object {
        const val ID = "QltyStatusBar"

        fun getInstance(project: Project): QltyStatusBarWidget? {
            val statusBar = com.intellij.openapi.wm.WindowManager.getInstance().getStatusBar(project)
            return statusBar?.getWidget(ID) as? QltyStatusBarWidget
        }
    }
}
