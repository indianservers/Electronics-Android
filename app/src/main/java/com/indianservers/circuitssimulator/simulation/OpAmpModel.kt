package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PlacedComponent
import kotlin.math.min

internal fun isOpAmp(p:PlacedComponent)=p.kind==Kind.OPAMP || p.kind==Kind.IDEAL_OPAMP

internal data class OpAmpLinearization(val output:Double,val slope:Double)

internal fun opAmpAt(p:PlacedComponent,differential:Double):OpAmpLinearization {
    val lower=p.value("lowerRail")
    val upper=p.value("upperRail")
    require(upper>lower) { "Op-amp upper rail must exceed lower rail" }
    val margin=if(p.kind==Kind.IDEAL_OPAMP) 0.0 else min(.5,(upper-lower)*.1)
    val raw=p.value("gain")*(differential+p.value("inputOffset"))
    val minOutput=lower+margin
    val maxOutput=upper-margin
    return OpAmpLinearization(raw.coerceIn(minOutput,maxOutput),
        if(raw>minOutput && raw<maxOutput) p.value("gain") else 0.0)
}
