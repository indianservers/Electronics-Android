package com.indianservers.circuitssimulator

import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CanvasIntelligenceInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun liveCanvasCanLocateReviewAndClearARepairedIssue() {
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        val r=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=180f,y=200f)
        val g=PlacedComponent(kind=Kind.GROUND,reference="GND",x=350f,y=200f)
        val bypass=Wire(start=TerminalRef(r.id,0),end=TerminalRef(r.id,1))
        val ground=Wire(start=TerminalRef(r.id,0),end=TerminalRef(g.id,0))
        rule.runOnUiThread {
            model.dismissRecovery();model.setLiveWarnings(true)
            model.loadSample(Circuit("Canvas intelligence",listOf(r,g),listOf(bypass,ground)))
            model.checkCircuit()
            rule.activity.setContent { SimulatorScreen(model) }
        }
        rule.waitUntil(10000) { model.state.value.intelligence.issues.any { it.ruleId=="BYPASSED_PASSIVE" } }
        rule.onNodeWithTag("canvas-intelligence").assertExists()
        val screenshot=rule.onRoot().captureToImage()
        java.io.File(rule.activity.getExternalFilesDir(null),"canvas-intelligence.png").outputStream().use {
            screenshot.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        rule.onNodeWithTag("canvas-locate").performClick()
        assertNotNull(model.state.value.highlightedIssueId)
        assertTrue(model.state.value.highlightedParts.contains(r.id))
        rule.onNodeWithTag("canvas-review").performClick()
        assertTrue(model.state.value.diagnosticsOpen)
        rule.runOnUiThread {
            model.showDiagnostics(false)
            model.focusIssue(model.state.value.intelligence.issues.first { it.ruleId=="BYPASSED_PASSIVE" })
            model.disconnectPin(TerminalRef(r.id,1))
            model.checkCircuit()
        }
        rule.waitUntil(10000) { model.state.value.intelligence.issues.none { it.ruleId=="BYPASSED_PASSIVE" } }
        assertNull(model.state.value.highlightedIssueId)
        assertTrue(model.state.value.issueHistory.any { it.issue.ruleId=="BYPASSED_PASSIVE" && it.resolved })
        rule.onNodeWithTag("canvas-intelligence").assertExists()
    }
}
