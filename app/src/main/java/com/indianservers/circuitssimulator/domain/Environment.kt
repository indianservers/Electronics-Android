package com.indianservers.circuitssimulator.domain

/** Shared virtual bench environment. Sensors consume these values; firmware never reads them directly. */
data class EnvironmentState(
    val temperatureC:Double=25.0,
    val humidityPercent:Double=45.0,
    val illuminanceLux:Double=300.0,
    val distanceCm:Double=40.0,
    val motion:Boolean=false,
    val pressureHpa:Double=1013.0,
    val soundDb:Double=40.0
) {
    init {
        require(temperatureC in -40.0..150.0)
        require(humidityPercent in 0.0..100.0)
        require(illuminanceLux in 0.0..100000.0)
        require(distanceCm in 1.0..400.0)
        require(pressureHpa in 800.0..1200.0)
        require(soundDb in 0.0..140.0)
    }
}

enum class FirmwareLanguage { ARDUINO_SUBSET, MICROPYTHON_SUBSET }

data class FirmwareAttachment(val boardId:String,val language:FirmwareLanguage,val source:String,
                              val usbPower:Boolean=false)

enum class EnvironmentPreset { COLD, ROOM, HOT, DARK, INDOOR, SUNLIGHT, NEAR, FAR, MOTION, STILL }

object EnvironmentPresets {
    fun apply(state:EnvironmentState,preset:EnvironmentPreset)=when(preset) {
        EnvironmentPreset.COLD -> state.copy(temperatureC=5.0)
        EnvironmentPreset.ROOM -> state.copy(temperatureC=25.0)
        EnvironmentPreset.HOT -> state.copy(temperatureC=45.0)
        EnvironmentPreset.DARK -> state.copy(illuminanceLux=5.0)
        EnvironmentPreset.INDOOR -> state.copy(illuminanceLux=300.0)
        EnvironmentPreset.SUNLIGHT -> state.copy(illuminanceLux=20000.0)
        EnvironmentPreset.NEAR -> state.copy(distanceCm=10.0)
        EnvironmentPreset.FAR -> state.copy(distanceCm=200.0)
        EnvironmentPreset.MOTION -> state.copy(motion=true)
        EnvironmentPreset.STILL -> state.copy(motion=false)
    }
}
