package com.qlty.intellij.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.*

class QltyConfigurable(private val project: Project) : Configurable {
    private var panel: JPanel? = null
    private var binaryPathField: JTextField? = null
    private var enabledCheckbox: JCheckBox? = null
    private var analyzeOnSaveCheckbox: JCheckBox? = null

    override fun getDisplayName(): String = "Qlty"

    override fun createComponent(): JComponent {
        val settings = QltySettings.getInstance(project)

        binaryPathField = JTextField(settings.qltyBinaryPath, 30)
        enabledCheckbox = JCheckBox("Enable Qlty analysis", settings.enabled)
        analyzeOnSaveCheckbox = JCheckBox("Analyze on save", settings.analyzeOnSave)

        panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

            val pathPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JLabel("Qlty binary path: "))
                add(binaryPathField)
            }
            add(pathPanel)
            add(Box.createVerticalStrut(8))
            add(enabledCheckbox!!.apply { alignmentX = JPanel.LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(4))
            add(analyzeOnSaveCheckbox!!.apply { alignmentX = JPanel.LEFT_ALIGNMENT })
        }

        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = QltySettings.getInstance(project)
        return binaryPathField?.text != settings.qltyBinaryPath ||
            enabledCheckbox?.isSelected != settings.enabled ||
            analyzeOnSaveCheckbox?.isSelected != settings.analyzeOnSave
    }

    override fun apply() {
        val settings = QltySettings.getInstance(project)
        settings.qltyBinaryPath = binaryPathField?.text ?: "qlty"
        settings.enabled = enabledCheckbox?.isSelected ?: true
        settings.analyzeOnSave = analyzeOnSaveCheckbox?.isSelected ?: true
    }

    override fun reset() {
        val settings = QltySettings.getInstance(project)
        binaryPathField?.text = settings.qltyBinaryPath
        enabledCheckbox?.isSelected = settings.enabled
        analyzeOnSaveCheckbox?.isSelected = settings.analyzeOnSave
    }
}
