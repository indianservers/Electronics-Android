package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*

enum class PeripheralInterface { GPIO, ADC, PWM, I2C, SPI, UART, PARALLEL, ANALOG, POWER }
data class ElectricalProfile(val supplyRange:ClosedFloatingPointRange<Double>, val inputMaximum:Double,
    val modulePullupOhms:Double?=null, val maximumClockHz:Int=400000)
data class PeripheralProfile(val family:String,val interfaces:Set<PeripheralInterface>,
    val electrical:ElectricalProfile,val datasheet:DatasheetMetadata,val limitations:List<String>)
interface EmbeddedPeripheral {
    val ownerId:String
    val profile:PeripheralProfile
    fun powerChanged(powered:Boolean,timeMicros:Long)
    fun inspect(timeMicros:Long):Map<String,String>
}

/** Profiles describe the selected four/six-pin module, rather than inventing a raw IC pinout. */
object EmbeddedProfiles {
    private fun profile(family:String,interfaceType:PeripheralInterface,range:ClosedFloatingPointRange<Double>,
        manufacturer:String,url:String,pullup:Double?=null,clock:Int=400000,vararg limits:String)=PeripheralProfile(
        family,setOf(interfaceType,PeripheralInterface.POWER),ElectricalProfile(range,range.endInclusive,pullup,clock),
        DatasheetMetadata(manufacturer=manufacturer,partNumber=family,referenceUrl=url,
            packageName="Simulated module",lastVerifiedUtc=if(manufacturer=="Generic module family") null else "2026-10-04"),limits.toList())
    val profiles=mapOf(
        Kind.I2C_TEMP_SENSOR to profile("LM75B module",PeripheralInterface.I2C,3.0..5.5,"Texas Instruments",
            "https://www.ti.com/lit/ds/symlink/lm75b.pdf",4700.0,400000,"OS output is not exposed by this module."),
        Kind.I2C_EEPROM to profile("AT24C02C module",PeripheralInterface.I2C,1.7..5.5,"Microchip",
            "https://ww1.microchip.com/downloads/en/DeviceDoc/AT24C01C-AT24C02C-I2C-Compatible-Two-Wire-Serial-EEPROM-1Kbit-2Kbit-20006111A.pdf",
            null,1000000,"Address and WP straps are module parameters; 5 ms conservative write-cycle policy; wear omitted."),
        Kind.OLED_SSD1306 to profile("SSD1306 128×64 module",PeripheralInterface.I2C,2.7..3.6,"Solomon Systech",
            "https://www.solomon-systech.com/product/ssd1306/",4700.0,400000,"Scroll/fade commands and analog contrast are not rendered."),
        Kind.I2C_LCD to profile("PCF8574 / HD44780 16×2 backpack",PeripheralInterface.I2C,4.5..5.5,"Texas Instruments / Hitachi",
            "https://www.ti.com/lit/ds/symlink/pcf8574.pdf",4700.0,100000,"P0 RS, P1 RW, P2 E, P3 backlight, P4–P7 D4–D7; write-only LCD interface."),
        Kind.SPI_MEMORY to profile("W25Q32JV module",PeripheralInterface.SPI,2.7..3.6,"Winbond",
            "https://www.winbond.com/resource-files/w25q32jv%20spi%20revc%2008302016.pdf",null,50000000,
            "Single-bit SPI modes 0/3; 1 µs electrical resolution (500 kHz resolved clock); conservative program/erase timing; quad IO and wear omitted."),
        Kind.ULTRASONIC to profile("HC-SR04",PeripheralInterface.GPIO,4.5..5.5,"Generic module family",
            "https://www.sparkfun.com/products/15569",null,0,"Acoustic propagation is reduced to distance-dependent echo timing."),
        Kind.PIR_SENSOR to profile("HC-SR501",PeripheralInterface.GPIO,4.5..12.0,"Generic module family",
            "https://www.mpja.com/download/31227sc.pdf",null,0,"Warm-up and retrigger are modeled; field geometry and thermal noise omitted.")
    )
}
