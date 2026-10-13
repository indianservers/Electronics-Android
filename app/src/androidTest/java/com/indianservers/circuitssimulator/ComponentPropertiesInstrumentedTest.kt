package com.indianservers.circuitssimulator

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.ui.*
import com.indianservers.circuitssimulator.ui.canvas.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ComponentPropertiesInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
    private fun show(circuit:Circuit,selected:PlacedComponent) {
        val model=model()
        rule.runOnUiThread {
            model.dismissRecovery();model.loadSample(circuit);model.select(selected.id)
            rule.activity.setContent {
                val state by model.state.collectAsState()
                MaterialTheme(colorScheme=darkColorScheme(background=Navy,surface=Panel,onSurface=TextIce,primary=Blue)) {
                    Column(Modifier.fillMaxSize().background(Navy)) {
                        CircuitCanvas(state,model,Modifier.fillMaxWidth().weight(1f))
                        state.circuit.components.firstOrNull { it.id==state.selectedId }?.let { ComponentDetails(it,state,model) }
                    }
                }
            }
        }
    }
    private fun p(kind:Kind,reference:String,x:Float=200f)=PlacedComponent(kind=kind,reference=reference,x=x,y=220f)
    private fun capture(name:String) {
        val image=rule.onRoot().captureToImage()
        java.io.File(rule.activity.getExternalFilesDir(null),name).outputStream().use {
            image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun unitsInlineErrorsAndUndoUseActualComponentValues() {
        val part=p(Kind.RESISTOR,"R1")
        show(Circuit("Properties",listOf(part),emptyList()),part)
        rule.onNodeWithTag("property-resistance").performTextReplacement("0 Ω")
        rule.onNodeWithTag("apply-resistance").assertIsNotEnabled()
        assertEquals(330.0,model().state.value.circuit.components.first().value("resistance"),0.0)
        rule.onNodeWithTag("property-resistance").performTextReplacement("4.7 kΩ")
        rule.onNodeWithTag("apply-resistance").performClick()
        rule.waitUntil(5000) { model().state.value.circuit.components.first().value("resistance")==4700.0 }
        rule.runOnUiThread { model().undo() }
        assertEquals(330.0,model().state.value.circuit.components.first().value("resistance"),0.0)
        rule.runOnUiThread { model().redo() }
        assertEquals(4700.0,model().state.value.circuit.components.first().value("resistance"),0.0)
        assertEquals(part.id,model().state.value.selectedId)
        rule.onNodeWithTag("property-resistance").assertIsDisplayed()
        capture("properties-editor.png")
    }
    @Test fun exactPinPickerAndTransformsPreserveElectricalEndpoints() {
        val first=p(Kind.RESISTOR,"R1")
        val second=p(Kind.RESISTOR,"R2",500f)
        show(Circuit("Pin picker",listOf(first,second),emptyList()),first)
        assertTrue(model().state.value.circuit.wires.isEmpty())
        rule.onNodeWithText("Pins",substring=false).performClick()
        rule.onNodeWithTag("connect-pin-0").performClick()
        rule.onNodeWithTag("connection-search").performTextReplacement("R2")
        rule.onNodeWithTag("target-R2-1").performClick()
        rule.waitUntil(5000) { model().state.value.circuit.wires.size==1 }
        val wire=model().state.value.circuit.wires.single()
        assertEquals(TerminalRef(first.id,0),wire.start)
        assertEquals(TerminalRef(second.id,1),wire.end)
        rule.runOnUiThread {
            model().rotate(first.id);model().resize(first.id,1.4f);model().move(first.id,270f,260f)
        }
        assertEquals(wire,model().state.value.circuit.wires.single())
        val restored=CircuitJson.decode(CircuitJson.encode(model().state.value.circuit))
        assertEquals(wire,restored.wires.single())
        capture("properties-pins.png")
        rule.onNodeWithText("Disconnect this pin").performClick()
        rule.waitUntil(5000) { model().state.value.circuit.wires.isEmpty() }
    }
    @Test fun packagePinNumbersAndReservedPinsAreNotConfusedWithElectricalIndices() {
        val transistor=p(Kind.NPN_BJT,"Q1")
        show(Circuit("Transistor pins",listOf(transistor),emptyList()),transistor)
        rule.onNodeWithText("Pins",substring=false).performClick()
        rule.onNodeWithText("C · pin 3").assertExists()
        rule.onNodeWithText("B · pin 2").assertExists()
        rule.onNodeWithText("E · pin 1").performScrollTo().assertIsDisplayed()
        capture("properties-transistor.png")
        val op=p(Kind.LM741,"U1")
        rule.runOnUiThread { model().loadSample(Circuit("NC pin",listOf(op),emptyList()));model().select(op.id) }
        rule.onNodeWithText("Pins",substring=false).performClick()
        rule.onNodeWithTag("connect-pin-7").performScrollTo().assertIsNotEnabled()
        rule.runOnUiThread { model().connect(TerminalRef(op.id,7),TerminalRef(op.id,0)) }
        assertTrue(model().state.value.circuit.wires.isEmpty())
    }
    @Test fun placementAndSharedBoardHeadersUseTheSameValidatedPinMap() {
        val model=model()
        rule.runOnUiThread { model.dismissRecovery();model.loadSample(Circuit("Placement",emptyList(),emptyList()));model.setPlacement(Kind.ARDUINO_UNO);model.place(200f,200f) }
        val board=model.state.value.circuit.components.single()
        assertEquals(board.id,model.state.value.selectedId)
        assertTrue(model.state.value.circuit.wires.isEmpty())
        val definition=BoardRegistry.boards.getValue(board.kind)
        val a=TerminalRef(board.id,definition.index("A4"))
        val sda=TerminalRef(board.id,definition.index("SDA"))
        rule.runOnUiThread { model.connect(a,TerminalRef(board.id,definition.index("A5"))) }
        assertEquals(1,model.state.value.circuit.wires.size)
        assertTrue(TerminalRef(board.id,definition.index("A5")) in ComponentPins.net(model.state.value.circuit,sda))
        rule.runOnUiThread { model.beginConnection(a) }
        assertEquals(a,model.state.value.connectionStart)
        rule.runOnUiThread { model.remove(board.id) }
        assertNull(model.state.value.connectionStart)
        assertTrue(model.state.value.circuit.wires.isEmpty())
    }
    @Test fun sensorPropertyChangesAndResetUpdateFirmwareStimulusWithUndo() {
        val sensor=p(Kind.LM35_SENSOR,"TMP1")
        show(Circuit("Shared stimulus",listOf(sensor),emptyList(),environment=EnvironmentState(temperatureC=40.0)),sensor)
        rule.runOnUiThread { assertTrue(model().setParameter(sensor.id,"temperatureC",25.0)) }
        assertEquals(25.0,model().state.value.circuit.environment.temperatureC,0.0)
        rule.runOnUiThread { model().undo() }
        assertEquals(40.0,model().state.value.circuit.environment.temperatureC,0.0)
        rule.runOnUiThread { model().setParameter(sensor.id,"temperatureC",50.0);model().resetComponentProperties(sensor.id) }
        assertEquals(25.0,model().state.value.circuit.environment.temperatureC,0.0)
        assertEquals(25.0,model().state.value.circuit.components.single().value("temperatureC"),0.0)
    }
}
