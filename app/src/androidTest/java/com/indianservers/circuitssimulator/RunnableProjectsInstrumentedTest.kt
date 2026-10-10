package com.indianservers.circuitssimulator

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RunnableProjectsInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun projects():SimulatorViewModel {
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        rule.runOnUiThread {
            model.dismissRecovery()
            rule.activity.setContent {
                var designer by remember { mutableStateOf(false) }
                val state by model.state.collectAsState()
                Column {
                    if(designer) {
                        CircuitCanvas(state,model,Modifier.fillMaxWidth().weight(1f))
                        FirmwareSheet(state,model)
                    } else ProjectsPage(Modifier.fillMaxSize(),"",model,{designer=true},{})
                }
            }
        }
        return model
    }
    @Test fun projectRunOpensLiveOutputFromActualFirmware() {
        val model=projects()
        rule.onAllNodesWithText("Run project")[0].performClick()
        rule.waitUntil(15000) { model.state.value.firmware.console.any { it.contains("LED on") } }
        assertTrue(model.state.value.firmware.running)
        assertNull(model.state.value.firmware.error)
        rule.onNodeWithText("Live output · Serial Monitor").assertIsDisplayed()
        rule.onAllNodesWithText("Pause",useUnmergedTree=true)[0].assertExists()
        assertTrue(model.state.value.firmware.pins.isNotEmpty())
        rule.runOnUiThread { model.pauseFirmware() }
    }
    @Test fun projectCodeIsEditableAndRunUsesEditedSource() {
        val model=projects()
        rule.onAllNodesWithText("Code")[0].performClick()
        assertTrue(model.state.value.firmware.open)
        assertFalse(model.state.value.firmware.running)
        assertTrue(model.state.value.firmware.source.contains("digitalWrite(13, HIGH)"))
        rule.onNode(hasSetTextAction() and hasText("void setup",substring=true)).performTextReplacement(
            model.state.value.firmware.source.replace("LED on","Edited project ON"))
        rule.onNodeWithText("Run",useUnmergedTree=true).performClick()
        rule.waitUntil(15000) { model.state.value.firmware.console.any { it.contains("Edited project ON") } }
        assertNull(model.state.value.firmware.error)
        rule.onNodeWithText("Live output · Serial Monitor").assertIsDisplayed()
        rule.runOnUiThread { model.pauseFirmware() }
    }
}
