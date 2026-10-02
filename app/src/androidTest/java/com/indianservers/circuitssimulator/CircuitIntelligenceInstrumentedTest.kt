package com.indianservers.circuitssimulator

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.guide.LessonTemplates
import com.indianservers.circuitssimulator.intelligence.SmartFix
import com.indianservers.circuitssimulator.ui.SimulatorViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CircuitIntelligenceInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
    private fun waitFor(code:String) { rule.waitUntil(5000) { model().state.value.intelligence.issues.any { it.ruleId==code } } }
    private fun waitGone(code:String) { rule.waitUntil(5000) { model().state.value.intelligence.issues.none { it.ruleId==code } } }
    private fun p(kind:Kind,ref:String,x:Float=300f,y:Float=300f,values:Map<String,Double> = emptyMap())=
        PlacedComponent(kind=kind,reference=ref,x=x,y=y,parameters=values)
    private fun w(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=
        Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))

    @Test fun directShortFixAndUndoRestoreDiagnostic() {
        val model=model()
        rule.runOnUiThread { model.loadSample(LessonTemplates.led(fault="short")) }
        waitFor("SOURCE_SHORT")
        val fix=model.state.value.intelligence.issues.first { it.ruleId=="SOURCE_SHORT" }
            .fixes.filterIsInstance<SmartFix.RemoveWire>().single()
        rule.runOnUiThread { model.applySmartFix(fix) }
        waitGone("SOURCE_SHORT")
        rule.runOnUiThread { model.undo() }
        waitFor("SOURCE_SHORT")
    }

    @Test fun reversedLedFixAndUndoRestoreDiagnostic() {
        val model=model()
        rule.runOnUiThread { model.loadSample(LessonTemplates.led(fault="reversed_led")) }
        waitFor("LED_REVERSED")
        val fix=model.state.value.intelligence.issues.first { it.ruleId=="LED_REVERSED" }
            .fixes.filterIsInstance<SmartFix.ReverseLed>().single()
        rule.runOnUiThread { model.applySmartFix(fix) }
        waitGone("LED_REVERSED")
        rule.runOnUiThread { model.undo() }
        waitFor("LED_REVERSED")
    }

    @Test fun addGroundFixAndUndoRestoreDiagnostic() {
        val model=model()
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=180f,y=400f)
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=600f,y=400f)
        val c=Circuit("Ground fix",listOf(source,resistor),listOf(
            Wire(start=TerminalRef(source.id,0),end=TerminalRef(resistor.id,0)),
            Wire(start=TerminalRef(resistor.id,1),end=TerminalRef(source.id,1))))
        rule.runOnUiThread { model.loadSample(c) }
        waitFor("NO_GROUND")
        val fix=model.state.value.intelligence.issues.first { it.ruleId=="NO_GROUND" }
            .fixes.filterIsInstance<SmartFix.AddGround>().single()
        rule.runOnUiThread { model.applySmartFix(fix) }
        waitGone("NO_GROUND")
        rule.runOnIdle { assertTrue(model.state.value.circuit.components.any { it.kind==Kind.GROUND }) }
        rule.runOnUiThread { model.undo() }
        waitFor("NO_GROUND")
    }

    @Test fun insertSeriesResistorFixAndUndoRestoreDiagnostic() {
        val model=model()
        val battery=PlacedComponent(kind=Kind.BATTERY,reference="B1",x=150f,y=450f)
        val led=PlacedComponent(kind=Kind.LED,reference="D1",x=790f,y=450f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=480f,y=740f)
        val c=Circuit("Direct LED",listOf(battery,led,ground),listOf(
            Wire(start=TerminalRef(battery.id,0),end=TerminalRef(led.id,0)),
            Wire(start=TerminalRef(led.id,1),end=TerminalRef(battery.id,1)),
            Wire(start=TerminalRef(battery.id,1),end=TerminalRef(ground.id,0))))
        rule.runOnUiThread { model.loadSample(c) }
        waitFor("LED_NO_RESISTOR")
        val fix=model.state.value.intelligence.issues.first { it.ruleId=="LED_NO_RESISTOR" }
            .fixes.filterIsInstance<SmartFix.InsertLedResistor>().single()
        rule.runOnUiThread { model.applySmartFix(fix) }
        waitGone("LED_NO_RESISTOR")
        rule.runOnIdle { assertTrue(model.state.value.circuit.components.any { it.kind==Kind.RESISTOR }) }
        rule.runOnUiThread { model.undo() }
        waitFor("LED_NO_RESISTOR")
    }

    @Test fun checkCircuitUiShowsEvidenceAndPreviewBeforeFix() {
        val model=model()
        rule.runOnUiThread { model.loadSample(LessonTemplates.led(fault="short")) }
        waitFor("SOURCE_SHORT")
        rule.onNodeWithText("Circuit Designer").performClick()
        rule.onNodeWithText("Simulation").performClick()
        rule.onNodeWithText("Check Circuit").performClick()
        rule.onNodeWithText("Circuit Check").assertExists()
        rule.onNodeWithText("Low-resistance path across B1").performClick()
        rule.onNodeWithText("Why am I seeing this?").assertExists()
        rule.onNodeWithText("Remove bypass wire").performClick()
        rule.onNodeWithText("Apply suggested fix?").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { assertTrue(model.state.value.circuit.wires.any { it.id=="guide-short-wire" }) }
    }

    @Test fun additionalFaultCircuitsAreDetectedInRunningApp() {
        val source=p(Kind.SOURCE,"V1",values=mapOf("voltage" to 10.0))
        val ground=p(Kind.GROUND,"GND1")
        val hot=p(Kind.RESISTOR,"R1",values=mapOf("resistance" to 10.0,"rating" to .25))
        val hotCircuit=Circuit("Hot resistor",listOf(source,ground,hot),listOf(
            w(source,0,hot,0),w(hot,1,source,1),w(source,1,ground,0)))
        val cap=p(Kind.ELECTROLYTIC,"C1",values=mapOf("maxVoltage" to 5.0))
        val capCircuit=Circuit("Overvoltage cap",listOf(source,ground,cap),listOf(
            w(source,0,cap,0),w(cap,1,source,1),w(source,1,ground,0)))
        val esp=p(Kind.ESP32_DEVKIT,"ESP1")
        val espPin=BoardRegistry.boards.getValue(esp.kind).index("IO21")
        val espCircuit=Circuit("ESP voltage",listOf(source,ground,esp),listOf(
            w(source,0,esp,espPin),w(source,1,ground,0)))
        val uno=p(Kind.ARDUINO_UNO,"UNO1")
        val unoPins=BoardRegistry.boards.getValue(uno.kind)
        val motor=p(Kind.DC_MOTOR,"M1")
        val relay=p(Kind.RELAY,"K1")
        val sensor=p(Kind.LM35_SENSOR,"TMP1")
        val boardCircuit=Circuit("Board faults",listOf(uno,motor,relay,sensor),listOf(
            w(uno,unoPins.index("D9"),motor,0),w(uno,unoPins.index("D10"),relay,0),
            w(sensor,1,uno,unoPins.index("D2"))))
        val nano=p(Kind.ARDUINO_NANO,"NANO1")
        val uartCircuit=Circuit("UART",listOf(uno,nano),listOf(
            w(uno,unoPins.index("D1"),nano,BoardRegistry.boards.getValue(nano.kind).index("D1"))))
        val load=p(Kind.RESISTOR,"R2")
        val voltmeter=p(Kind.VOLTMETER,"VM1")
        val voltCircuit=Circuit("Series voltmeter",listOf(source,ground,load,voltmeter),listOf(
            w(source,0,voltmeter,0),w(voltmeter,1,load,0),w(load,1,source,1),w(source,1,ground,0)))
        val ammeter=p(Kind.AMMETER,"A1")
        val ampCircuit=Circuit("Parallel ammeter",listOf(source,ground,ammeter),listOf(
            w(source,0,ammeter,0),w(ammeter,1,source,1),w(source,1,ground,0)))
        val ic=p(Kind.LM358,"U1")
        val icCircuit=Circuit("Unpowered op amp",listOf(ic,source,ground),listOf(
            w(source,0,ic,1),w(source,1,ground,0)))
        val cases=listOf(
            LessonTemplates.led(fault="open") to "DISCONNECTED_LOAD",
            hotCircuit to "POWER_OVERLOAD",
            capCircuit to "CAP_OVER_VOLTAGE",
            espCircuit to "GPIO_OVERVOLTAGE",
            boardCircuit to "GPIO_DIRECT_MOTOR",
            boardCircuit to "GPIO_DIRECT_RELAY",
            boardCircuit to "INVALID_ADC_PIN",
            boardCircuit to "MISSING_COMMON_GROUND",
            uartCircuit to "UART_TX_TX",
            voltCircuit to "VOLTMETER_IN_SERIES",
            ampCircuit to "AMMETER_ACROSS_SOURCE",
            icCircuit to "UNPOWERED_IC")
        for((circuit,expected) in cases) {
            rule.runOnUiThread { model().loadSample(circuit) }
            try { waitFor(expected) } catch(error:Throwable) {
                val state=model().state.value
                throw AssertionError("${circuit.name}: expected $expected; loaded ${state.circuit.name}; found ${state.intelligence.issues.map { it.ruleId }}; solve error ${state.result.error}; readings ${state.result.readings}",error)
            }
        }
    }
}
