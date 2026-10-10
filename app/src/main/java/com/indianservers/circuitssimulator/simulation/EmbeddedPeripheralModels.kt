package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*
import kotlin.math.abs

internal fun stampPhaseFive(p:PlacedComponent,indices:IntArray,guess:DoubleArray,
    matrix:Array<DoubleArray>,rhs:DoubleArray) {
    fun v(pin:Int)=indices[pin].takeIf { it>=0 }?.let { guess[it] } ?: 0.0
    fun branch(a:Int,b:Int,g:Double,ieq:Double=0.0)=
        stampBranch(matrix,rhs,indices[a],indices[b],BranchModel(g,ieq))
    when(p.kind) {
        Kind.SERIAL_TERMINAL -> {
            branch(0,2,1e-9);branch(1,2,1e-9)
        }
        Kind.I2C_TEMP_SENSOR,Kind.I2C_LCD,Kind.OLED_SSD1306,Kind.I2C_EEPROM -> {
            branch(0,1,1.0/8000.0)
            branch(2,1,1e-9);branch(3,1,1e-9)
            com.indianservers.circuitssimulator.firmware.EmbeddedProfiles.profiles[p.kind]?.electrical?.modulePullupOhms?.let { r ->
                branch(2,0,1.0/r);branch(3,0,1.0/r)
            }
        }
        Kind.ULTRASONIC -> {
            branch(0,1,1.0/15000.0)
            branch(2,1,1e-9);branch(3,1,1e-9)
        }
        Kind.PIR_SENSOR -> {
            branch(0,2,1.0/20000.0)
            branch(1,2,1e-9)
        }
        Kind.SPI_MEMORY -> {
            branch(0,1,1.0/10000.0)
            (2 until p.terminalCount).forEach { branch(it,1,1e-9) }
        }
        else -> error("No Phase 5 model for ${p.kind}")
    }
}

internal fun phaseFiveReading(p:PlacedComponent,volts:Map<TerminalRef,Double>):Reading {
    fun v(pin:Int)=volts[TerminalRef(p.id,pin)] ?: 0.0
    val drop=when(p.kind) {
        Kind.PIR_SENSOR -> v(0)-v(2)
        else -> v(0)-v(1)
    }
    val current=when(p.kind) {
        Kind.I2C_TEMP_SENSOR,Kind.I2C_LCD,Kind.OLED_SSD1306,Kind.I2C_EEPROM -> drop/8000.0
        Kind.ULTRASONIC -> drop/15000.0
        Kind.PIR_SENSOR -> drop/20000.0
        Kind.SPI_MEMORY -> drop/10000.0
        else -> 0.0
    }
    return Reading(drop,current,abs(drop*current))
}
