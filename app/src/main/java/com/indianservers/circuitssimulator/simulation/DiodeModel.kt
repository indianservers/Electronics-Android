package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PlacedComponent
import kotlin.math.exp
import kotlin.math.max

data class DiodeLinearization(val current:Double,val conductance:Double) {
    fun equivalentCurrent(voltage:Double)=current-conductance*voltage
}

/** Educational Shockley junction, with a linear numerical continuation beyond the useful range. */
fun diodeAt(part:PlacedComponent,voltage:Double):DiodeLinearization {
    require(part.kind in listOf(Kind.DIODE,Kind.RECTIFIER,Kind.LED))
    val isat=part.value("isat")
    val n=part.value("ideality")
    val temperatureKelvin=part.value("temperatureC")+273.15
    val thermal=n*8.617333262145e-5*temperatureKelvin // k/q in V/K
    val limit=if(part.kind==Kind.LED) 2.0 else 1.2
    if(voltage<=limit) {
        val e=exp((voltage/thermal).coerceAtLeast(-40.0))
        return DiodeLinearization(isat*(e-1.0),max(isat*e/thermal,1e-12))
    }
    val e=exp(limit/thermal)
    val g=max(isat*e/thermal,1e-12)
    return DiodeLinearization(isat*(e-1.0)+g*(voltage-limit),g)
}
