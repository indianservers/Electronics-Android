package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.embedded.PinMode
import com.indianservers.circuitssimulator.simulation.digital.LogicState

enum class PeripheralLevel { FULL, BASIC, METADATA, NOT_SIMULATED }

data class BoardSupportMatrix(
    val boardId:String,val product:String,val languages:List<FirmwareLanguage>,
    val gpio:PeripheralLevel,val adc:PeripheralLevel,val pwm:PeripheralLevel,
    val uart:PeripheralLevel,val i2c:PeripheralLevel,val spi:PeripheralLevel,
    val wifi:PeripheralLevel,val bluetooth:PeripheralLevel,
    val logicVoltage:Double,val adcBits:Int,val pwmDefaultHz:Double,
    val notes:List<String>
)

/** Execution support is never implied beyond these tiers. */
interface BoardRuntime {
    val boardId:String
    fun reset()
    fun setPinMode(pin:String,mode:PinMode)
    fun digitalWrite(pin:String,high:Boolean)
    fun digitalRead(pin:String):LogicState
    fun analogRead(pin:String):Int
    fun pwmWrite(pin:String,value:Int,frequencyHz:Double=490.0)
    fun serialPorts():List<UartPortSpec>
}

data class UartPortSpec(val name:String,val txPin:String,val rxPin:String,val baud:Int?=null)
data class PinWatch(val boardId:String,val pin:String)
data class VariableWatch(val boardId:String,val name:String)

data class CombinedAdvance(
    val frames:List<FirmwareFrame>,
    val console:Map<String,List<String>>,
    val error:FirmwareDiagnostic?=null,
    val timeMicros:Long=0,
    val currentLines:Map<String,Int> = emptyMap(),
    val variables:Map<String,Map<String,String>> = emptyMap(),
    val pins:Map<String,Map<String,com.indianservers.circuitssimulator.simulation.embedded.GpioReading>> = emptyMap(),
    val busLog:List<BusEvent> = emptyList(),
    val displays:Map<String,String> = emptyMap(),
    val servoAngles:Map<String,Double> = emptyMap()
)

object BoardSupport {
    fun languages(kind:Kind)=when(BoardRegistry.boards[kind]?.family) {
        BoardFamily.ARDUINO_AVR,BoardFamily.ARDUINO_SAMD -> listOf(FirmwareLanguage.ARDUINO_SUBSET)
        BoardFamily.RP2040,BoardFamily.RP2350,BoardFamily.ESP8266,BoardFamily.ESP32,BoardFamily.ESP32_C3 ->
            listOf(FirmwareLanguage.ARDUINO_SUBSET,FirmwareLanguage.MICROPYTHON_SUBSET)
        null -> emptyList()
    }
    fun matrix(kind:Kind):BoardSupportMatrix {
        val board=BoardRegistry.boards.getValue(kind)
        val cap=board.capabilities
        fun level(sim:FeatureSimLevel)=when(sim) {
            FeatureSimLevel.FULL -> PeripheralLevel.FULL
            FeatureSimLevel.BASIC -> PeripheralLevel.BASIC
            FeatureSimLevel.METADATA -> PeripheralLevel.METADATA
            FeatureSimLevel.UNSUPPORTED -> PeripheralLevel.NOT_SIMULATED
        }
        return BoardSupportMatrix(kind.name,board.product,languages(kind),
            level(cap.gpio.simulator),level(cap.adc.simulator),level(cap.pwm.simulator),
            level(cap.uart.simulator),level(cap.i2c.simulator),level(cap.spi.simulator),
            level(cap.wifi.simulator),level(cap.bluetooth.simulator),
            board.logicVoltage,board.adcBits,if(board.family==BoardFamily.ARDUINO_AVR) 490.0 else 500.0,
            listOf(board.notes)+board.warnings+listOf(
                "Firmware uses the documented supported subset, not arbitrary C++ or CPython.",
                "UART/I²C/SPI are event-based educational buses; bit timing is not claimed."))
    }
    fun defaultLanguage(kind:Kind)=languages(kind).first()
    fun label(level:PeripheralLevel)=when(level) {
        PeripheralLevel.FULL -> "Full";PeripheralLevel.BASIC -> "Basic"
        PeripheralLevel.METADATA -> "Metadata";PeripheralLevel.NOT_SIMULATED -> "Not simulated"
    }
}
