package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PlacedComponent

/** Pins: 0 top, 1 wiper, 2 bottom. Contact stops use 1 mΩ to avoid singular branches. */
fun potentiometerSegments(part:PlacedComponent):Pair<Double,Double> {
    require(part.kind==Kind.POTENTIOMETER)
    val total=part.value("resistance")
    val position=part.value("position")
    return (total*position).coerceAtLeast(1e-3) to
        (total*(1.0-position)).coerceAtLeast(1e-3)
}
