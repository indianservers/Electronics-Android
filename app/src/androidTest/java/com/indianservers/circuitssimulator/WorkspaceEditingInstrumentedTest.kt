package com.indianservers.circuitssimulator

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.ui.SimulatorViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkspaceEditingInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]

    @Test fun groupClipboardUndoAndRoutePreserveTopology() {
        val model=model();val sample=SampleCircuits.divider()
        rule.runOnUiThread { model.loadSample(sample);model.toggleMultiSelect();model.selectAllParts();model.copySelection() }
        val original=model.state.value.circuit
        rule.runOnUiThread { model.pasteSelection() }
        assertEquals(original.components.size*2,model.state.value.circuit.components.size)
        assertEquals(original.wires.size*2,model.state.value.circuit.wires.size)
        rule.runOnUiThread { model.undo() }
        assertEquals(original,model.state.value.circuit)
        assertTrue(model.state.value.selection.isEmpty())
        rule.runOnUiThread { model.redo();model.undo() }
        val connections=model.state.value.circuit.electricalConnections()
        rule.runOnUiThread { model.setWireRoute(original.wires.first().id,listOf(WirePoint(80f,120f))) }
        assertEquals(connections,model.state.value.circuit.electricalConnections())
        rule.runOnUiThread { model.undo() }
        assertEquals(original,model.state.value.circuit)
    }

    @Test fun toolbarSelectionAndSnapAreConnectedToModel() {
        val model=model()
        rule.runOnUiThread { model.loadSample(SampleCircuits.divider());model.setGrid(false,32f) }
        rule.onNodeWithText("Circuit Designer").performClick()
        rule.onNodeWithText("Select group (0)").performClick()
        rule.onNodeWithText("All",substring=false).performClick()
        rule.waitUntil(5000) { model.state.value.selection.size==model.state.value.circuit.components.size }
        rule.onNodeWithText("Copy",substring=false).performScrollTo().performClick()
        rule.onNodeWithText("Paste",substring=false).performScrollTo().performClick()
        rule.waitUntil(5000) { model.state.value.circuit.components.size==SampleCircuits.divider().components.size*2 }
        rule.onNodeWithText("Snap",substring=false).performScrollTo().performClick()
        assertTrue(model.state.value.snapEnabled)
        rule.runOnUiThread { model.add(Kind.RESISTOR,-20f,45f) }
        val added=model.state.value.circuit.components.last()
        assertEquals(-32f,added.x,0f);assertEquals(32f,added.y,0f)
    }
}
