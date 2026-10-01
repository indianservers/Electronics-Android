package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PlacedComponent
import kotlin.math.exp
import kotlin.math.max

/** Electrical terminal order: BJT C/B/E; MOSFET D/G/S. Positive current leaves a terminal. */
fun threeTerminalCurrents(part:PlacedComponent, volts:DoubleArray):DoubleArray = when(part.kind) {
    Kind.NPN_BJT,Kind.PNP_BJT -> bjtCurrents(part,volts)
    Kind.NMOS,Kind.PMOS -> mosCurrents(part,volts)
    else -> error("Not a three-terminal device: ${part.kind}")
}

private fun bjtCurrents(part:PlacedComponent,v:DoubleArray):DoubleArray {
    val sign=if(part.kind==Kind.NPN_BJT) 1.0 else -1.0
    val vt=0.025852 // kT/q at 300 K; model temperature dependence remains future work.
    val forward=limitedJunctionCurrent(sign*(v[1]-v[2]),part.value("is"),vt)
    val reverse=limitedJunctionCurrent(sign*(v[1]-v[0]),part.value("is"),vt)
    val alphaF=part.value("bf")/(part.value("bf")+1.0)
    val alphaR=part.value("br")/(part.value("br")+1.0)
    val early=1.0+max(sign*(v[0]-v[2]),0.0)/part.value("vaf")
    val collector=sign*(alphaF*forward*early-reverse)
    val emitter=sign*(-forward*early+alphaR*reverse)
    return doubleArrayOf(collector,-collector-emitter,emitter)
}

private fun mosCurrents(part:PlacedComponent,v:DoubleArray):DoubleArray {
    val sign=if(part.kind==Kind.NMOS) 1.0 else -1.0
    val vds=sign*(v[0]-v[2]);val vgs=sign*(v[1]-v[2])
    val overdrive=vgs-part.value("vth")
    val channel=when {
        overdrive<=0.0 || vds<=0.0 -> 0.0
        vds<overdrive -> part.value("kp")*(overdrive*vds-.5*vds*vds)*(1.0+part.value("lambda")*vds)
        else -> .5*part.value("kp")*overdrive*overdrive*(1.0+part.value("lambda")*vds)
    }
    // Body diode anode at source for NMOS, at drain for PMOS in transformed coordinates.
    val body=limitedJunctionCurrent(-vds,1e-12,1.8*.025852)
    val gateLeak=1e-12*(v[1]-v[2])
    val drain=sign*(channel-body)
    return doubleArrayOf(drain,gateLeak,-drain-gateLeak)
}

/** Linear continuation above the exponential limit keeps Newton's Jacobian informative. */
private fun limitedJunctionCurrent(voltage:Double,isat:Double,thermal:Double):Double {
    val limit=.8
    if(voltage<=limit) return isat*(exp((voltage/thermal).coerceAtLeast(-40.0))-1.0)
    val atLimit=isat*(exp(limit/thermal)-1.0)
    return atLimit+(voltage-limit)*isat*exp(limit/thermal)/thermal
}

/** Adds the device's linearized KCL residual and numerical Jacobian to a dense MNA system. */
fun stampThreeTerminal(part:PlacedComponent,indices:IntArray,guess:DoubleArray,
                       matrix:Array<DoubleArray>,rhs:DoubleArray) {
    val v=DoubleArray(3) { pin -> indices[pin].takeIf { it>=0 }?.let { guess[it] } ?: 0.0 }
    val current=threeTerminalCurrents(part,v)
    val jacobian=Array(3) { DoubleArray(3) }
    for(col in 0..2) {
        val delta=1e-5*max(1.0,kotlin.math.abs(v[col]))
        val upper=v.copyOf().also { it[col]+=delta }
        val lower=v.copyOf().also { it[col]-=delta }
        val plus=threeTerminalCurrents(part,upper)
        val minus=threeTerminalCurrents(part,lower)
        for(row in 0..2) jacobian[row][col]=(plus[row]-minus[row])/(2.0*delta)
    }
    for(row in 0..2) {
        val r=indices[row]
        if(r<0) continue
        rhs[r]-=current[row]
        for(col in 0..2) {
            val g=jacobian[row][col]
            if(indices[col]>=0) matrix[r][indices[col]]+=g
            rhs[r]+=g*v[col]
        }
    }
}
