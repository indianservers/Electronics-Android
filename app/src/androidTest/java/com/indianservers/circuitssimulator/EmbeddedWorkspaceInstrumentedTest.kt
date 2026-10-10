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
import com.indianservers.circuitssimulator.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EmbeddedWorkspaceInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun nativeOledShowsFirmwareRamAndReferenceInspector() {
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        val board=PlacedComponent(kind=Kind.ARDUINO_UNO,reference="UNO",x=180f,y=220f,parameters=mapOf("usbPower" to 1.0))
        val oled=PlacedComponent(kind=Kind.OLED_SSD1306,reference="OLED",x=480f,y=220f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="G",x=300f,y=420f)
        val def=BoardRegistry.boards.getValue(board.kind)
        fun wire(name:String,index:Int)=Wire(start=TerminalRef(board.id,def.index(name)),end=TerminalRef(oled.id,index))
        val circuit=Circuit("OLED native bench",listOf(board,oled,ground),listOf(wire("3V3",0),wire("GND",1),wire("A4",2),wire("A5",3),
            Wire(start=TerminalRef(board.id,def.index("GND")),end=TerminalRef(ground.id,0))))
        rule.runOnUiThread {
            model.dismissRecovery();model.loadSample(circuit);model.showFirmware(true,board.id)
            model.setFirmwareSource("void setup(){Wire.begin();Wire.beginTransmission(60);Wire.write(0);Wire.write(175);Wire.endTransmission();Wire.beginTransmission(60);Wire.write(64);Wire.write(66);Wire.endTransmission();}void loop(){delay(100);}")
            model.runFirmware();model.showFirmware(false)
            rule.activity.setContent { val state by model.state.collectAsState();Column {
                CircuitCanvas(state,model,Modifier.fillMaxWidth().weight(1f));ComponentDetails(oled,state,model)
            } }
        }
        rule.waitUntil(15000) { model.state.value.firmware.framebuffers[oled.id]?.firstOrNull()==66 }
        assertNull(model.state.value.firmware.error)
        rule.onNodeWithText("Device state / reference").performScrollTo().performClick()
        rule.onNodeWithText("SSD1306 128×64 module").assertExists()
        rule.onNodeWithText("Manufacturer reference").assertHasClickAction()
        rule.onNodeWithText("Display on: true").performScrollTo().assertIsDisplayed()
        val image=rule.onRoot().captureToImage()
        java.io.File(rule.activity.getExternalFilesDir(null),"embedded-workspace.png").outputStream().use {
            image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        rule.runOnUiThread { model.pauseFirmware() }
    }
    @Test fun nativeMemoryEditorChangesPersistedDeviceByte() {
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        val memory=PlacedComponent(kind=Kind.I2C_EEPROM,reference="EEPROM",x=200f,y=200f)
        rule.runOnUiThread {
            model.dismissRecovery();model.loadSample(Circuit("Memory editor",listOf(memory),emptyList()))
            rule.activity.setContent { val state by model.state.collectAsState();ComponentDetails(memory,state,model) }
        }
        rule.onNodeWithText("Device state / reference").performScrollTo().performClick()
        rule.onNodeWithText("HEX address").performScrollTo().performTextReplacement("10")
        rule.onNodeWithText("HEX byte").performTextReplacement("42")
        rule.onNodeWithText("Write",useUnmergedTree=true).performClick()
        assertEquals(66,model.state.value.circuit.peripheralMemory[memory.id]?.get(16))
        val restored=com.indianservers.circuitssimulator.data.CircuitJson.decode(com.indianservers.circuitssimulator.data.CircuitJson.encode(model.state.value.circuit))
        assertEquals(66,restored.peripheralMemory[memory.id]?.get(16))
    }
}
