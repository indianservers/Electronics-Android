package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PlacedComponent
import kotlin.math.exp
import kotlin.math.pow

/** Electrical resistance from user controlled environmental stimuli. */
fun sensorResistanceOhms(part: PlacedComponent): Double = when (part.kind) {
    Kind.LDR -> {
        val lux = part.value("illuminanceLux").coerceAtLeast(0.1)
        (part.value("referenceResistance") * (lux / 100.0).pow(-part.value("gamma")))
            .coerceIn(1.0, 1e12)
    }
    Kind.THERMISTOR -> {
        val kelvin = part.value("temperatureC") + 273.15
        (part.value("resistance25C") * exp(part.value("beta") *
            (1.0 / kelvin - 1.0 / 298.15))).coerceIn(1.0, 1e12)
    }
    else -> error("${part.kind} is not a resistive sensor")
}
