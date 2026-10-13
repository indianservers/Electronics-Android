package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test

class MotionSensorTest {
    private fun signed(bytes:List<Int>,offset:Int=0):Int = ((bytes[offset+1] shl 8) or bytes[offset]).toShort().toInt()
    private fun read(model:MotionSensorModel,reg:Int,count:Int):List<Int> {
        assertTrue(model.write(listOf(reg)));return model.read(count)
    }
    @Test fun everySensorHasItsManufacturerSourceExactIdentityAndCarrierPins() {
        assertTrue(ComponentRegistry.validate().joinToString(),ComponentRegistry.validate().isEmpty())
        assertTrue(ComponentProperties.audit().joinToString(),ComponentProperties.audit().isEmpty())
        assertEquals(4,MotionSensorParts.profiles.size)
        MotionSensorParts.profiles.values.forEach { profile ->
            val definition=ComponentRegistry.definitions.getValue(profile.kind)
            assertEquals(profile.url,definition.datasheet.referenceUrl)
            assertEquals(profile.revision,definition.datasheet.revision)
            assertEquals(listOf("VCC","GND","SDA","SCL"),definition.datasheet.physicalPinOrder)
            val part=PlacedComponent(kind=profile.kind,reference="S",x=0f,y=0f)
            assertEquals(4,part.terminalCount)
            (0..3).forEach { assertEquals(MotionSensorParts.pins[it],ComponentPins.name(part,it));assertNull(ComponentPins.packageNumber(part,it)) }
            val model=MotionSensorModel(profile.kind)
            assertEquals(listOf(profile.identity),read(model,if(profile.kind==Kind.ADXL345_ACCEL)0 else 0x0F,1))
        }
        assertTrue(ComponentRegistry.search("gyroscope").any { it.kind==Kind.L3GD20_GYRO })
        assertTrue(ComponentRegistry.search("accelerometer").any { it.kind==Kind.ADXL345_ACCEL })
    }
    @Test fun addressStrapsRejectUndocumentedValuesAndPropertiesKeepMotionUnits() {
        val part=PlacedComponent(kind=Kind.ADXL345_ACCEL,reference="ACC",x=0f,y=0f)
        val spec=ComponentRegistry.definitions.getValue(part.kind).parameters.first { it.key=="address" }
        listOf(0x53,0x1D).forEach { assertNull(ComponentProperties.parse(part,spec,"0x${it.toString(16)}").error) }
        assertNotNull(ComponentProperties.parse(part,spec,"0x40").error)
        val gyro=PlacedComponent(kind=Kind.L3GD20_GYRO,reference="G",x=0f,y=0f)
        val velocity=ComponentRegistry.definitions.getValue(gyro.kind).parameters.first { it.key=="gyroXDps" }
        assertEquals(-123.5,ComponentProperties.parse(gyro,velocity,"-123.5 °/s").value!!,0.0)
    }
    @Test fun l3gd20HasPowerDownAxisEnablesThreeScalesAndEndianSelection() {
        val model=MotionSensorModel(Kind.L3GD20_GYRO)
        model.stimuli=mapOf("gyroXDps" to 87.5,"gyroYDps" to -87.5,"gyroZDps" to 35.0)
        assertEquals(List(6){0},read(model,0xA8,6))
        assertTrue(model.write(listOf(0x20,0x0F)))
        val samples=read(model,0xA8,6)
        assertEquals(10000,signed(samples));assertEquals(-10000,signed(samples,2));assertEquals(4000,signed(samples,4))
        assertTrue(model.write(listOf(0x23,0x10)));assertEquals(5000,signed(read(model,0xA8,2)))
        assertTrue(model.write(listOf(0x23,0x20)));assertEquals(1250,signed(read(model,0xA8,2)))
        assertTrue(model.write(listOf(0x23,0x40)));assertEquals(listOf(0x27,0x10),read(model,0xA8,2))
        assertTrue(model.write(listOf(0x20,0x0E)));assertEquals(0,signed(read(model,0xA8,2)))
    }
    @Test fun legacyBurstRequiresBitSevenAndReadonlyRegistersCannotBeOverwritten() {
        val model=MotionSensorModel(Kind.L3GD20_GYRO)
        model.stimuli=mapOf("gyroXDps" to 87.5)
        assertTrue(model.write(listOf(0x20,0x0F)))
        assertEquals(listOf(0x10,0x10),read(model,0x28,2))
        assertEquals(listOf(0x10,0x27),read(model,0xA8,2))
        assertFalse(model.write(listOf(0x0F,0)))
        assertFalse(model.write(listOf(0x28,255)))
        assertEquals(listOf(0xD4),read(model,0x0F,1))
        assertTrue(model.write(listOf(0x70)));assertEquals(emptyList<Int>(),model.read(1))
        assertNotNull(model.lastError)
    }
    @Test fun lis3dhUsesLeftAlignedEightTenTwelveBitDataAndSixteenGScale() {
        val model=MotionSensorModel(Kind.LIS3DH_ACCEL)
        model.stimuli=mapOf("accelXG" to .256,"accelYG" to -.256,"accelZG" to 1.0)
        assertTrue(model.write(listOf(0x20,0x57)))
        assertEquals(64 shl 6,signed(read(model,0xA8,2)))
        assertTrue(model.write(listOf(0x23,8)))
        assertEquals(256 shl 4,signed(read(model,0xA8,2)))
        assertEquals(-256 shl 4,signed(read(model,0xAA,2)))
        assertTrue(model.write(listOf(0x23,0x38)));model.stimuli=mapOf("accelXG" to 12.0)
        assertEquals(1000 shl 4,signed(read(model,0xA8,2)))
        assertTrue(model.write(listOf(0x23,0)));assertTrue(model.write(listOf(0x20,0x5F)))
        model.stimuli=mapOf("accelXG" to .256)
        assertEquals(16 shl 8,signed(read(model,0xA8,2)))
    }
    @Test fun adxl345HandlesFixedFullResolutionSaturationAndJustification() {
        val model=MotionSensorModel(Kind.ADXL345_ACCEL)
        model.stimuli=mapOf("accelXG" to .9984,"accelYG" to -.9984)
        assertTrue(model.write(listOf(0x2D,8)))
        assertEquals(256,signed(read(model,0x32,6)))
        assertEquals(-256,signed(read(model,0x32,6),2))
        assertTrue(model.write(listOf(0x31,1)));assertEquals(128,signed(read(model,0x32,2)))
        assertTrue(model.write(listOf(0x31,0x0B)));assertEquals(256,signed(read(model,0x32,2)))
        assertTrue(model.write(listOf(0x31,0x0F)));assertEquals(256 shl 3,signed(read(model,0x32,2)))
        assertTrue(model.write(listOf(0x31,0)));model.stimuli=mapOf("accelXG" to 16.0)
        assertEquals(511,signed(read(model,0x32,2)))
        model.stimuli=mapOf("accelXG" to -16.0);assertEquals(-512,signed(read(model,0x32,2)))
        assertTrue(model.write(listOf(0x2D,0)));assertEquals(List(6){0},read(model,0x32,6))
    }
    @Test fun imuKeepsGyroAndAccelerometerSeparateAndResetsControlRegisters() {
        val model=MotionSensorModel(Kind.LSM6DSOX_IMU)
        model.stimuli=mapOf("gyroXDps" to 43.75,"accelXG" to .999424)
        assertTrue(model.write(listOf(0x10,0x40,0x42))) // accel ±2 g, gyro ±125 dps
        assertEquals(10000,signed(read(model,0x22,6)))
        assertEquals(16384,signed(read(model,0x28,6)))
        assertTrue(model.write(listOf(0x11,0x4C)));assertEquals(625,signed(read(model,0x22,2)))
        assertTrue(model.write(listOf(0x12,0)));assertEquals(listOf(0x71,0x71),read(model,0x22,2))
        assertTrue(model.write(listOf(0x12,1)))
        assertEquals(listOf(4),read(model,0x12,1));assertEquals(List(6){0},read(model,0x22,6))
        assertEquals(listOf(0x6C),read(model,0x0F,1))
    }
    @Test fun bduHoldsBothBytesUntilAWordHasBeenReadAndPowerLossResetsDevice() {
        val device=I2cDeviceModel(0x6A,"gyro",Kind.L3GD20_GYRO)
        assertFalse(device.write(listOf(0x20,0x0F)))
        assertTrue(device.read(1,EnvironmentState()).isEmpty())
        device.powerChanged(true,0)
        val model=device.motion!!;model.stimuli=mapOf("gyroXDps" to 87.5)
        assertTrue(device.write(listOf(0x20,0x0F)));assertTrue(device.write(listOf(0x23,0x80)))
        assertEquals(listOf(0x10),read(model,0x28,1))
        model.stimuli=mapOf("gyroXDps" to 175.0)
        assertEquals(listOf(0x27),read(model,0x29,1))
        assertEquals(20000,signed(read(model,0xA8,2)))
        device.powerChanged(false,100);device.powerChanged(true,200)
        assertEquals(listOf(7),read(model,0x20,1))
        assertEquals(List(6){0},read(model,0xA8,6))
    }
    @Test fun eachReadyWiredLabRunsRealFirmwareThroughTheConnectedSensor() {
        MotionSensorParts.profiles.values.forEach { profile ->
            val circuit=MotionSensorExamples.circuit(profile.kind)
            val boardPart=circuit.components.first { it.kind.isBoard }
            val board=FirmwareBoard(circuit,boardPart.id)
            val session=FirmwareSession(ArduinoSubset.compile(circuit.firmware.single().source,board),board)
            val result=session.advance(30000)
            assertNull("${profile.part}: ${result.error}",result.error)
            val console=result.console.joinToString("\n")
            assertTrue("${profile.part}: $console",console.contains(profile.part))
            assertFalse(console.contains("No sensor data"))
            assertEquals(listOf(profile.addresses.first()),session.fabric.i2cDevices.map { it.address })
        }
    }
    @Test fun actualBusRequiresPullupsPowerGroundAndCorrectDataClockNets() {
        val good=MotionSensorExamples.circuit(Kind.L3GD20_GYRO)
        val board=good.components.first { it.kind.isBoard };val sensor=good.components.first { it.kind==Kind.L3GD20_GYRO }
        fun acknowledge(c:Circuit):String? {
            val runtime=FirmwareBoard(c,board.id)
            val code="void setup(){Wire.begin();Wire.beginTransmission(106);int ack=Wire.endTransmission();}void loop(){delay(100);}"
            val result=FirmwareSession(ArduinoSubset.compile(code,runtime),runtime).advance(3000)
            assertNull(result.error);return result.variables["ack"]
        }
        assertEquals("0.0",acknowledge(good))
        val resistors=good.components.filter { it.kind==Kind.RESISTOR }.map { it.id }.toSet()
        assertEquals("2.0",acknowledge(good.copy(components=good.components.filterNot { it.id in resistors },
            wires=good.wires.filterNot { it.start.componentId in resistors || it.end.componentId in resistors })))
        assertEquals("2.0",acknowledge(good.copy(wires=good.wires.filterNot { it.end==TerminalRef(sensor.id,1) })))
        assertEquals("2.0",acknowledge(good.copy(wires=good.wires.filterNot { it.end==TerminalRef(sensor.id,0) })))
        assertEquals("2.0",acknowledge(good.copy(wires=good.wires.map { wire ->
            if(wire.end.componentId==sensor.id && wire.end.index in 2..3)wire.copy(end=wire.end.copy(index=5-wire.end.index)) else wire })))
        fun sensorSupply(voltage:Double):Circuit {
            val supply=PlacedComponent(kind=Kind.SOURCE,reference="VS",x=0f,y=0f,parameters=mapOf("voltage" to voltage))
            val ground=good.components.first { it.kind==Kind.GROUND }
            return good.copy(components=good.components+supply,wires=good.wires.filterNot { it.end==TerminalRef(sensor.id,0) }+
                listOf(Wire(start=TerminalRef(supply.id,0),end=TerminalRef(sensor.id,0)),
                    Wire(start=TerminalRef(supply.id,1),end=TerminalRef(ground.id,0))))
        }
        assertEquals("2.0",acknowledge(sensorSupply(5.0)))
        // Valid analog supply, but the 3.3 V bus exceeds the carrier's tied 2.5 V I/O rail + 0.3 V.
        assertEquals("2.0",acknowledge(sensorSupply(2.5)))
    }
}
