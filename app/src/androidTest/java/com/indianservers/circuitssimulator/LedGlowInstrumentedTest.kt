package com.indianservers.circuitssimulator

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcSolver
import com.indianservers.circuitssimulator.ui.SimulatorState
import com.indianservers.circuitssimulator.ui.canvas.*
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class LedGlowInstrumentedTest {
    private fun visual(kind:Kind,closed:Boolean):Pair<PlacedComponent,ComponentVisualState> {
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V",x=0f,y=0f,parameters=mapOf("voltage" to 3.3))
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R",x=0f,y=0f,parameters=mapOf("resistance" to 330.0))
        val switch=PlacedComponent(kind=Kind.SWITCH,reference="SW",x=0f,y=0f,closed=closed)
        val led=PlacedComponent(kind=kind,reference="LED",x=128f,y=140f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="G",x=0f,y=0f)
        fun wire(a:PlacedComponent,ap:Int,b:PlacedComponent,bp:Int)=Wire(start=TerminalRef(a.id,ap),end=TerminalRef(b.id,bp))
        val circuit=Circuit("LED visibility",listOf(source,resistor,switch,led,ground),listOf(
            wire(source,0,resistor,0),wire(resistor,1,switch,0),wire(switch,1,led,0),wire(led,1,source,1),wire(source,1,ground,0)))
        return led to ComponentVisualAdapter.read(led,SimulatorState(circuit=circuit,result=DcSolver().solve(circuit)))
    }
    private fun render(part:PlacedComponent,visual:ComponentVisualState):Bitmap {
        val bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(android.graphics.Canvas(bitmap)),Size(256f,256f)) {
            drawRect(Color(0xFF071622))
            drawComponent(part,false,visual.brightness,visual=visual,details=false)
        }
        return bitmap
    }
    @Test fun poweredLedsHaveVisiblePulsingHaloAndOpenCircuitHasNone() {
        val kinds=listOf(Kind.LED,Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED)
        val gallery=Bitmap.createBitmap(1024,768,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(gallery)
        kinds.forEachIndexed { column,kind ->
            val (part,on)=visual(kind,true);val (_,off)=visual(kind,false)
            assertTrue(kind.title,on.brightness>.001f);assertTrue(off.brightness<.001f)
            val offFrame=render(part,off)
            val low=render(part,on.copy(phase=.375f))
            val high=render(part,on.copy(phase=.125f))
            val x=183;val y=122
            assertNotEquals("${kind.title}: powered halo",offFrame.getPixel(x,y),high.getPixel(x,y))
            assertNotEquals("${kind.title}: animated halo",low.getPixel(x,y),high.getPixel(x,y))
            assertTrue(render(part,off.copy(phase=.125f)).sameAs(render(part,off.copy(phase=.375f))))
            assertTrue(render(part,on.copy(phase=.125f,reducedMotion=true)).sameAs(render(part,on.copy(phase=.375f,reducedMotion=true))))
            listOf(offFrame,low,high).forEachIndexed { row,frame -> canvas.drawBitmap(frame,column*256f,row*256f,null) }
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null),"led-on-off.png").outputStream().use {
            gallery.compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
