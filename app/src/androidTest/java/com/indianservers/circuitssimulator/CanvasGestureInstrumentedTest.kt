package com.indianservers.circuitssimulator

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.ui.*
import com.indianservers.circuitssimulator.ui.canvas.terminalPosition
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.min
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

class CanvasGestureInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private lateinit var model:SimulatorViewModel
    private val battery=PlacedComponent(kind=Kind.BATTERY,reference="B",x=300f,y=300f,sizeScale=.6f)
    private val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R",x=700f,y=300f,sizeScale=.6f)
    private var mode by mutableStateOf(CanvasInteractionMode.CONNECT)
    private val canvas get()=rule.onNode(hasContentDescription("Circuit workspace.",substring=true))

    @Before fun setup() {
        model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        rule.runOnUiThread {
            model.dismissRecovery()
            model.loadSample(Circuit("Gesture QA",listOf(battery,resistor),emptyList()))
            model.zoom(.6f)
            rule.activity.setContent {
                val state by model.state.collectAsState()
                CircuitCanvas(state,model,Modifier.fillMaxSize(),interactionMode=mode)
            }
        }
        rule.waitForIdle()
    }
    private fun screen(world:Offset):Offset {
        val bounds=canvas.fetchSemanticsNode().boundsInRoot
        val state=model.state.value
        val scale=min(bounds.width/1000f,bounds.height/900f)*state.zoom
        return world*scale+Offset((bounds.width-1000f*scale)/2+state.panX,
            (bounds.height-900f*scale).coerceAtLeast(0f)*.5f+state.panY)
    }
    private fun pin(p:PlacedComponent,index:Int)=screen(terminalPosition(p,index))
    private fun assertUnmoved(before:SimulatorState) {
        val after=model.state.value
        assertEquals(before.panX,after.panX,.01f);assertEquals(before.panY,after.panY,.01f)
        assertEquals(before.circuit.components,after.circuit.components)
    }

    @Test fun dragFromSmallTerminalConnectsWithoutMovingCircuitOrComponents() {
        val before=model.state.value
        val start=pin(battery,0)+Offset(12f,0f)
        val end=pin(resistor,0)
        canvas.performTouchInput { swipe(start,end,500) }
        rule.waitUntil(5000) { model.state.value.circuit.wires.size==1 }
        val wire=model.state.value.circuit.wires.single()
        assertEquals(TerminalRef(battery.id,0),wire.start)
        assertEquals(TerminalRef(resistor.id,0),wire.end)
        assertUnmoved(before)
        // A connected terminal remains usable for a second wire (green glow).
        canvas.performTouchInput { swipe(pin(battery,0),pin(resistor,1),500) }
        rule.waitUntil(5000) { model.state.value.circuit.wires.size==2 }
        assertUnmoved(before)
    }

    @Test fun rapidTapToTapConnectsAndSingleFingerNeverPans() {
        val before=model.state.value
        val a=pin(battery,0);val b=pin(resistor,0)
        canvas.performTouchInput { click(a);advanceEventTime(80);click(b) }
        rule.waitUntil(5000) { model.state.value.circuit.wires.size==1 }
        canvas.performTouchInput { swipe(screen(Offset(500f,650f)),screen(Offset(700f,750f)),500) }
        canvas.performTouchInput { swipe(screen(Offset(battery.x,battery.y)),screen(Offset(400f,400f)),500) }
        assertUnmoved(before)
    }

    @Test fun rotatedTerminalRemainsConnectableAfterRotationAnimationSettles() {
        rule.runOnUiThread { model.rotate(battery.id) }
        rule.waitForIdle()
        val rotated=model.state.value.circuit.components.first { it.id==battery.id }
        val before=model.state.value
        val a=pin(rotated,0);val b=pin(resistor,0)
        canvas.performTouchInput { swipe(a,b,500) }
        rule.waitUntil(5000) { model.state.value.circuit.wires.size==1 }
        assertEquals(TerminalRef(battery.id,0),model.state.value.circuit.wires.single().start)
        assertUnmoved(before)
    }

    @Test fun twoFingerPanAndPinchCancelWireAndNeverMoveParts() {
        val before=model.state.value
        val a=pin(battery,0);val b=a+Offset(220f,0f)
        canvas.performTouchInput {
            down(0,a);down(1,b)
            repeat(12) { i ->
                val delta=Offset((i+1)*8f,(i+1)*4f)
                moveTo(0,a+delta,delayMillis=16);moveTo(1,b+delta,delayMillis=16)
            }
            up(0);up(1)
        }
        rule.waitForIdle()
        assertTrue(model.state.value.panX>before.panX+20f)
        assertEquals(before.circuit.components,model.state.value.circuit.components)
        assertTrue(model.state.value.circuit.wires.isEmpty())
        val zoom=model.state.value.zoom
        canvas.performTouchInput { pinch(Offset(400f,1000f),Offset(600f,1000f),Offset(320f,1000f),Offset(680f,1000f),500) }
        rule.waitForIdle()
        assertTrue(model.state.value.zoom>zoom)
        assertTrue(model.state.value.circuit.wires.isEmpty())
    }

    @Test fun viewDoesNotEditAndMovePartsRequiresExplicitMode() {
        rule.runOnUiThread { mode=CanvasInteractionMode.VIEW }
        rule.waitForIdle()
        val before=model.state.value
        canvas.performTouchInput { swipe(pin(battery,0),pin(resistor,0),500) }
        assertTrue(model.state.value.circuit.wires.isEmpty());assertUnmoved(before)
        rule.runOnUiThread { mode=CanvasInteractionMode.MOVE_PARTS }
        rule.waitForIdle()
        canvas.performTouchInput { swipe(screen(Offset(battery.x,battery.y)),screen(Offset(450f,450f)),500) }
        rule.waitUntil(5000) { model.state.value.circuit.components.first().x!=battery.x }
        assertEquals(before.panX,model.state.value.panX,.01f)
        assertTrue(model.state.value.circuit.wires.isEmpty())
    }

    @Test fun workspaceControlsDefaultToConnectAndSwitchCanvasMode() {
        rule.runOnUiThread { rule.activity.setContent { SimulatorScreen(model) } }
        rule.onNodeWithText("Connect").assertIsSelected()
        rule.onNodeWithText("View").performClick().assertIsSelected()
        rule.onNode(hasContentDescription("Inspect the circuit",substring=true)).assertExists()
        rule.onNodeWithText("Move parts").performClick().assertIsSelected()
        rule.onNode(hasContentDescription("drag a component",substring=true)).assertExists()
        rule.onNodeWithText("Connect").performClick().assertIsSelected()
        rule.onNode(hasContentDescription("tap two pins",substring=true)).assertExists()
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            val file=File(rule.activity.getExternalFilesDir(null),"gesture-modes.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
}
