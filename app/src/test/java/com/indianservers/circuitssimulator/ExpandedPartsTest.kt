package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ExpandedPartsTest {
    private fun part(kind:Kind,reference:String)=PlacedComponent(kind=kind,reference=reference,x=0f,y=0f)
    private fun wire(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=
        Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))

    @Test fun controlledVoltageSourceAmplifiesARealLoad() {
        val supply=part(Kind.SOURCE,"V1").copy(parameters=mapOf("voltage" to 2.0))
        val vcvs=part(Kind.VCVS,"E1")
        val load=part(Kind.RESISTOR,"R1").copy(parameters=mapOf("resistance" to 1000.0))
        val ground=part(Kind.GROUND,"GND1")
        val circuit=Circuit("VCVS",listOf(supply,vcvs,load,ground),listOf(
            wire(supply,0,vcvs,0),wire(supply,1,ground,0),wire(vcvs,1,ground,0),
            wire(vcvs,2,load,0),wire(vcvs,3,ground,0),wire(load,1,ground,0)))
        val result=DcSolver().solve(circuit)
        assertNull(result.error)
        assertEquals(4.0,result.nodeVoltages.getValue(TerminalRef(load.id,0)),1e-4)
        assertEquals(.004,abs(result.readings.getValue(load.id).current),1e-6)
    }

    @Test fun currentControlledSourcesSenseControlBranch() {
        val supply=part(Kind.SOURCE,"V1")
        val sensor=part(Kind.RESISTOR,"R1").copy(parameters=mapOf("resistance" to 1000.0))
        val load=part(Kind.RESISTOR,"R2").copy(parameters=mapOf("resistance" to 1000.0))
        val ground=part(Kind.GROUND,"GND1")
        for(kind in listOf(Kind.CCCS,Kind.CCVS)) {
            val controlled=part(kind,"X1")
            val circuit=Circuit(kind.title,listOf(supply,sensor,controlled,load,ground),listOf(
                wire(supply,0,sensor,0),wire(sensor,1,controlled,0),wire(controlled,1,ground,0),
                wire(controlled,2,load,0),wire(controlled,3,ground,0),
                wire(load,1,ground,0),wire(supply,1,ground,0)))
            val dc=DcSolver().solve(circuit)
            assertNull(dc.error)
            val output=dc.nodeVoltages.getValue(TerminalRef(load.id,0))
            if(kind==Kind.CCVS) assertEquals(.9,output,1e-3)
            else assertEquals(-18.0,output,1e-3)
            assertNull(TransientSolver().simulate(circuit,.002,.001).error)
            assertNotNull(AcSolver().solve(circuit,100.0))
        }
    }

    @Test fun zenerClampsAndMotorAccelerates() {
        val supply=part(Kind.SOURCE,"V1")
        val resistor=part(Kind.RESISTOR,"R1").copy(parameters=mapOf("resistance" to 100.0))
        val zener=part(Kind.ZENER,"ZD1")
        val ground=part(Kind.GROUND,"GND1")
        val circuit=Circuit("Zener",listOf(supply,resistor,zener,ground),listOf(
            wire(supply,0,resistor,0),wire(resistor,1,zener,1),wire(zener,0,ground,0),
            wire(supply,1,ground,0)))
        val result=DcSolver().solve(circuit)
        assertNull(result.error)
        val clamped=result.nodeVoltages.getValue(TerminalRef(zener.id,1))
        assertTrue(clamped in 5.0..6.0)

        val motor=part(Kind.DC_MOTOR,"M1").copy(parameters=mapOf("inertia" to 1e-5))
        val motorCircuit=Circuit("Motor",listOf(supply,motor,ground),listOf(
            wire(supply,0,motor,0),wire(motor,1,ground,0),wire(supply,1,ground,0)))
        val trace=TransientSolver().simulate(motorCircuit,.2,.001)
        assertNull(trace.error)
        assertTrue(trace.checkpoint!!.motorSpeeds.getValue(motor.id)>0.0)
        assertEquals(.3,trace.frames.last().readings.getValue(motor.id).current,1e-3)
        assertEquals(30.0,trace.checkpoint!!.motorSpeeds.getValue(motor.id),1.0)
    }

    @Test fun flipFlopAndCounterRespondToClockEdges() {
        val clock=part(Kind.CLOCK,"CLK1")
        val data=part(Kind.SOURCE,"V1").copy(parameters=mapOf("voltage" to 3.3))
        val flip=part(Kind.D_FLIP_FLOP,"U1")
        val counter=part(Kind.COUNTER_4,"U2")
        val ground=part(Kind.GROUND,"GND1")
        val circuit=Circuit("Logic",listOf(clock,data,flip,counter,ground),listOf(
            wire(clock,0,flip,1),wire(clock,0,counter,0),wire(data,0,flip,0),
            wire(data,1,ground,0)))
        val result=CanvasDigitalSimulator().simulate(circuit,.26,.001)
        assertNull(result.error)
        assertTrue(result.frames.any { it.digitalStates[TerminalRef(flip.id,2)]==LogicState.HIGH })
        val q0=result.frames.map { it.digitalStates[TerminalRef(counter.id,1)] }.toSet()
        assertTrue(LogicState.LOW in q0 && LogicState.HIGH in q0)

        val simulator=CanvasDigitalSimulator()
        val first=simulator.session(circuit).advance(.15,.001)
        assertNull(first.error)
        assertEquals(2,first.checkpoint!!.digitalCounters[counter.id])
        val resumed=simulator.session(circuit,first.checkpoint!!.timeSeconds)
            .advance(.10,.001,first.checkpoint)
        assertNull(resumed.error)
        assertEquals(3,resumed.checkpoint!!.digitalCounters[counter.id])
    }

    @Test fun fourChannelCsvIncludesEachProbe() {
        val sample=TransientFrame(.1,mapOf(TerminalRef("a",0) to 1.0,
            TerminalRef("b",0) to 2.0,TerminalRef("c",0) to 3.0,
            TerminalRef("d",0) to 4.0),emptyMap())
        val output=AnalysisCsv.scope(listOf(sample),listOf("a","b","c","d").map { TerminalRef(it,0) })
        assertTrue(output.contains("ch4_volts"))
        assertEquals(listOf(.1,1.0,2.0,3.0,4.0),output.lines()[1].split(',').map(String::toDouble))
    }

    @Test fun adcAndDacCrossTheAnalogDigitalBoundary() {
        val supply=part(Kind.SOURCE,"V1").copy(parameters=mapOf("voltage" to 2.2))
        val adc=part(Kind.ADC_2,"U1")
        val dac=part(Kind.DAC_2,"U2")
        val load=part(Kind.RESISTOR,"R1").copy(parameters=mapOf("resistance" to 1000.0))
        val ground=part(Kind.GROUND,"GND1")
        val circuit=Circuit("Converters",listOf(supply,adc,dac,load,ground),listOf(
            wire(supply,0,adc,0),wire(supply,1,ground,0),wire(adc,1,ground,0),
            wire(adc,2,dac,0),wire(adc,3,dac,1),wire(dac,2,load,0),
            wire(dac,3,ground,0),wire(load,1,ground,0)))
        val result=CanvasDigitalSimulator().simulate(circuit,.02,.0002)
        assertNull(result.error)
        val last=result.frames.last()
        assertEquals(LogicState.LOW,last.digitalStates[TerminalRef(adc.id,2)])
        assertEquals(LogicState.HIGH,last.digitalStates[TerminalRef(adc.id,3)])
        assertTrue((last.nodeVoltages[TerminalRef(dac.id,2)] ?: 0.0) in 2.0..2.3)
    }

    @Test fun timerResetForcesLowAndTransformerTransfersAc() {
        val timer=part(Kind.TIMER_555,"U1")
        val ground=part(Kind.GROUND,"GND1")
        val load=part(Kind.RESISTOR,"R1")
        val clockCircuit=Circuit("Timer",listOf(timer,load,ground),listOf(
            wire(timer,0,ground,0),wire(timer,1,ground,0),wire(timer,2,load,0),wire(load,1,ground,0)))
        val timed=CanvasDigitalSimulator().simulate(clockCircuit,.16,.001)
        assertNull(timed.error)
        assertTrue(timed.frames.all { it.digitalStates[TerminalRef(timer.id,2)]==LogicState.LOW })

        val source=part(Kind.SOURCE,"V1")
        val transformer=part(Kind.TRANSFORMER,"T1")
        val acLoad=part(Kind.RESISTOR,"R2").copy(parameters=mapOf("resistance" to 10000.0))
        val acCircuit=Circuit("Transformer",listOf(source,transformer,acLoad,ground),listOf(
            wire(source,0,transformer,0),wire(source,1,ground,0),wire(transformer,1,ground,0),
            wire(transformer,2,acLoad,0),wire(transformer,3,ground,0),wire(acLoad,1,ground,0)))
        val ac=AcSolver().solve(acCircuit,100.0)
        assertNotNull(ac)
        val gain=ac!!.nodeVoltages.getValue(TerminalRef(acLoad.id,0)).magnitude
        assertTrue(gain in 1.8..2.1)
    }

    @Test fun spdtRoutesSelectedThrow() {
        val source=part(Kind.SOURCE,"V1")
        val selector=part(Kind.SPDT_SWITCH,"SW1")
        val a=part(Kind.RESISTOR,"R1")
        val b=part(Kind.RESISTOR,"R2")
        val ground=part(Kind.GROUND,"GND1")
        val wires=listOf(wire(source,0,selector,0),wire(selector,1,a,0),wire(selector,2,b,0),
            wire(a,1,ground,0),wire(b,1,ground,0),wire(source,1,ground,0))
        val closed=DcSolver().solve(Circuit("A",listOf(source,selector,a,b,ground),wires))
        val open=DcSolver().solve(Circuit("B",listOf(source,selector.copy(closed=false),a,b,ground),wires))
        assertNull(closed.error);assertNull(open.error)
        assertTrue(closed.nodeVoltages.getValue(TerminalRef(a.id,0))>8.0)
        assertTrue(open.nodeVoltages.getValue(TerminalRef(b.id,0))>8.0)
    }

    @Test fun relayAndMultiChannelLedsCarryCurrent() {
        val source=part(Kind.SOURCE,"V1")
        val relay=part(Kind.RELAY,"K1")
        val load=part(Kind.RESISTOR,"R1")
        val ground=part(Kind.GROUND,"GND1")
        val circuit=Circuit("Relay",listOf(source,relay,load,ground),listOf(
            wire(source,0,relay,0),wire(source,1,relay,1),wire(source,0,relay,2),
            wire(relay,3,load,0),wire(load,1,ground,0),wire(source,1,ground,0)))
        val result=DcSolver().solve(circuit)
        assertNull(result.error)
        assertTrue(result.nodeVoltages.getValue(TerminalRef(load.id,0))>8.0)

        for(kind in listOf(Kind.RGB_LED,Kind.SEVEN_SEGMENT)) {
            val led=part(kind,"D1")
            val ledCircuit=Circuit(kind.title,listOf(source,led,ground),listOf(
                wire(source,0,led,0),wire(source,1,ground,0),
                wire(led,led.terminalCount-1,ground,0)))
            val solved=DcSolver().solve(ledCircuit)
            assertNull(solved.error)
            assertTrue(solved.readings.getValue(led.id).current>.001)
        }
    }

    @Test fun phaseThreeSamplesProduceOutput() {
        val rgb=SampleCircuits.timerCounterRgb()
        val rgbResult=CanvasDigitalSimulator().simulate(rgb,.45,.001)
        assertNull(rgbResult.error)
        val led=rgb.components.first { it.kind==Kind.RGB_LED }
        assertTrue(rgbResult.frames.any { it.readings.getValue(led.id).current>.001 })

        val transformer=SampleCircuits.transformerDemo()
        val transformerResult=TransientSolver().simulate(transformer,.06,.0001)
        assertNull(transformerResult.error)
        val load=transformer.components.first { it.kind==Kind.RESISTOR }
        assertTrue(transformerResult.frames.any { abs(it.readings.getValue(load.id).voltage)>.5 })
    }

    @Test fun pendingLogicTransitionSurvivesSessionRestart() {
        val clock=part(Kind.CLOCK,"CLK1").copy(parameters=mapOf("frequency" to 1.0))
        val gate=part(Kind.NOT_GATE,"U1").copy(parameters=mapOf("delay" to .05))
        val ground=part(Kind.GROUND,"GND1")
        val circuit=Circuit("Delayed gate",listOf(clock,gate,ground),listOf(wire(clock,0,gate,0)))
        val simulator=CanvasDigitalSimulator()
        val first=simulator.session(circuit).advance(.01,.001)
        assertNull(first.error)
        assertTrue(first.checkpoint!!.digitalEvents.isNotEmpty())
        val resumed=simulator.session(circuit,first.checkpoint!!.timeSeconds)
            .advance(.06,.001,first.checkpoint)
        assertNull(resumed.error)
        assertEquals(LogicState.LOW,resumed.frames.last().digitalStates[TerminalRef(gate.id,1)])
    }
}
