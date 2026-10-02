package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PhaseOneAdvancedTest {
    private fun p(k:Kind,r:String)=PlacedComponent(kind=k,reference=r,x=0f,y=0f)
    private fun w(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=
        Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))

    @Test fun electromechanicalAndSensorsParticipateInDcAndTransient() {
        for(kind in ElectromechanicalParts.pinNames.keys) {
            val part=p(kind,"X1").let { if(kind==Kind.REED_SWITCH)
                it.copy(parameters=it.parameters+("field" to 1.0))
                else if(kind==Kind.DIP_SWITCH_4) it.copy(parameters=it.parameters+("switch1" to 1.0))
                else it }
            val supply=p(Kind.SOURCE,"V1").copy(parameters=mapOf("voltage" to 5.0))
            val control=p(Kind.SOURCE,"V2").copy(parameters=mapOf("voltage" to 3.3))
            val ground=p(Kind.GROUND,"G1")
            val load=p(Kind.RESISTOR,"R1").copy(parameters=mapOf("resistance" to 1000.0))
            val wires=mutableListOf(w(supply,1,ground,0))
            val usesControl=kind==Kind.SERVO_MOTOR
            val usesLoad=kind !in setOf(Kind.BUZZER,Kind.SPEAKER,Kind.SOLENOID,Kind.SERVO_MOTOR)
            if(usesControl) wires+=w(control,1,ground,0)
            when(kind) {
                Kind.BUZZER,Kind.SPEAKER,Kind.SOLENOID -> wires+=listOf(
                    w(supply,0,part,0),w(part,1,ground,0))
                Kind.SERVO_MOTOR -> wires+=listOf(w(supply,0,part,0),w(part,1,ground,0),
                    w(control,0,part,2))
                Kind.DIP_SWITCH_4,Kind.REED_SWITCH -> wires+=listOf(w(supply,0,part,0),
                    w(part,1,load,0),w(load,1,ground,0))
                Kind.DPDT_RELAY -> wires+=listOf(w(supply,0,part,0),w(part,1,ground,0),
                    w(supply,0,part,2),w(part,3,load,0),w(load,1,ground,0))
                Kind.LM35_SENSOR,Kind.HALL_SENSOR -> wires+=listOf(
                    w(supply,0,part,0),w(part,2,ground,0),w(part,1,load,0),w(load,1,ground,0))
                Kind.PHOTOTRANSISTOR -> wires+=listOf(w(supply,0,load,0),
                    w(load,1,part,0),w(part,1,ground,0))
                else -> error("Unexpected")
            }
            val circuit=Circuit(kind.title,listOf(part,supply,ground)+
                (if(usesControl) listOf(control) else emptyList())+
                (if(usesLoad) listOf(load) else emptyList()),wires)
            val dc=DcSolver().solve(circuit)
            assertNull("$kind DC ${dc.error}",dc.error)
            assertTrue("$kind DC current",dc.readings.getValue(part.id).current.isFinite())
            val transient=TransientSolver().simulate(circuit,.003,.001)
            assertNull("$kind transient ${transient.error}",transient.error)
            assertTrue("$kind transient current",
                transient.frames.last().readings.getValue(part.id).current.isFinite())
            if(kind==Kind.LM35_SENSOR)
                assertEquals(.25,dc.nodeVoltages.getValue(TerminalRef(part.id,1)),.01)
        }
    }

    @Test fun darlingtonAndThyristorsSwitchCurrent() {
        for(kind in SemiconductorSwitchParts.pinNames.keys) {
            val device=p(kind,"Q1")
            val supply=p(Kind.SOURCE,"V1").copy(parameters=mapOf("voltage" to 5.0))
            val ground=p(Kind.GROUND,"G1")
            val load=p(Kind.RESISTOR,"R1").copy(parameters=mapOf("resistance" to 330.0))
            val gate=p(Kind.RESISTOR,"R2").copy(parameters=mapOf("resistance" to 1000.0))
            val circuit=Circuit(kind.title,listOf(device,supply,ground,load,gate),listOf(
                w(supply,0,load,0),w(load,1,device,0),w(device,2,ground,0),
                w(supply,0,gate,0),w(gate,1,device,1),w(supply,1,ground,0)))
            val dc=DcSolver().solve(circuit)
            assertNull("$kind DC ${dc.error}",dc.error)
            assertTrue("$kind conducts ${dc.readings.getValue(load.id).current} "+
                "pins="+(0..2).map { dc.nodeVoltages[TerminalRef(device.id,it)] },
                abs(dc.readings.getValue(load.id).current)>.001)
            val transient=TransientSolver().simulate(circuit,.003,.001)
            assertNull("$kind transient ${transient.error}",transient.error)
            assertTrue("$kind transient conducts",
                abs(transient.frames.last().readings.getValue(load.id).current)>.001)
        }
    }
}
