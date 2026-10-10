package com.indianservers.circuitssimulator

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BoardWorkspaceInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun inspectorOpensCodeForItsOwnBoardAndSwitchingPreservesBothPrograms() {
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        val uno=PlacedComponent(kind=Kind.ARDUINO_UNO,reference="UNO1",x=100f,y=100f)
        val mega=PlacedComponent(kind=Kind.ARDUINO_MEGA,reference="MEGA2",x=500f,y=100f)
        rule.runOnUiThread {
            model.dismissRecovery()
            model.loadSample(Circuit("Board UI",listOf(uno,mega),emptyList()))
            model.showFirmware(true,mega.id)
            rule.activity.setContent {
                val state by model.state.collectAsState()
                FirmwareSheet(state,model)
            }
        }
        rule.waitForIdle()
        assertEquals(mega.id,model.state.value.firmware.boardId)
        rule.runOnUiThread { model.setFirmwareSource("void setup(){pinMode(40,OUTPUT);}void loop(){delay(100);}") }
        rule.onNodeWithText("UNO1 · Arduino Uno R3").performClick()
        assertEquals(uno.id,model.state.value.firmware.boardId)
        rule.runOnUiThread { model.setFirmwareSource("void setup(){pinMode(13,OUTPUT);}void loop(){delay(500);}") }
        rule.onNodeWithText("MEGA2 · Arduino Mega 2560").performClick()
        assertTrue(model.state.value.firmware.source.contains("40,OUTPUT"))
        val saved=CircuitJson.decode(CircuitJson.encode(model.state.value.circuit))
        assertEquals(2,saved.firmware.size)
        assertEquals("arduino-mega2560-rev3-a000067",saved.firmware.first { it.boardId==mega.id }.profileId)
        assertTrue(saved.firmware.first { it.boardId==uno.id }.source.contains("13,OUTPUT"))
        rule.runOnUiThread { model.setUsbPower(mega.id,true) }
        assertTrue(model.state.value.circuit.firmware.first { it.boardId==mega.id }.usbPower)
        rule.runOnUiThread { model.setUsbPower(mega.id,false) }
        assertFalse(model.state.value.circuit.firmware.first { it.boardId==mega.id }.usbPower)
        assertEquals(0.0,model.state.value.circuit.components.first { it.id==mega.id }.value("usbPower"),0.0)
    }
    @Test fun nativeReferenceHasStructuredTopicsAndClickableOfficialSources() {
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        val pico=PlacedComponent(kind=Kind.RASPBERRY_PICO_W,reference="PICO",x=100f,y=100f)
        rule.runOnUiThread {
            model.dismissRecovery();model.loadSample(Circuit("Reference UI",listOf(pico),emptyList()))
            rule.activity.setContent {
                val state by model.state.collectAsState()
                BoardInspector(pico,state,model)
            }
        }
        rule.onNodeWithText("Datasheet").performScrollTo().performClick()
        rule.onNode(hasText("Profile: raspberry-pi-pico-w",substring=true)).assertExists()
        rule.onNode(hasText("Pico W datasheet · Raspberry Pi Ltd",substring=true)).assertHasClickAction()
        val screenshot=rule.onRoot().captureToImage()
        val path=java.io.File(rule.activity.getExternalFilesDir(null),"board-reference.png")
        path.outputStream().use { screenshot.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        rule.onNodeWithText("PWM").performScrollTo().performClick()
        rule.onNode(hasText("timed electrical HIGH/LOW frames",substring=true)).assertExists()
        rule.onNodeWithText("Code").performScrollTo().performClick()
        rule.onNodeWithText("Open code for PICO").performClick()
        assertEquals(pico.id,model.state.value.firmware.boardId)
        assertTrue(model.state.value.firmware.open)
    }
}
