package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*

/** Returns a nominal output relative to sensor GND, or null outside documented operation. */
fun iotSensorOutputVoltage(part: PlacedComponent, supply: Double): Double? {
    val profile=IotSensorParts.profiles.getValue(part.kind)
    val temperature=part.value("temperatureC")
    if(!supply.isFinite() || supply !in profile.supplyMin..profile.supplyMax ||
        !temperature.isFinite() || temperature !in profile.temperatureMin..profile.temperatureMax) return null
    val stimulus=if(part.kind==Kind.HIH4030_SENSOR) {
        val humidity=part.value("humidityPercent")
        if(!humidity.isFinite() || humidity !in 0.0..100.0) return null
        humidity*(1.0546-.00216*temperature)
    } else temperature
    return (profile.offset+profile.slope*stimulus) *
        (profile.referenceSupply?.let { supply/it } ?: 1.0)
}
