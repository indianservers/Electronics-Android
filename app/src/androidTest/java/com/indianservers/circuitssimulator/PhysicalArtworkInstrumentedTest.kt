package com.indianservers.circuitssimulator

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import android.os.Debug
import android.os.Process
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.ui.*
import com.indianservers.circuitssimulator.ui.canvas.*
import com.indianservers.circuitssimulator.simulation.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.*

class PhysicalArtworkInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val output get()=File(rule.activity.getExternalFilesDir(null),"visual-qa").apply { mkdirs() }
    private val scope=CanvasDrawScope()
    private val legacy=setOf(Kind.BATTERY,Kind.RESISTOR,Kind.FUSE,Kind.LED,Kind.RED_LED,
        Kind.GREEN_LED,Kind.BLUE_LED,Kind.LAMP,Kind.INDUCTOR,Kind.JUNCTION,Kind.SOLENOID,Kind.DIP_SWITCH_4)
    private fun save(bitmap:Bitmap,name:String) { File(output,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
    private fun frame(bitmap:Bitmap,block:CanvasDrawScope.()->Unit) {
        scope.draw(Density(1f),LayoutDirection.Ltr,Canvas(android.graphics.Canvas(bitmap)),Size(bitmap.width.toFloat(),bitmap.height.toFloat())) { scope.block() }
    }
    private fun screenshot(name:String) {
        // Allow layout, viewport state, and motion settling to cross separate UI frames.
        repeat(12) {
            rule.mainClock.advanceTimeBy(100)
            rule.waitForIdle()
            Thread.sleep(40)
        }
        // Copy this activity's actual surface. UiAutomation can be disconnected
        // independently when a long-running host ADB session is interrupted.
        val ready=java.util.concurrent.CountDownLatch(1)
        val result=java.util.concurrent.atomic.AtomicInteger(-1)
        lateinit var bitmap:Bitmap
        rule.runOnUiThread {
            val decor=rule.activity.window.decorView
            bitmap=Bitmap.createBitmap(decor.width,decor.height,Bitmap.Config.ARGB_8888)
            android.view.PixelCopy.request(rule.activity.window,bitmap,{
                result.set(it);ready.countDown()
            },android.os.Handler(android.os.Looper.getMainLooper()))
        }
        try {
            assertTrue("Activity surface capture timed out",ready.await(5,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("Activity surface capture failed",android.view.PixelCopy.SUCCESS,result.get())
            save(bitmap,name)
        } finally {bitmap.recycle()}
    }

    @Test fun entireLibraryRendersAtEveryRotationZoomAndGeneratesAuditGallery() {
        val audit=JSONArray()
        val scratch=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        Kind.entries.forEach { kind ->
            val part=PlacedComponent(kind=kind,reference="",x=256f,y=256f)
            var handled=false
            frame(scratch) { handled=drawPhysicalBody(part,ComponentVisualState(),false) }
            assertEquals("Unreviewed renderer for $kind",kind !in legacy,handled)
            val zooms=listOf(.25f,.5f,.75f,1f,1.5f,2f,3f)
            for(zoom in zooms) for(rotation in listOf(0,90,180,270)) {
                scratch.eraseColor(android.graphics.Color.TRANSPARENT)
                frame(scratch) { drawComponent(part.copy(rotation=rotation,sizeScale=zoom),false,details=zoom>=.6f) }
                for(pin in 0 until part.terminalCount) {
                    val terminal=terminalPosition(part.copy(rotation=rotation,sizeScale=zoom),pin)
                    assertTrue(terminal.x.isFinite() && terminal.y.isFinite())
                    val base=localTerminalOffset(part,pin)
                    assertEquals(hypot(base.x,base.y)*zoom,hypot(terminal.x-part.x,terminal.y-part.y),.001f)
                }
            }
            // Detect accidental DrawScope default centers: the part is deliberately off-center.
            scratch.eraseColor(android.graphics.Color.TRANSPARENT)
            val shifted=part.copy(x=128f,y=128f)
            frame(scratch) { drawComponent(shifted,false) }
            val pixels=IntArray(512*512);scratch.getPixels(pixels,0,512,0,0,512,512)
            val board=BoardRegistry.boards[kind]
            val radius=if(board!=null) hypot(board.boardWidth,board.boardHeight)/2f+40f
                else if(kind in IcParts.pinNames && part.terminalCount>=16) 160f else 115f
            pixels.forEachIndexed { index,pixel ->
                if((pixel ushr 24)!=0) assertTrue("$kind draws outside its local component coordinates",
                    abs(index%512-128f)<=radius && abs(index/512-128f)<=radius)
            }
            audit.put(JSONObject().put("component",kind.name).put("title",kind.title)
                .put("renderer",if(handled) { if(ComponentRegistry.definitions.getValue(kind).datasheet.packageName?.contains("simulated",ignoreCase=true)==true) "simulated carrier" else "physical package" } else "dedicated passive/light/contact")
                .put("toolboxCanvasShared",true).put("pins","existing stable anchors")
                .put("rotations",JSONArray(listOf(0,90,180,270))).put("zooms",JSONArray(zooms))
                .put("nativeRenderVerified",true))
        }
        scratch.recycle()
        File(output,"component-audit.json").writeText(audit.toString(2))
        Kind.entries.chunked(25).forEachIndexed { page,kinds ->
            val bitmap=Bitmap.createBitmap(1200,1100,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(7,21,34))
            val paint=android.graphics.Paint(3).apply { color=android.graphics.Color.rgb(221,234,255);textSize=14f;textAlign=android.graphics.Paint.Align.CENTER }
            frame(bitmap) {
                kinds.forEachIndexed { i,kind ->
                    val x=120f+(i%5)*240f;val y=95f+(i/5)*220f
                    val b=BoardRegistry.boards[kind]
                    val factor=if(b!=null) min(170f/(b.boardWidth+25),160f/(b.boardHeight+25)) else .9f
                    drawComponent(PlacedComponent(kind=kind,reference="",x=x,y=y,sizeScale=factor),false,details=true)
                    drawContext.canvas.nativeCanvas.drawText(kind.title.take(30),x,y+107f,paint)
                }
            }
            save(bitmap,"library-${page+1}.png");bitmap.recycle()
        }
    }

    @Test fun nativeDrawingBenchmark25Animated50And100Components() {
        val bitmap=Bitmap.createBitmap(1080,1600,Bitmap.Config.ARGB_8888)
        val report=JSONArray()
        for(count in listOf(25,50,100)) {
            val parts=(0 until count).map { i -> PlacedComponent(kind=listOf(Kind.DC_MOTOR,Kind.SERVO_MOTOR,Kind.LED,Kind.RELAY,Kind.RGB_LED)[i%5],reference="",x=70f+(i%10)*105f,y=75f+(i/10)*140f,sizeScale=.55f) }
            val times=mutableListOf<Double>()
            val cpuStart=Process.getElapsedCpuTime()
            repeat(90) { index ->
                val start=System.nanoTime()
                frame(bitmap) { parts.forEach { drawComponent(it,false,.5f,visual=ComponentVisualState(
                    motor=MotorVisualState(3200f,true),rotorDegrees=index*9f,servoDegrees=(index*2f)%180f,
                    brightness=.5f,red=.5f,green=.3f,relayClosed=index%2==0),details=false) } }
                if(index>=10) times+=(System.nanoTime()-start)/1e6
            }
            val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory)
            val ordered=times.sorted()
            report.put(JSONObject().put("components",count).put("nativeDrawMeanMs",times.average())
                .put("nativeDrawP95Ms",ordered[(ordered.size*.95).toInt()])
                .put("cpuElapsedMs",Process.getElapsedCpuTime()-cpuStart).put("processPssKb",memory.totalPss)
                .put("measurement","Native renderer CPU draw time; excludes GPU and Compose recompositions"))
        }
        File(output,"performance.json").writeText(report.toString(2));bitmap.recycle()
    }

    @Test fun actualMotorCircuitOffOnVoltagePolarityAndDisconnect() {
        rule.mainClock.autoAdvance=false
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        val b=PlacedComponent(kind=Kind.BATTERY,reference="B1",x=200f,y=480f)
        val sw=PlacedComponent(kind=Kind.SWITCH,reference="SW1",x=460f,y=280f,closed=false)
        val motor=PlacedComponent(kind=Kind.DC_MOTOR,reference="M1",x=750f,y=480f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND",x=450f,y=760f)
        fun wire(a:PlacedComponent,ai:Int,c:PlacedComponent,ci:Int)=Wire(start=TerminalRef(a.id,ai),end=TerminalRef(c.id,ci))
        val circuit=Circuit("Physical Motor QA",listOf(b,sw,motor,ground),listOf(wire(b,0,sw,0),wire(sw,1,motor,0),wire(motor,1,b,1),wire(b,1,ground,0)))
        rule.runOnUiThread { rule.activity.setContent { SimulatorScreen(model) };model.dismissRecovery();model.loadSample(circuit) }
        rule.waitUntil(10000) { model.state.value.transient?.checkpoint!=null }
        fun rpm()=ComponentVisualAdapter.read(motor,model.state.value).motor.rpm
        assertEquals(0f,rpm(),.01f)
        screenshot("motor-off.png")
        rule.runOnUiThread { model.toggleSwitch(sw.id) }
        rule.waitUntil(15000) { rpm()>100f }
        screenshot("motor-running.png")
        rule.runOnUiThread { model.setParameter(b.id,"voltage",18.0) }
        rule.waitUntil(15000) { rpm()>400f }
        val high=rpm()
        rule.runOnUiThread { model.setParameter(b.id,"voltage",3.0) }
        rule.waitUntil(15000) { rpm()<high*.7f }
        val reversed=circuit.copy(components=circuit.components.map { if(it.id==sw.id) it.copy(closed=true) else it },
            wires=listOf(wire(b,0,sw,0),wire(sw,1,motor,1),wire(motor,0,b,1),wire(b,1,ground,0)))
        rule.runOnUiThread { model.loadSample(reversed) }
        rule.waitUntil(15000) { rpm()< -100f }
        screenshot("motor-reversed.png")
        rule.runOnUiThread { model.removeWire(reversed.wires[1].id) }
        rule.waitUntil(15000) { abs(rpm())<1f }
        screenshot("motor-disconnected.png")
    }

    @Test fun realLedAndRelayAndBoardCanvas() {
        rule.mainClock.autoAdvance=false
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        val ledCircuit=SampleCircuits.led()
        val led=ledCircuit.components.first { it.kind==Kind.LED }
        val resistor=ledCircuit.components.first { it.kind==Kind.RESISTOR }
        rule.runOnUiThread { rule.activity.setContent { SimulatorScreen(model) };model.dismissRecovery();model.loadSample(ledCircuit) }
        rule.waitUntil(10000) { (model.state.value.result.readings[led.id]?.current ?: 0.0)>.001 }
        val bright=ComponentVisualAdapter.read(led,model.state.value).brightness
        rule.runOnUiThread { model.setParameter(resistor.id,"resistance",2200.0) }
        rule.waitUntil(10000) { ComponentVisualAdapter.read(led,model.state.value).brightness<bright }
        screenshot("led-current.png")
        val backwards=ledCircuit.copy(wires=ledCircuit.wires.map { w -> w.copy(
            start=if(w.start.componentId==led.id) w.start.copy(index=1-w.start.index) else w.start,
            end=if(w.end.componentId==led.id) w.end.copy(index=1-w.end.index) else w.end) })
        rule.runOnUiThread { model.loadSample(backwards) }
        rule.waitUntil(10000) { abs(model.state.value.result.readings[led.id]?.current ?: 1.0)<1e-6 }
        assertEquals(0f,ComponentVisualAdapter.read(led,model.state.value).brightness,.001f)
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=170f,y=500f,parameters=mapOf("voltage" to 9.0))
        val relay=PlacedComponent(kind=Kind.RELAY,reference="K1",x=500f,y=500f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND",x=200f,y=750f)
        val circuit=Circuit("Relay QA",listOf(source,relay,ground),listOf(
            Wire(start=TerminalRef(source.id,0),end=TerminalRef(relay.id,0)),
            Wire(start=TerminalRef(relay.id,1),end=TerminalRef(source.id,1)),
            Wire(start=TerminalRef(source.id,1),end=TerminalRef(ground.id,0))))
        rule.runOnUiThread { model.loadSample(circuit) }
        rule.waitUntil(10000) { ComponentVisualAdapter.read(relay,model.state.value).relayClosed }
        screenshot("relay-on.png")
        rule.runOnUiThread { model.setParameter(source.id,"voltage",1.0) }
        rule.waitUntil(10000) { !ComponentVisualAdapter.read(relay,model.state.value).relayClosed }
        screenshot("relay-off.png")
        val kinds=listOf(Kind.ARDUINO_UNO,Kind.RASPBERRY_PICO,Kind.ESP32_DEVKIT,Kind.ULTRASONIC,Kind.SERVO_MOTOR,Kind.POTENTIOMETER,Kind.SEVEN_SEGMENT,Kind.BUZZER,Kind.BATTERY,Kind.SWITCH,Kind.RESISTOR,Kind.LED,Kind.DC_MOTOR,Kind.RELAY)
        val bench=Circuit("Physical Bench QA",kinds.mapIndexed { i,k -> PlacedComponent(kind=k,reference=k.prefix,x=140f+(i%4)*240f,y=120f+(i/4)*180f,sizeScale=if(k.isBoard) .55f else .75f) },emptyList())
        rule.runOnUiThread { model.loadSample(bench) }
        rule.waitUntil(10000) { model.state.value.circuit.name==bench.name }
        screenshot("physical-bench.png")
        val wave=SampleCircuits.rcLowPass()
        rule.runOnUiThread { model.loadSample(wave) }
        rule.waitUntil(10000) { (model.state.value.transient?.frames?.size ?: 0) > 5 }
        rule.runOnUiThread { model.attachScope(1,wave.wires.first().id);model.showScope(true) }
        screenshot("scope-live.png")
    }
    @Test fun composedCanvasBenchmarksSharedMotionWithoutFrameRecomposition() {
        rule.mainClock.autoAdvance=false
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        val report=JSONArray()
        for(count in listOf(25,50,100)) {
            val parts=(0 until count).map { i -> PlacedComponent(kind=Kind.DC_MOTOR,reference="M$i",
                x=55f+(i%10)*100f,y=55f+(i/10)*85f,sizeScale=.42f) }
            val state=SimulatorState(circuit=Circuit("$count motor render benchmark",parts,emptyList()),
                result=DcResult(readings=parts.associate { it.id to Reading(9.0,.3,2.7) }))
            val compositions=java.util.concurrent.atomic.AtomicInteger()
            val times=java.util.Collections.synchronizedList(mutableListOf<Long>())
            val probe=CanvasPerformanceProbe({compositions.incrementAndGet()},{times.add(it)})
            rule.runOnUiThread { rule.activity.setContent {
                CircuitCanvas(state,model,androidx.compose.ui.Modifier.fillMaxSize(),probe)
            } }
            rule.mainClock.advanceTimeBy(250)
            rule.waitForIdle()
            Thread.sleep(150)
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            val initial=compositions.get();times.clear()
            val cpuStart=Process.getElapsedCpuTime()
            val wallStart=System.nanoTime()
            repeat(45) {
                rule.mainClock.advanceTimeByFrame()
                rule.waitForIdle()
                Thread.sleep(16)
            }
            val cpu=Process.getElapsedCpuTime()-cpuStart
            val wall=(System.nanoTime()-wallStart)/1e6
            val samples=times.toList().map { it/1e6 }.sorted()
            screenshot("canvas-$count-motors.png")
            assertTrue("Animation did not draw frames: ${samples.size} draws, ${compositions.get()} compositions",samples.size>10)
            assertTrue("Motion recomposes the entire canvas",compositions.get()-initial<=2)
            val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory)
            report.put(JSONObject().put("components",count).put("drawFrames",samples.size)
                .put("drawMeanMs",samples.average()).put("drawP95Ms",samples[(samples.size*.95).toInt()])
                .put("animationRecompositions",compositions.get()-initial).put("cpuElapsedMs",cpu)
                .put("cpuWallPercent",cpu/wall*100).put("processPssKb",memory.totalPss)
                .put("measurement","Running Compose canvas draw recording, shared clock; emulator GPU timing is separate"))
            screenshot("canvas-$count-motors.png")
        }
        File(output,"compose-performance.json").writeText(report.toString(2))
    }

    @Test fun servoHornTracksActualControllerCommandsAtFiveAngles() {
        rule.mainClock.autoAdvance=false
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        val circuit=SampleCircuits.servoControl()
        val servo=circuit.components.first { it.kind==Kind.SERVO_MOTOR }
        rule.runOnUiThread { rule.activity.setContent { SimulatorScreen(model) };model.dismissRecovery();model.loadSample(circuit) }
        rule.waitUntil(10000) { model.state.value.circuit.name==circuit.name }
        for(angle in listOf(0,45,90,135,180)) {
            rule.runOnUiThread {
                model.showFirmware(true)
                model.setFirmwareSource("Servo servo; void setup() { servo.attach(9); servo.write($angle); } void loop() { delay(20); }")
                assertTrue(model.compileFirmware())
                model.showFirmware(false)
                model.runFirmware()
            }
            rule.waitUntil(15000) {
                val command=ComponentVisualAdapter.servoCommand(servo,model.state.value)
                command!=null && abs(command-angle)<.01f
            }
            val visual=ComponentVisualAdapter.read(servo,model.state.value)
            assertTrue("Servo lacks solved supply",visual.servoDriven)
            assertEquals(angle.toFloat(),visual.servoDegrees,.01f)
            screenshot("servo-$angle.png")
            rule.runOnUiThread { model.stopFirmware() }
        }
    }

    @Test fun lcdAndOledRenderActualI2cText() {
        rule.mainClock.autoAdvance=false
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        rule.runOnUiThread { rule.activity.setContent { SimulatorScreen(model) };model.dismissRecovery() }
        for(kind in listOf(Kind.OLED_SSD1306,Kind.I2C_LCD)) {
            val base=com.indianservers.circuitssimulator.firmware.FirmwareExamples.esp32OledCircuit()
            val oled=base.components.first { it.kind==Kind.OLED_SSD1306 }
            val device=oled.copy(kind=kind,parameters=mapOf("address" to if(kind==Kind.I2C_LCD)39.0 else 60.0))
            val uno=PlacedComponent(id="LCD_MCU",kind=Kind.ARDUINO_UNO,reference="B1",x=200f,y=390f,parameters=mapOf("usbPower" to 1.0))
            val ground=PlacedComponent(id="LCD_GND",kind=Kind.GROUND,reference="GND",x=200f,y=700f)
            val pulls=(0..1).map {PlacedComponent(id="LCD_PU$it",kind=Kind.RESISTOR,reference="PU$it",x=450f,y=150f+it*90,parameters=mapOf("resistance" to 4700.0))}
            val pins=BoardRegistry.boards.getValue(uno.kind)
            fun pin(label:String)=TerminalRef(uno.id,pins.index(label))
            val circuit=if(kind==Kind.OLED_SSD1306)base else Circuit("5 V LCD backpack QA",listOf(uno,ground,device)+pulls,
                listOf(Wire(start=pin("GND"),end=TerminalRef(ground.id,0)),Wire(start=pin("5V"),end=TerminalRef(device.id,0)),
                    Wire(start=pin("GND"),end=TerminalRef(device.id,1)),Wire(start=pin("A4"),end=TerminalRef(device.id,2)),
                    Wire(start=pin("A5"),end=TerminalRef(device.id,3)))+pulls.mapIndexed { i,r ->
                    listOf(Wire(start=pin("5V"),end=TerminalRef(r.id,0)),Wire(start=TerminalRef(r.id,1),end=pin(if(i==0)"A4" else "A5")))
                }.flatten())
            val glyphs=listOf(127,8,8,8,127,0,127,73,73,73,65,0,127,64,64,64,64,0,127,64,64,64,64,0,62,65,65,65,62,0)
            val program=buildString {
                append("void setup(){Wire.begin();delay(50);")
                if(kind==Kind.OLED_SSD1306) {
                    append("Wire.beginTransmission(60);Wire.write(0);Wire.write(175);Wire.write(176);Wire.write(0);Wire.write(16);Wire.endTransmission();")
                    append("Wire.beginTransmission(60);Wire.write(64);")
                    glyphs.forEach {append("Wire.write($it);")};append("Wire.endTransmission();")
                } else {
                    fun port(v:Int){append("Wire.beginTransmission(39);Wire.write($v);Wire.endTransmission();")}
                    fun nibble(n:Int,rs:Int){port(n*16+8+rs+4);port(n*16+8+rs)}
                    fun byte(v:Int,rs:Int){nibble(v shr 4,rs);nibble(v and 15,rs)}
                    repeat(3){nibble(3,0);append("delay(5);")};nibble(2,0)
                    byte(0x28,0);byte(0x0C,0);byte(1,0);append("delay(2);");byte(6,0);byte(0x80,0)
                    "HELLO".forEach {byte(it.code,1)}
                }
                append("}void loop(){delay(20);}")
            }
            rule.runOnUiThread { model.loadSample(circuit);model.showFirmware(true)
                model.setFirmwareSource(program)
                assertTrue(model.compileFirmware());model.showFirmware(false);model.runFirmware() }
            if(kind==Kind.OLED_SSD1306) {
                val expected=glyphs+List(1024-glyphs.size){0}
                rule.waitUntil(15000) { model.state.value.firmware.framebuffers[device.id]==expected }
                assertEquals(expected,ComponentVisualAdapter.read(device,model.state.value).pixels)
            } else {
                rule.waitUntil(15000) { model.state.value.firmware.displays[device.id]?.lineSequence()?.firstOrNull()?.trimEnd()=="HELLO" }
                assertEquals("HELLO",ComponentVisualAdapter.read(device,model.state.value).display.lineSequence().first().trimEnd())
            }
            screenshot("display-${kind.name}.png")
            rule.runOnUiThread { model.stopFirmware() }
        }
    }

}
