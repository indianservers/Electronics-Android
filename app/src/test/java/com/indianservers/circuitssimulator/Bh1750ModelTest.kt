package com.indianservers.circuitssimulator
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Assert.*
import org.junit.Test

class Bh1750ModelTest {
    @Test fun highResolutionConversionTimingQuantizationAndSaturation(){
        val m=Bh1750Model();m.lux=1000.0
        assertEquals(listOf(0,0),m.read(2,0));assertTrue(m.write(listOf(0x10),0))
        assertEquals(listOf(0,0),m.read(2,119999));assertEquals(listOf(4,176),m.read(2,120000))
        m.lux=2000.0;assertEquals(listOf(4,176),m.read(2,239999));assertEquals(listOf(9,96),m.read(2,240000))
        m.lux=100000.0;assertEquals(listOf(255,255),m.read(2,360000))
        m.lux=Double.NaN;assertTrue(m.read(2,480000).isEmpty())
    }
    @Test fun singleShotResetUnsupportedCommandsAndSupplyLoss(){
        val d=I2cDeviceModel(35,"L",Kind.BH1750_LIGHT)
        assertFalse(d.write(listOf(0x20),0));d.powerChanged(true,0);d.light!!.lux=50.0
        assertTrue(d.write(listOf(0x20),0));assertEquals(listOf(0,60),d.read(2,EnvironmentState(),120000))
        d.light.lux=100.0;assertEquals(listOf(0,60),d.read(2,EnvironmentState(),240000))
        assertFalse(d.write(listOf(7),250000));assertTrue(d.write(listOf(1,7),250000));assertEquals(listOf(0,0),d.read(2,EnvironmentState(),250000))
        assertFalse(d.write(listOf(0x13),250001));assertFalse(d.write(listOf(0x42),250001))
        d.powerChanged(false,250002);d.powerChanged(true,250003);assertEquals(listOf(0,0),d.read(2,EnvironmentState(),500000))
        assertTrue(ComponentRegistry.validate().joinToString(),ComponentRegistry.validate().isEmpty())
    }
}
