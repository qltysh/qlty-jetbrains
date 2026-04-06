package com.qlty.intellij.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.*

class QltyConfigurable(private val project: Project) : Configurable {
    private var panel: JPanel? = null
    private var enabledCheckbox: JCheckBox? = null
    private var analyzeOnSaveCheckbox: JCheckBox? = null
    private var fmtOnSaveCheckbox: JCheckBox? = null

    override fun getDisplayName(): String = "Qlty"

    override fun createComponent(): JComponent {
        val settings = QltySettings.getInstance(project)

        enabledCheckbox = JCheckBox("Enable Qlty analysis", settings.enabled)
        analyzeOnSaveCheckbox = JCheckBox("Analyze on save", settings.analyzeOnSave)
        fmtOnSaveCheckbox = JCheckBox("Format on save (qlty fmt)", settings.fmtOnSave)

        panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

            add(enabledCheckbox!!.apply { alignmentX = JPanel.LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(4))
            add(analyzeOnSaveCheckbox!!.apply { alignmentX = JPanel.LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(4))
            add(fmtOnSaveCheckbox!!.apply { alignmentX = JPanel.LEFT_ALIGNMENT })
        }

        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = QltySettings.getInstance(project)
        return enabledCheckbox?.isSelected != settings.enabled ||
            analyzeOnSaveCheckbox?.isSelected != settings.analyzeOnSave ||
            fmtOnSaveCheckbox?.isSelected != settings.fmtOnSave
    }

    override fun apply() {
        val settings = QltySettings.getInstance(project)
        settings.enabled = enabledCheckbox?.isSelected ?: true
        settings.analyzeOnSave = analyzeOnSaveCheckbox?.isSelected ?: true
        settings.fmtOnSave = fmtOnSaveCheckbox?.isSelected ?: false
    }

    override fun reset() {
        val settings = QltySettings.getInstance(project)
        enabledCheckbox?.isSelected = settings.enabled
        analyzeOnSaveCheckbox?.isSelected = settings.analyzeOnSave
        fmtOnSaveCheckbox?.isSelected = settings.fmtOnSave
    }
}
