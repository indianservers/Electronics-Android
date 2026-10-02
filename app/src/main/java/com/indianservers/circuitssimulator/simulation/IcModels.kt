package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PlacedComponent
import com.indianservers.circuitssimulator.domain.TerminalRef
import kotlin.math.abs

/** Pin-level educational IC models. Unused physical pins remain electrically high impedance. */
internal fun stampAnalogIc(p:PlacedComponent,indices:IntArray,guess:DoubleArray,
                           a:Array<DoubleArray>,b:DoubleArray) {
    fun v(pin:Int)=indices[pin].takeIf { it>=0 }?.let { guess[it] } ?: 0.0
    fun branch(first:Int,second:Int,g:Double,ieq:Double=0.0)=
        stampBranch(a,b,indices[first],indices[second],BranchModel(g,ieq))
    fun drive(out:Int,ground:Int,target:Double,resistance:Double)=
        branch(out,ground,1.0/resistance,-target/resistance)
    fun input(pin:Int,ground:Int)=branch(pin,ground,1e-9)
    fun amplifier(plus:Int,minus:Int,out:Int,low:Int,high:Int,headroom:Double) {
        input(plus,low);input(minus,low)
        val lower=v(low);val upper=v(high)
        if(upper-lower<3.0) { branch(out,low,1e-9);return }
        val gain=p.value("gain");val g=1.0/p.value("outputResistance")
        val target=lower+gain*(v(plus)-v(minus))
        val floor=lower+if(p.kind==Kind.LM741) 1.5 else .05
        val ceiling=upper-headroom
        if(target<=floor || target>=ceiling) {
            drive(out,low,(target.coerceIn(floor,ceiling)-lower),p.value("outputResistance"))
        } else {
            val outNode=indices[out];val lowNode=indices[low]
            val plusNode=indices[plus];val minusNode=indices[minus]
            fun add(row:Int,col:Int,value:Double) { if(row>=0 && col>=0) a[row][col]+=value }
            add(outNode,outNode,g);add(outNode,lowNode,-g)
            add(outNode,plusNode,-g*gain);add(outNode,minusNode,g*gain)
            add(lowNode,outNode,-g);add(lowNode,lowNode,g)
            add(lowNode,plusNode,g*gain);add(lowNode,minusNode,-g*gain)
        }
    }
    when(p.kind) {
        Kind.LM358 -> {
            branch(7,3,1.0/10000.0)
            amplifier(2,1,0,3,7,1.5)
            amplifier(4,5,6,3,7,1.5)
        }
        Kind.LM741 -> {
            branch(6,3,1.0/5000.0)
            amplifier(2,1,5,3,6,1.5)
        }
        Kind.COMPARATOR -> {
            input(0,2);input(1,2);branch(4,2,1.0/10000.0)
            if(v(4)-v(2)>=2.0 && v(0)-v(1)<p.value("thresholdOffset"))
                branch(3,2,1.0/50.0)
            else branch(3,2,1e-9)
        }
        Kind.REGULATOR_7805 -> {
            val input=v(0)-v(1);val output=v(2)-v(1)
            if(input>=5.0+p.value("dropout")) {
                val g=1.0/p.value("outputResistance")
                drive(2,1,5.0,p.value("outputResistance"))
                // Load current is drawn from IN; the difference is dissipated as heat.
                branch(0,1,1e-9,(g*(5.0-output)).coerceAtLeast(0.0)+.005)
            } else {
                branch(2,1,1e-9)
                branch(0,1,1e-9)
            }
        }
        Kind.REGULATOR_LM317 -> {
            val headroom=v(2)-v(1)
            if(headroom>=p.value("dropout")) {
                val g=1.0/p.value("outputResistance")
                drive(1,0,p.value("referenceVoltage"),p.value("outputResistance"))
                branch(2,0,1e-9,(g*(p.value("referenceVoltage")-(v(1)-v(0))))
                    .coerceAtLeast(0.0)+.005)
            } else branch(1,0,1e-9)
        }
        Kind.L293D -> {
            val ground=3
            branch(15,ground,1.0/10000.0)
            input(0,ground);input(1,ground);input(6,ground)
            input(8,ground);input(9,ground);input(14,ground)
            val powered=v(15)-v(ground)>=4.5 && v(7)-v(ground)>=4.5
            listOf(Triple(0,1,2),Triple(0,6,5),Triple(8,9,10),Triple(8,14,13)).forEach { (en,signal,out) ->
                if(powered && v(en)-v(ground)>2.0) {
                    if(v(signal)-v(ground)>2.0) branch(out,7,1.0/5.0)
                    else branch(out,ground,1.0/5.0)
                } else branch(out,ground,1e-9)
            }
        }
        Kind.ULN2003 -> {
            (0..6).forEach { channel ->
                input(channel,7)
                val out=15-channel
                if(v(channel)-v(7)>2.0) branch(out,7,1.0/2.0)
                else branch(out,7,1e-9)
            }
            branch(8,7,1e-9) // flyback COM is exposed; clamp diode is omitted.
        }
        else -> error("No analog IC model for ${p.kind}")
    }
}

internal fun analogIcReading(p:PlacedComponent,volts:Map<TerminalRef,Double>):Reading {
    fun v(pin:Int)=volts[TerminalRef(p.id,pin)] ?: 0.0
    val (supply,ground)=when(p.kind) {
        Kind.LM358 -> 7 to 3;Kind.LM741 -> 6 to 3;Kind.COMPARATOR -> 4 to 2
        Kind.REGULATOR_7805 -> 0 to 1;Kind.REGULATOR_LM317 -> 2 to 0
        Kind.L293D -> 7 to 3;Kind.ULN2003 -> 8 to 7
        else -> error("No analog IC reading")
    }
    val drop=v(supply)-v(ground)
    val current=when(p.kind) {
        Kind.LM358,Kind.COMPARATOR,Kind.L293D -> drop/10000.0
        Kind.LM741 -> drop/5000.0
        Kind.REGULATOR_7805,Kind.REGULATOR_LM317 -> if(drop>3.0) .005 else 0.0
        else -> 0.0
    }
    return Reading(drop,current,abs(drop*current))
}
