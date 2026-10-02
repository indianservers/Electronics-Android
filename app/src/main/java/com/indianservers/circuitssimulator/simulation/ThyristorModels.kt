package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PlacedComponent
import com.indianservers.circuitssimulator.domain.TerminalRef
import kotlin.math.abs

internal fun thyristorTriggered(p:PlacedComponent,v:DoubleArray):Boolean {
    val gate=abs(v[1]-v[2])>=p.value("triggerVoltage")
    return gate && (p.kind==Kind.TRIAC || v[0]-v[2]>0.0)
}

internal fun stampThyristor(p:PlacedComponent,indices:IntArray,guess:DoubleArray,
    matrix:Array<DoubleArray>,rhs:DoubleArray,latched:Boolean=false) {
    val voltages=DoubleArray(3) { i -> indices[i].takeIf { it>=0 }?.let { guess[it] } ?: 0.0 }
    val on=latched || thyristorTriggered(p,voltages)
    val gate=BranchModel(1.0/p.value("gateResistance"),0.0)
    stampBranch(matrix,rhs,indices[1],indices[2],gate)
    val forward=p.kind==Kind.TRIAC || voltages[0]>=voltages[2]
    stampBranch(matrix,rhs,indices[0],indices[2],BranchModel(
        if(on && forward) 1.0/p.value("onResistance") else 1e-9,0.0))
}

internal fun thyristorReading(p:PlacedComponent,volts:Map<TerminalRef,Double>,latched:Boolean=false):Reading {
    val pins=DoubleArray(3) { volts[TerminalRef(p.id,it)] ?: 0.0 }
    val drop=pins[0]-pins[2]
    val on=latched || thyristorTriggered(p,pins)
    val current=drop*(if(on && (p.kind==Kind.TRIAC || drop>=0.0))
        1.0/p.value("onResistance") else 1e-9)
    return Reading(drop,current,abs(drop*current))
}
