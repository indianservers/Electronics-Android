package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*
import kotlin.math.abs

/** Models use actual circuit pin voltages. Environment inputs (light, field, temperature) are editable stimuli. */
internal fun stampElectromechanical(p:PlacedComponent,indices:IntArray,guess:DoubleArray,
    matrix:Array<DoubleArray>,rhs:DoubleArray,dt:Double?=null,priorCoilCurrent:Double=0.0) {
    fun v(pin:Int)=indices[pin].takeIf { it>=0 }?.let { guess[it] } ?: 0.0
    fun branch(a:Int,b:Int,g:Double,ieq:Double=0.0)=
        stampBranch(matrix,rhs,indices[a],indices[b],BranchModel(g,ieq))
    fun drive(out:Int,ground:Int,voltage:Double,resistance:Double) =
        branch(out,ground,1.0/resistance,-voltage/resistance)
    when(p.kind) {
        Kind.BUZZER -> {
            val model=ledBranchAt(v(0)-v(1),p.value("startVoltage"),p.value("resistance"))
            branch(0,1,model.conductance,model.ieq)
        }
        Kind.SPEAKER,Kind.SOLENOID -> {
            val resistance=p.value("resistance")
            if(dt==null) branch(0,1,1.0/resistance)
            else {
                val inductive=p.value("inductance")/dt
                val g=1.0/(resistance+inductive)
                branch(0,1,g,g*inductive*priorCoilCurrent)
            }
        }
        Kind.SERVO_MOTOR -> {
            val supply=v(0)-v(1)
            branch(0,1,p.value("idleCurrent")/5.0)
            branch(2,1,1e-9)
            if(supply<3.0) return
        }
        Kind.DIP_SWITCH_4 -> (0..3).forEach { channel ->
            if(p.value("switch${channel+1}")>=.5) branch(channel*2,channel*2+1,1000.0)
        }
        Kind.DPDT_RELAY -> {
            branch(0,1,1.0/p.value("coilResistance"))
            val active=abs(v(0)-v(1))>=p.value("pullInVoltage")
            branch(2,if(active) 3 else 4,1000.0)
            branch(5,if(active) 6 else 7,1000.0)
        }
        Kind.REED_SWITCH -> if(p.value("field")>=.5) branch(0,1,1000.0)
        Kind.LM35_SENSOR -> {
            branch(0,2,60e-6/5.0)
            if(v(0)-v(2)>=4.0) drive(1,2,p.value("temperatureC")*.01,.1)
            else branch(1,2,1e-9)
        }
        Kind.HALL_SENSOR -> {
            val supply=v(0)-v(2)
            branch(0,2,1.0/10000.0)
            if(supply>=3.0) drive(1,2,(supply/2.0+
                p.value("fieldMilliTesla")*p.value("sensitivity")).coerceIn(0.0,supply),50.0)
            else branch(1,2,1e-9)
        }
        Kind.PHOTOTRANSISTOR -> {
            val vce=v(0)-v(1)
            val photocurrent=p.value("currentAt100Lux")*p.value("illuminanceLux")/100.0
            val leak=1.0/p.value("darkResistance")
            if(vce>=.2) branch(0,1,leak,photocurrent)
            else if(vce>0.0) branch(0,1,leak+photocurrent/.2)
            else branch(0,1,leak)
        }
        else -> error("No electromechanical model for ${p.kind}")
    }
}

internal fun electromechanicalReading(p:PlacedComponent,volts:Map<TerminalRef,Double>,
    dt:Double?=null,priorCoilCurrent:Double=0.0):Reading {
    fun v(pin:Int)=volts[TerminalRef(p.id,pin)] ?: 0.0
    val drop=v(0)-v(1)
    val current=when(p.kind) {
        Kind.BUZZER -> ledBranchAt(drop,p.value("startVoltage"),p.value("resistance")).current(drop)
        Kind.SPEAKER,Kind.SOLENOID -> if(dt==null) drop/p.value("resistance") else {
            val l=p.value("inductance")/dt
            (drop+l*priorCoilCurrent)/(p.value("resistance")+l)
        }
        Kind.SERVO_MOTOR -> drop*p.value("idleCurrent")/5.0
        Kind.DIP_SWITCH_4 -> (0..3).sumOf { n -> if(p.value("switch${n+1}")>=.5)
            (v(n*2)-v(n*2+1))*1000.0 else 0.0 }
        Kind.DPDT_RELAY -> drop/p.value("coilResistance")
        Kind.REED_SWITCH -> if(p.value("field")>=.5) drop*1000.0 else 0.0
        Kind.LM35_SENSOR -> drop*60e-6/5.0
        Kind.HALL_SENSOR -> drop/10000.0
        Kind.PHOTOTRANSISTOR -> {
            val photo=p.value("currentAt100Lux")*p.value("illuminanceLux")/100.0
            drop/p.value("darkResistance")+if(drop>0.0) photo*(drop/.2).coerceAtMost(1.0) else 0.0
        }
        else -> error("No reading for ${p.kind}")
    }
    return Reading(drop,current,abs(drop*current))
}
