package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import org.junit.Assert.*
import org.junit.Test

class PhaseOneIcTest {
    private fun p(k:Kind,r:String)=PlacedComponent(kind=k,reference=r,x=0f,y=0f)
    private fun w(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=
        Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))

    @Test fun allAnalogIcsDriveARealLoad() {
        val kinds=listOf(Kind.LM358,Kind.LM741,Kind.COMPARATOR,Kind.REGULATOR_7805,
            Kind.REGULATOR_LM317,Kind.L293D,Kind.ULN2003)
        for(kind in kinds) {
            val chip=p(kind,"U1")
            val power=p(Kind.SOURCE,"V1").copy(parameters=mapOf("voltage" to 9.0))
            val logic=p(Kind.SOURCE,"V2").copy(parameters=mapOf("voltage" to 5.0))
            val load=p(Kind.RESISTOR,"R1").copy(parameters=mapOf("resistance" to 1000.0))
            val ground=p(Kind.GROUND,"G1")
            val wires=mutableListOf(w(power,1,ground,0),w(logic,1,ground,0))
            var measured=0
            when(kind) {
                Kind.LM358 -> {
                    wires+=listOf(w(power,0,chip,7),w(chip,3,ground,0),
                        w(logic,0,chip,2),w(chip,1,ground,0),
                        w(chip,0,load,0),w(load,1,ground,0))
                    measured=0
                }
                Kind.LM741 -> {
                    wires+=listOf(w(power,0,chip,6),w(chip,3,ground,0),
                        w(logic,0,chip,2),w(chip,1,ground,0),
                        w(chip,5,load,0),w(load,1,ground,0))
                    measured=5
                }
                Kind.COMPARATOR -> {
                    wires+=listOf(w(logic,0,chip,4),w(chip,2,ground,0),
                        w(chip,0,ground,0),w(logic,0,chip,1),
                        w(logic,0,load,0),w(load,1,chip,3))
                    measured=3
                }
                Kind.REGULATOR_7805 -> {
                    wires+=listOf(w(power,0,chip,0),w(chip,1,ground,0),
                        w(chip,2,load,0),w(load,1,ground,0))
                    measured=2
                }
                Kind.REGULATOR_LM317 -> {
                    wires+=listOf(w(power,0,chip,2),w(chip,0,ground,0),
                        w(chip,1,load,0),w(load,1,ground,0))
                    measured=1
                }
                Kind.L293D -> {
                    wires+=listOf(w(power,0,chip,7),w(logic,0,chip,15),
                        w(chip,3,ground,0),w(logic,0,chip,0),w(logic,0,chip,1),
                        w(chip,2,load,0),w(load,1,ground,0))
                    measured=2
                }
                Kind.ULN2003 -> {
                    wires+=listOf(w(chip,7,ground,0),w(logic,0,chip,0),
                        w(logic,0,load,0),w(load,1,chip,15))
                    measured=15
                }
                else -> error("Unexpected")
            }
            val circuit=Circuit(kind.title,listOf(chip,power,logic,load,ground),wires)
            val dc=DcSolver().solve(circuit)
            assertNull("$kind DC ${dc.error}",dc.error)
            val output=dc.nodeVoltages.getValue(TerminalRef(chip.id,measured))
            if(kind in setOf(Kind.COMPARATOR,Kind.ULN2003)) assertTrue("$kind sink $output",output<1.0)
            else assertTrue("$kind output $output",output>1.0)
            val transient=TransientSolver().simulate(circuit,.002,.001)
            assertNull("$kind transient ${transient.error}",transient.error)
        }
    }

    @Test fun digitalIcsNeedPowerAndProduceOutputStates() {
        val supply=p(Kind.SOURCE,"V1").copy(parameters=mapOf("voltage" to 3.3))
        val ground=p(Kind.GROUND,"G1")
        val high=p(Kind.LOGIC_INPUT,"IN1").copy(parameters=mapOf("state" to 1.0))
        val low=p(Kind.LOGIC_INPUT,"IN2").copy(parameters=mapOf("state" to 0.0))
        val clock=p(Kind.CLOCK,"CLK1")
        val counter=p(Kind.COUNTER_CD4017,"U1")
        val counterCircuit=Circuit("CD4017",listOf(supply,ground,high,low,clock,counter),listOf(
            w(supply,0,counter,15),w(supply,1,ground,0),w(counter,7,ground,0),
            w(low,0,counter,12),w(low,0,counter,14),w(clock,0,counter,13)))
        val result=CanvasDigitalSimulator().simulate(counterCircuit,.003,.0002)
        assertNull(result.error)
        assertEquals(LogicState.HIGH,result.frames.last().digitalStates[TerminalRef(counter.id,1)])
        assertEquals(LogicState.LOW,result.frames.last().digitalStates[TerminalRef(counter.id,2)])

        val shift=p(Kind.SHIFT_74HC595,"U2")
        val shiftCircuit=Circuit("74HC595",listOf(supply,ground,high,low,clock,shift),listOf(
            w(supply,0,shift,15),w(supply,1,ground,0),w(shift,7,ground,0),
            w(high,0,shift,9),w(high,0,shift,13),w(low,0,shift,12),
            w(clock,0,shift,10),w(clock,0,shift,11)))
        val shifted=CanvasDigitalSimulator().simulate(shiftCircuit,.12,.001)
        assertNull(shifted.error)
        assertEquals(LogicState.HIGH,shifted.frames.last().digitalStates[TerminalRef(shift.id,14)])
    }
}
