package com.indianservers.circuitssimulator
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Assert.*
import org.junit.Test
class ChemistrySensorTest {
    @Test fun commandTimingAsciiConsumptionAndPowerLoss() {
        ChemistrySensorParts.profiles.values.forEach { p ->
            val d=I2cDeviceModel(p.address,"S",p.kind)
            assertFalse(d.write(listOf(82)));assertTrue(d.read(16,EnvironmentState()).isEmpty())
            d.powerChanged(true,0);d.chemistry!!.stimuli=mapOf(p.key to p.normal)
            assertEquals(255,d.read(16,EnvironmentState(),0).first())
            assertTrue(d.write(listOf(82),100))
            assertEquals(254,d.read(16,EnvironmentState(),99+p.conversionMicros).first())
            val bytes=d.read(16,EnvironmentState(),100+p.conversionMicros)
            assertEquals(1,bytes.first());assertEquals(p.normal,bytes.drop(1).takeWhile { it!=0 }.map { it.toChar() }.joinToString("").toDouble(),.0001)
            assertEquals(255,d.read(16,EnvironmentState(),100+p.conversionMicros).first())
            assertTrue(d.write(listOf(90),2_000_000));assertEquals(2,d.read(16,EnvironmentState(),2_300_000).first())
            d.write(listOf(82),3_000_000);d.powerChanged(false,3_000_100);d.powerChanged(true,3_000_200)
            assertEquals(255,d.read(16,EnvironmentState(),3_000_201).first())
        }
    }
    @Test fun registryAndPropertyAudit() {
        assertTrue(ComponentRegistry.validate().joinToString(),ComponentRegistry.validate().isEmpty())
        assertTrue(ComponentProperties.audit().joinToString(),ComponentProperties.audit().isEmpty())
    }
}
