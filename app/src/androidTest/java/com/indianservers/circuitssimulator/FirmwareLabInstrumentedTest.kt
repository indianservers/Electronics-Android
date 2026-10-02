package com.indianservers.circuitssimulator

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.ui.SimulatorViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FirmwareLabInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]

    @Test fun executableBlinkAppearsInLabAndDrivesScopeFrames() {
        val model=model()
        rule.runOnUiThread { model.loadSample(SampleCircuits.blinkingLed()) }
        rule.onNodeWithText("Circuit Designer").performClick()
        rule.runOnUiThread { model.showFirmware(true) }
        rule.onNodeWithText("Firmware Lab").assertExists()
        rule.onNodeWithText("Arduino subset",substring=true).assertExists()
        rule.onNodeWithText("Run").performClick()
        rule.waitUntil(10000) { model.state.value.firmware.timeMicros>=1_000_000L }
        val state=model.state.value
        val led=state.circuit.components.first { it.reference=="D1" }
        val board=state.circuit.components.first { it.kind==Kind.ARDUINO_UNO }
        val d13=TerminalRef(board.id,BoardRegistry.boards.getValue(board.kind).index("D13"))
        val frames=state.transient!!.frames
        assertTrue(frames.any { (it.readings[led.id]?.current ?: 0.0)>.001 })
        assertTrue(frames.any { (it.readings[led.id]?.current ?: 1.0)<1e-5 })
        assertTrue(frames.mapNotNull { it.nodeVoltages[d13] }.max()>3.0)
        assertTrue(frames.mapNotNull { it.nodeVoltages[d13] }.min()<1.0)
        assertTrue(state.firmware.console.any { it.contains("LED on") })
        assertTrue(state.firmware.console.any { it.contains("LED off") })
        rule.runOnUiThread { model.pauseFirmware() }
    }

    @Test fun stopClearsLedDriveAndResetRerunsBlink() {
        val model=model()
        rule.runOnUiThread { model.loadSample(SampleCircuits.blinkingLed()) }
        rule.onNodeWithText("Circuit Designer").performClick()
        rule.runOnUiThread { model.showFirmware(true) }
        rule.onNodeWithText("Run").performClick()
        rule.waitUntil(10000) { model.state.value.firmware.timeMicros>=200_000L }
        val led=model.state.value.circuit.components.first { it.reference=="D1" }
        assertTrue(model.state.value.transient!!.frames.any { (it.readings[led.id]?.current ?: 0.0)>.001 })
        rule.runOnUiThread { model.stopFirmware() }
        rule.waitUntil(4000) {
            !model.state.value.firmware.running &&
                (model.state.value.result.readings[led.id]?.current ?: 0.0)<1e-4
        }
        rule.runOnUiThread { model.resetFirmware();model.runFirmware() }
        rule.waitUntil(10000) { model.state.value.firmware.timeMicros>=200_000L }
        assertTrue(model.state.value.transient!!.frames.any { (it.readings[led.id]?.current ?: 0.0)>.001 })
        rule.runOnUiThread { model.pauseFirmware() }
    }
}
