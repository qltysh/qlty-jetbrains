package com.qlty.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.qlty.intellij.ui.QltyStatusBarWidget

class QltyStatusBarWidgetTest : BasePlatformTestCase() {
    fun testInitialStateIsReady() {
        val widget = QltyStatusBarWidget(project)
        assertEquals(QltyStatusBarWidget.State.READY, widget.state)
        assertEquals("Qlty", widget.getText())
        assertEquals("Qlty: Ready", widget.getTooltipText())
    }

    fun testUpdateStateChangesStateAndText() {
        val widget = QltyStatusBarWidget(project)

        widget.updateState(QltyStatusBarWidget.State.ANALYZING)
        assertEquals(QltyStatusBarWidget.State.ANALYZING, widget.state)
        assertEquals("Qlty: Analyzing...", widget.getText())
        assertEquals("Qlty: Running analysis on current file", widget.getTooltipText())

        widget.updateState(QltyStatusBarWidget.State.ERROR)
        assertEquals(QltyStatusBarWidget.State.ERROR, widget.state)
        assertEquals("Qlty: Error", widget.getText())

        widget.updateState(QltyStatusBarWidget.State.DISABLED)
        assertEquals(QltyStatusBarWidget.State.DISABLED, widget.state)
        assertEquals("Qlty: Disabled", widget.getText())

        widget.updateState(QltyStatusBarWidget.State.NO_CONFIG)
        assertEquals(QltyStatusBarWidget.State.NO_CONFIG, widget.state)
        assertEquals("Qlty: No Config", widget.getText())

        widget.updateState(QltyStatusBarWidget.State.READY)
        assertEquals(QltyStatusBarWidget.State.READY, widget.state)
        assertEquals("Qlty", widget.getText())
    }

    fun testWidgetIdIsQltyStatusBar() {
        val widget = QltyStatusBarWidget(project)
        assertEquals("QltyStatusBar", widget.ID())
    }
}
