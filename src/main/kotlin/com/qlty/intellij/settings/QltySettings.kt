package com.qlty.intellij.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "QltySettings",
    storages = [Storage("qlty.xml")],
)
class QltySettings : PersistentStateComponent<QltySettings.State> {
    data class State(
        var enabled: Boolean = true,
        var analyzeOnSave: Boolean = true,
        var fmtOnSave: Boolean = false,
    )

    private var state = State()

    var enabled: Boolean
        get() = state.enabled
        set(value) {
            state.enabled = value
        }

    var analyzeOnSave: Boolean
        get() = state.analyzeOnSave
        set(value) {
            state.analyzeOnSave = value
        }

    var fmtOnSave: Boolean
        get() = state.fmtOnSave
        set(value) {
            state.fmtOnSave = value
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(project: Project): QltySettings = project.getService(QltySettings::class.java)
    }
}
