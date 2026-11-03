package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*
import kotlin.math.*

/** Nominal 25 C macromodels. Currents are positive into each physical package pin. */
internal val datasheetIcs=setOf(Kind.TIMER_555,Kind.LM358,Kind.L293D)

internal fun timerLatch(v:DoubleArray,previous:Boolean):Boolean = when {
    v[7]-v[0] !in 4.5..16.0 || v[3]-v[0]<.7 -> false
    v[1]-v[0] < (v[4]-v[0])*.5 -> true // Trigger has priority over threshold.
    v[5]>v[4] -> false
    else -> previous
}

internal fun lm358Valid(v:DoubleArray)=v[7]-v[3] in 3.0..30.0 &&
    listOf(1,2,4,5).all { v[it]>=v[3]-1e-6 && v[it]<=v[7]-1.5+1e-6 }

/** Internal dominant-pole voltage, relative to V-. History is committed only after convergence. */
internal fun lm358Targets(p:PlacedComponent,v:DoubleArray,dt:Double=0.0,
                          previous:List<Double> = listOf(0.0,0.0)):List<Double> {
    val supply=(v[7]-v[3]).coerceAtLeast(0.0)
    return listOf(2 to 1,4 to 5).mapIndexed { channel,(plus,minus) ->
        val dc=p.value("gain")*(v[plus]-v[minus]+p.value("inputOffset"))
        val prior=previous[channel]
        val k=dt*2*PI*p.value("gbw")/p.value("gain")
        val pole=if(dt>0) (prior+k*dc)/(1+k) else dc
        val slew=if(dt>0) pole.coerceIn(prior-p.value("slewRate")*dt,prior+p.value("slewRate")*dt) else pole
        slew.coerceIn(min(.005,supply),max(min(.005,supply),supply-1.5))
    }
}

internal fun datasheetIcCurrents(p:PlacedComponent,v:DoubleArray,timerHigh:Boolean=false,
                                 dt:Double=0.0,lmPrevious:List<Double> = listOf(0.0,0.0)):DoubleArray {
    val amps=DoubleArray(v.size)
    fun flow(from:Int,to:Int,current:Double) { amps[from]+=current;amps[to]-=current }
    fun resistor(a:Int,b:Int,r:Double)=flow(a,b,(v[a]-v[b])/r)
    fun diode(a:Int,b:Int) {
        val x=v[a]-v[b]-.7
        val rounded=if(x>=0) (x+hypot(x,.001))*.5 else .5e-6/(hypot(x,.001)-x)
        flow(a,b,rounded) // smooth 1.3 V / 600 mA clamp fit, avoids a singular off-state knee
    }
    when(p.kind) {
        Kind.TIMER_555 -> {
            val supply=v[7]-v[0]
            // Three 5 kohm internal divider resistors; lower two are series at CONT.
            resistor(7,4,5000.0);resistor(4,0,10000.0)
            if(supply in 4.5..16.0) {
                val high=timerHigh && v[3]-v[0]>=.7
                val quiescent=(if(high) .002 else .003)+(supply-5).coerceAtLeast(0.0)*.0007
                flow(7,0,max(0.0,quiescent-supply/15000.0))
                flow(5,0,30e-9);flow(7,1,.5e-6)
                if(high) {
                    flow(7,2,(max(0.0,supply-1.0)-(v[2]-v[0]))/7.0)
                    flow(6,0,20e-9)
                } else {
                    // Nominal VOL/IOL fit, interpolating the 5 V and 15 V test curves.
                    val blend=((supply-5)/10).coerceIn(0.0,1.0)
                    val points=listOf(0.0 to 0.0,.1 to (.005+.005*blend),
                        .15 to (.008+.0086666667*blend),.4 to (.020+.030*blend),
                        2.0 to .100,2.5 to .200)
                    val output=v[2]-v[0]
                    val upper=points.indexOfFirst { it.first>=output }.let { if(it<0) points.lastIndex else if(it==0) 1 else it }
                    val segment=points.subList(upper-1,upper+1)
                    val (a,b)=segment
                    flow(2,0,a.second+(output-a.first)*(b.second-a.second)/(b.first-a.first))
                    resistor(6,0,18.75) // 0.15 V at 8 mA, SLFS022K section 5.5
                }
            } else { resistor(2,0,1e9);resistor(6,0,1e9) }
            resistor(3,0,1e9)
        }
        Kind.LM358 -> {
            val powered=v[7]-v[3] in 3.0..30.0
            if(powered) {
                flow(7,3,.0007)
                listOf(1,2,4,5).forEach { flow(7,it,p.value("inputBias")) }
            }
            val targets=lm358Targets(p,v,dt,lmPrevious)
            listOf(0,6).forEachIndexed { ch,out ->
                val inputs=if(ch==0) listOf(1,2) else listOf(4,5)
                if(powered && inputs.all { v[it]>=v[3]-1e-6 && v[it]<=v[7]-1.5+1e-6 }) {
                    val current=((v[out]-v[3]-targets[ch])/p.value("outputResistance")).coerceIn(-.030,.020)
                    if(current<0) flow(7,out,-current) else flow(out,3,current)
                }
                resistor(out,3,1e9)
            }
            listOf(1,2,4,5).forEach { resistor(it,3,1e12) }
        }
        Kind.L293D -> {
            val logic=v[15]-v[3];val motor=v[7]-v[3]
            val powered=logic in 4.5..7.0 && motor>=logic && motor<=36.0
            listOf(Triple(0,1,2),Triple(0,6,5),Triple(8,9,10),Triple(8,14,13)).forEach { (en,input,out) ->
                val enabled=powered && v[en]-v[3]>=2.3
                val high=enabled && v[input]-v[3]>=2.3
                val low=enabled && v[input]-v[3]<=1.5
                if(logic>=4.5) flow(15,3,when { high->.013/4;low->.035/4;else->.008/4 })
                if(motor>=4.5) flow(7,3,if(high) .014/4 else .002/4)
                // Source/sink drops interpolate nominal test points; no fictitious short-circuit protection.
                if(high) flow(7,out,v[7]-v[out]-.8)
                if(low) flow(out,3,(v[out]-v[3])* .5)
                diode(out,7);diode(3,out)
                resistor(out,3,1e9)
            }
            listOf(0,1,6,8,9,14).forEach { resistor(it,3,1e9) }
        }
        else -> error("Unknown datasheet IC ${p.kind}")
    }
    return amps
}

/** Newton stamp of a conservative multiport model; numerical Jacobian includes supply loading. */
internal fun stampDatasheetIc(p:PlacedComponent,indices:IntArray,guess:DoubleArray,
                               a:Array<DoubleArray>,b:DoubleArray,timerHigh:Boolean=false,
                               dt:Double=0.0,lmPrevious:List<Double> = listOf(0.0,0.0)) {
    val volts=DoubleArray(indices.size) { indices[it].let { node -> if(node>=0) guess[node] else 0.0 } }
    val current=datasheetIcCurrents(p,volts,timerHigh,dt,lmPrevious)
    val jacobian=Array(indices.size) { DoubleArray(indices.size) }
    for(pin in indices.indices) {
        val h=if(p.kind==Kind.LM358 && pin in listOf(1,2,4,5)) 1e-8 else max(1e-6,abs(volts[pin])*1e-6)
        val shifted=volts.copyOf();shifted[pin]+=h
        val next=datasheetIcCurrents(p,shifted,timerHigh,dt,lmPrevious)
        for(row in indices.indices) jacobian[row][pin]=(next[row]-current[row])/h
    }
    for(pin in indices.indices) {
        val row=indices[pin];if(row<0) continue
        var ieq=current[pin]
        for(other in indices.indices) {
            val g=jacobian[pin][other]
            ieq-=g*volts[other]
            val col=indices[other];if(col>=0) a[row][col]+=g
        }
        b[row]-=ieq
    }
    if(p.kind==Kind.LM358) {
        // Pseudo-transient conductance is zero at the current iterate. It regularizes
        // current-limited outputs without altering the converged circuit equations.
        for(out in listOf(0,6)) {
            val g=1.0/p.value("outputResistance")
            stampBranch(a,b,indices[out],indices[3],BranchModel(g,-g*(volts[out]-volts[3])))
        }
    }
}

internal fun datasheetIcReading(p:PlacedComponent,volts:Map<TerminalRef,Double>,timerHigh:Boolean=false,
                                dt:Double=0.0,lmPrevious:List<Double> = listOf(0.0,0.0)):Reading {
    val v=DoubleArray(p.terminalCount) { volts[TerminalRef(p.id,it)] ?: 0.0 }
    val amps=datasheetIcCurrents(p,v,timerHigh,dt,lmPrevious)
    val ground=if(p.kind==Kind.TIMER_555) 0 else 3
    val supply=7
    val power=max(0.0,v.indices.sumOf { (v[it]-v[ground])*amps[it] })
    val health=when(p.kind) {
        Kind.TIMER_555 -> when {
            abs(amps[2])>.2 -> Health.OVERLOAD
            v[7]-v[0] !in 4.5..16.0 -> Health.WARNING
            else -> Health.NORMAL
        }
        Kind.LM358 -> if(!lm358Valid(v)) Health.WARNING else if(listOf(0,6).any { amps[it]>=.01999 || amps[it]<=-.02999 }) Health.OVERLOAD else Health.NORMAL
        else -> when {
            listOf(2,5,10,13).any { abs(amps[it])>.6 } -> Health.OVERLOAD
            v[15]-v[3] !in 4.5..7.0 || v[7]-v[3] !in (v[15]-v[3])..36.0 -> Health.WARNING
            listOf(0,8).any { v[it]-v[3]>1.5 && v[it]-v[3]<2.3 } -> Health.WARNING
            listOf(0 to 1,0 to 6,8 to 9,8 to 14).any { (en,input) -> v[en]-v[3]>=2.3 && v[input]-v[3]>1.5 && v[input]-v[3]<2.3 } -> Health.WARNING
            else -> Health.NORMAL
        }
    }
    val temperature=if(p.kind==Kind.L293D) 25+36.4*power else null
    return Reading(v[supply]-v[ground],amps[supply],power,
        if(health==Health.NORMAL && temperature!=null && temperature>=125) Health.THERMAL_WARNING else health,temperature)
}
