package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.guide.LessonTemplates
import com.indianservers.circuitssimulator.intelligence.*
import com.indianservers.circuitssimulator.simulation.*
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import org.junit.Assert.*
import org.junit.Test

class CircuitIntelligenceTest {
    private fun p(kind:Kind,ref:String,parameters:Map<String,Double> = emptyMap())=
        PlacedComponent(kind=kind,reference=ref,x=0f,y=0f,parameters=parameters)
    private fun w(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=
        Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))
    private fun report(c:Circuit,solved:Boolean=true,mode:MeterMode?=null,
                       plus:TerminalRef?=null,common:TerminalRef?=null)=
        CircuitIntelligence.analyze(IntelligenceInput(c,if(solved) DcSolver().solve(c) else null,
            running=solved,meterMode=mode,meterPlus=plus,meterCommon=common))
    private fun codes(r:IntelligenceReport)=r.issues.map { it.ruleId }.toSet()

    @Test fun validLedDoesNotReceiveMissingResistorOrShortWarning() {
        val result=report(LessonTemplates.led())
        assertFalse("${result.issues}","LED_NO_RESISTOR" in codes(result))
        assertFalse("${result.issues}","SOURCE_SHORT" in codes(result))
        assertTrue(result.insights.any { it.id.startsWith("LED_SERIES:") })
    }

    @Test fun directShortAndMissingGroundAreRootCauses() {
        val short=report(LessonTemplates.led(fault="short"))
        assertTrue("SOURCE_SHORT" in codes(short))
        assertFalse("SOLVER_FAILURE" in codes(short))
        val source=p(Kind.SOURCE,"V1");val resistor=p(Kind.RESISTOR,"R1")
        val noGround=report(Circuit("No ground",listOf(source,resistor),listOf(w(source,0,resistor,0),
            w(resistor,1,source,1))))
        assertTrue("NO_GROUND" in codes(noGround))
        assertFalse("SOLVER_FAILURE" in codes(noGround))
    }

    @Test fun openAndReversedLedHaveConcreteEvidence() {
        assertTrue("DISCONNECTED_LOAD" in codes(report(LessonTemplates.led(fault="open"))))
        val reversed=report(LessonTemplates.led(fault="reversed_led"))
        val issue=reversed.issues.first { it.ruleId=="LED_REVERSED" }
        assertTrue(issue.evidence.any { it.label.contains("Anode") })
        assertTrue(issue.fixes.any { it is SmartFix.ReverseLed })
    }

    @Test fun directLedGetsCalculatedResistorSuggestion() {
        val source=p(Kind.BATTERY,"B1");val led=p(Kind.LED,"D1");val ground=p(Kind.GROUND,"GND1")
        val c=Circuit("Direct LED",listOf(source,led,ground),listOf(w(source,0,led,0),
            w(led,1,source,1),w(source,1,ground,0)))
        val issue=report(c).issues.first { it.ruleId=="LED_NO_RESISTOR" }
        assertTrue(issue.evidence.any { it.label=="Target current" })
        assertTrue("$issue",issue.fixes.any { it is SmartFix.InsertLedResistor })
    }

    @Test fun electricalStressUsesConfiguredRatings() {
        val source=p(Kind.SOURCE,"V1",mapOf("voltage" to 10.0))
        val resistor=p(Kind.RESISTOR,"R1",mapOf("resistance" to 10.0,"rating" to .25))
        val ground=p(Kind.GROUND,"GND1")
        val hot=Circuit("Hot",listOf(source,resistor,ground),listOf(w(source,0,resistor,0),
            w(resistor,1,source,1),w(source,1,ground,0)))
        assertTrue("POWER_OVERLOAD" in codes(report(hot)))
        val cap=p(Kind.ELECTROLYTIC,"C1",mapOf("maxVoltage" to 10.0))
        val fake=DcResult(readings=mapOf(cap.id to Reading(-12.0,0.0,0.0)))
        val capReport=CircuitIntelligence.analyze(IntelligenceInput(Circuit("Cap",listOf(cap,ground),emptyList()),fake))
        assertTrue("CAP_OVER_VOLTAGE" in codes(capReport))
    }

    @Test fun boardVoltageAndCapabilityUseRegistryMetadata() {
        val board=p(Kind.ESP32_DEVKIT,"ESP1")
        val ref=TerminalRef(board.id,BoardRegistry.boards.getValue(board.kind).index("IO21"))
        val ground=p(Kind.GROUND,"GND1")
        val source=p(Kind.SOURCE,"V1",mapOf("voltage" to 5.0))
        val c=Circuit("GPIO",listOf(board,source,ground),listOf(w(source,0,board,ref.index),
            w(source,1,ground,0)))
        val solved=DcSolver().solve(c)
        val report=CircuitIntelligence.analyze(IntelligenceInput(c,solved))
        assertTrue("$solved ${report.issues}","GPIO_OVERVOLTAGE" in codes(report))
        val invalid=board.copy(parameters=mapOf("gpio_VP" to 2.0))
        val vp=BoardRegistry.boards.getValue(board.kind).index("VP")
        val c2=Circuit("Invalid output",listOf(invalid,source,ground),listOf(w(source,0,invalid,vp),
            w(source,1,ground,0)))
        assertTrue("INVALID_GPIO_MODE" in codes(report(c2,false)))
    }

    @Test fun directMotorRelayUartAndCommonGroundAreTopologyBased() {
        val a=p(Kind.ARDUINO_UNO,"UNO1")
        val b=p(Kind.ARDUINO_NANO,"NANO1")
        val motor=p(Kind.DC_MOTOR,"M1")
        val relay=p(Kind.RELAY,"K1")
        val sensor=p(Kind.LM35_SENSOR,"TMP1")
        val uno=BoardRegistry.boards.getValue(a.kind);val nano=BoardRegistry.boards.getValue(b.kind)
        val c=Circuit("Board faults",listOf(a,b,motor,relay,sensor),listOf(
            w(a,uno.index("D9"),motor,0),w(a,uno.index("D10"),relay,0),
            w(a,uno.index("D1"),b,nano.index("D1")),w(sensor,1,a,uno.index("A0"))))
        val issueCodes=codes(report(c,false))
        assertTrue("$issueCodes","GPIO_DIRECT_MOTOR" in issueCodes)
        assertTrue("$issueCodes","GPIO_DIRECT_RELAY" in issueCodes)
        assertTrue("$issueCodes","UART_TX_TX" in issueCodes)
        assertTrue("$issueCodes","MISSING_COMMON_GROUND" in issueCodes)
    }

    @Test fun meterMisuseAndFloatingInputUseActualConnectionsAndMode() {
        val source=p(Kind.SOURCE,"V1");val meter=p(Kind.AMMETER,"A1");val ground=p(Kind.GROUND,"GND1")
        val c=Circuit("Bad meter",listOf(source,meter,ground),listOf(w(source,0,meter,0),
            w(meter,1,source,1),w(source,1,ground,0)))
        assertTrue("AMMETER_ACROSS_SOURCE" in codes(report(c)))
        assertTrue("OHMMETER_POWERED" in codes(report(c,mode=MeterMode.RESISTANCE,
            plus=TerminalRef(source.id,0),common=TerminalRef(source.id,1))))
        val board=p(Kind.RASPBERRY_PICO,"PICO1")
        val pin=BoardRegistry.boards.getValue(board.kind).index("GP1")
        val resistor=p(Kind.RESISTOR,"R1")
        val floating=Circuit("Floating",listOf(board,resistor),listOf(w(board,pin,resistor,0)))
        val fake=DcResult(digitalStates=mapOf(TerminalRef(board.id,pin) to LogicState.UNKNOWN))
        assertTrue("FLOATING_GPIO" in codes(CircuitIntelligence.analyze(IntelligenceInput(floating,fake))))
    }

    @Test fun dividerIsRecognizedAndIndependentCircuitsAreNotSourceConflicts() {
        val source=p(Kind.SOURCE,"V1",mapOf("voltage" to 10.0))
        val r1=p(Kind.RESISTOR,"R1",mapOf("resistance" to 1000.0))
        val r2=p(Kind.RESISTOR,"R2",mapOf("resistance" to 1000.0))
        val ground=p(Kind.GROUND,"GND1")
        val divider=Circuit("Divider",listOf(source,r1,r2,ground),listOf(w(source,0,r1,0),
            w(r1,1,r2,0),w(r2,1,source,1),w(source,1,ground,0)))
        val result=report(divider)
        assertTrue(result.insights.any { it.id.startsWith("DIVIDER:") })
        assertFalse("SOURCE_CONFLICT" in codes(result))
    }

    @Test fun hundredComponentCircuitAnalyzesWithoutCanvasScaleDelay() {
        val source=p(Kind.SOURCE,"V1");val ground=p(Kind.GROUND,"GND1")
        val resistors=(1..100).map { index -> p(Kind.RESISTOR,"R$index") }
        val wires=mutableListOf(w(source,0,resistors.first(),0),w(source,1,ground,0))
        resistors.zipWithNext().forEach { (a,b) -> wires+=w(a,1,b,0) }
        wires+=w(resistors.last(),1,source,1)
        val circuit=Circuit("100 components",listOf(source,ground)+resistors,wires)
        val report=CircuitIntelligence.analyze(IntelligenceInput(circuit,null))
        println("Phase 3 102-component / ${wires.size}-wire analysis: ${report.durationMillis} ms")
        assertTrue("${report.durationMillis} ms",report.durationMillis<2000)
    }

    @Test fun shortThroughClosedSwitchIsDetectedButOpenSwitchIsNot() {
        val source=p(Kind.SOURCE,"V1");val ground=p(Kind.GROUND,"GND1")
        val closed=p(Kind.SWITCH,"S1")
        val base=listOf(w(source,0,closed,0),w(closed,1,source,1),w(source,1,ground,0))
        assertTrue("SOURCE_SHORT" in codes(report(Circuit("Closed",listOf(source,closed,ground),base))))
        val open=closed.copy(closed=false)
        assertFalse("SOURCE_SHORT" in codes(report(Circuit("Open",listOf(source,open,ground),base))))
    }

    @Test fun reversedCapacitorAndVoltageStressAreIndependentlyDetected() {
        val cap=p(Kind.ELECTROLYTIC,"C1",mapOf("maxVoltage" to 16.0))
        val ground=p(Kind.GROUND,"GND1")
        val c=Circuit("Cap",listOf(cap,ground),emptyList())
        val reversed=DcResult(readings=mapOf(cap.id to Reading(-5.0,0.0,0.0)))
        assertTrue("CAP_REVERSED" in codes(CircuitIntelligence.analyze(IntelligenceInput(c,reversed))))
        val safe=DcResult(readings=mapOf(cap.id to Reading(5.0,0.0,0.0)))
        val safeCodes=codes(CircuitIntelligence.analyze(IntelligenceInput(c,safe)))
        assertFalse("CAP_REVERSED" in safeCodes)
        assertFalse("CAP_OVER_VOLTAGE" in safeCodes)
    }

    @Test fun seriesVoltmeterIsFlaggedButParallelVoltmeterIsNot() {
        val source=p(Kind.SOURCE,"V1");val load=p(Kind.RESISTOR,"R1")
        val meter=p(Kind.VOLTMETER,"VM1");val ground=p(Kind.GROUND,"GND1")
        val series=Circuit("Series meter",listOf(source,load,meter,ground),listOf(
            w(source,0,meter,0),w(meter,1,load,0),w(load,1,source,1),w(source,1,ground,0)))
        assertTrue("VOLTMETER_IN_SERIES" in codes(report(series,false)))
        val parallel=Circuit("Parallel meter",listOf(source,load,meter,ground),listOf(
            w(source,0,load,0),w(load,1,source,1),w(meter,0,load,0),w(meter,1,load,1),
            w(source,1,ground,0)))
        assertFalse("VOLTMETER_IN_SERIES" in codes(report(parallel,false)))
    }

    @Test fun invalidAdcAndPwmAreGroundedInPinMetadata() {
        val board=p(Kind.ARDUINO_UNO,"UNO1",mapOf("gpio_D2" to 3.0))
        val sensor=p(Kind.LM35_SENSOR,"TMP1")
        val def=BoardRegistry.boards.getValue(board.kind)
        val circuit=Circuit("Pins",listOf(board,sensor),listOf(w(sensor,1,board,def.index("D2"))))
        val found=codes(report(circuit,false))
        assertTrue("$found","INVALID_ADC_PIN" in found)
        assertTrue("$found","INVALID_PWM_PIN" in found)
    }

    @Test fun opposingOutputsAndUnpoweredIcAreReported() {
        val first=p(Kind.ARDUINO_UNO,"UNO1",mapOf("gpio_D9" to 1.0))
        val second=p(Kind.ARDUINO_NANO,"NANO1",mapOf("gpio_D9" to 2.0))
        val ic=p(Kind.LM358,"U1")
        val a=BoardRegistry.boards.getValue(first.kind).index("D9")
        val b=BoardRegistry.boards.getValue(second.kind).index("D9")
        val c=Circuit("Logic",listOf(first,second,ic),listOf(w(first,a,second,b),w(first,a,ic,1)))
        val found=codes(report(c,false))
        assertTrue("$found","OUTPUT_CONTENTION" in found)
        assertTrue("$found","UNPOWERED_IC" in found)
    }

    @Test fun conflictingIdealSourcesAndFloatingIslandAreSeparatedFromIndependentSources() {
        val a=p(Kind.SOURCE,"V1",mapOf("voltage" to 5.0,"internalResistance" to 0.0))
        val b=p(Kind.SOURCE,"V2",mapOf("voltage" to 9.0,"internalResistance" to 0.0))
        val g=p(Kind.GROUND,"GND1")
        val parallel=Circuit("Conflict",listOf(a,b,g),listOf(w(a,0,b,0),w(a,1,b,1),w(a,1,g,0)))
        assertTrue("SOURCE_CONFLICT" in codes(report(parallel,false)))
        val r=p(Kind.RESISTOR,"R1")
        val floating=Circuit("Island",listOf(a,b,g,r),listOf(w(a,1,g,0),w(b,0,r,0),w(r,1,b,1)))
        assertTrue("FLOATING_ISLAND" in codes(report(floating,false)))
    }

    @Test fun unconnectedSourceAndLedOvercurrentUseActualModelSettings() {
        val source=p(Kind.SOURCE,"V1",mapOf("voltage" to 9.0))
        val resistor=p(Kind.RESISTOR,"R1",mapOf("resistance" to 10.0))
        val led=p(Kind.LED,"D1")
        val g=p(Kind.GROUND,"GND1")
        val dangling=Circuit("Dangling",listOf(source,g),listOf(w(source,1,g,0)))
        assertTrue("SOURCE_UNCONNECTED" in codes(report(dangling,false)))
        val c=Circuit("LED overcurrent",listOf(source,resistor,led,g),listOf(
            w(source,0,resistor,0),w(resistor,1,led,0),w(led,1,source,1),w(source,1,g,0)))
        val fake=DcResult(readings=mapOf(led.id to Reading(2.0,.05,.1)))
        val issue=CircuitIntelligence.analyze(IntelligenceInput(c,fake)).issues
            .first { it.ruleId=="LED_OVERCURRENT" }
        assertTrue(issue.fixes.any { it is SmartFix.SetValue })
    }
}
