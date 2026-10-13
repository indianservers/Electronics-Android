package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test

class IotSensorModelTest {
    private fun sensor(kind:Kind,temperature:Double=25.0)=PlacedComponent(
        kind=kind,reference="S1",x=0f,y=0f).let {
        it.copy(parameters=it.parameters+("temperatureC" to temperature))
    }

    @Test fun manufacturerNominalOutputsAt25C() {
        val expected=mapOf(Kind.TMP35_SENSOR to .25,Kind.TMP36_SENSOR to .75,
            Kind.TMP37_SENSOR to .5,Kind.MCP9700_SENSOR to .75,Kind.MCP9701_SENSOR to .8875,
            Kind.AD22100_SENSOR to 1.9375,Kind.AD22103_SENSOR to .95,Kind.HIH4030_SENSOR to 2.35093)
        assertEquals(8,IotSensorParts.profiles.size)
        expected.forEach { (kind,voltage) ->
            assertEquals(kind.name,voltage,iotSensorOutputVoltage(sensor(kind),if(kind==Kind.AD22103_SENSOR) 3.3 else 5.0)!!,1e-9)
        }
    }

    @Test fun datasheetEndpointsAndSupplyScaling() {
        assertEquals(.1,iotSensorOutputVoltage(sensor(Kind.TMP36_SENSOR,-40.0),2.7)!!,1e-9)
        assertEquals(1.75,iotSensorOutputVoltage(sensor(Kind.MCP9700_SENSOR,125.0),3.3)!!,1e-9)
        assertEquals(.205,iotSensorOutputVoltage(sensor(Kind.MCP9701_SENSOR,-10.0),5.0)!!,1e-9)
        assertEquals(.25,iotSensorOutputVoltage(sensor(Kind.AD22100_SENSOR,-50.0),5.0)!!,1e-9)
        assertEquals(4.75,iotSensorOutputVoltage(sensor(Kind.AD22100_SENSOR,150.0),5.0)!!,1e-9)
        assertEquals(3.05,iotSensorOutputVoltage(sensor(Kind.AD22103_SENSOR,100.0),3.3)!!,1e-9)
        for(kind in listOf(Kind.AD22100_SENSOR,Kind.AD22103_SENSOR,Kind.HIH4030_SENSOR)) {
            val profile=IotSensorParts.profiles.getValue(kind)
            val low=iotSensorOutputVoltage(sensor(kind),profile.supplyMin)!!
            val high=iotSensorOutputVoltage(sensor(kind),profile.supplyMax)!!
            assertEquals(profile.supplyMax/profile.supplyMin,high/low,1e-9)
        }
        val humidity=sensor(Kind.HIH4030_SENSOR,70.0)
        assertEquals(2.20027,iotSensorOutputVoltage(humidity,5.0)!!,1e-9)
    }

    @Test fun invalidOperatingConditionsReleaseOutput() {
        IotSensorParts.profiles.values.forEach { profile ->
            val part=sensor(profile.kind)
            assertNull(iotSensorOutputVoltage(part,profile.supplyMin-.01))
            assertNull(iotSensorOutputVoltage(part,profile.supplyMax+.01))
            assertNull(iotSensorOutputVoltage(part,-5.0))
            assertNull(iotSensorOutputVoltage(part,Double.NaN))
            assertNull(iotSensorOutputVoltage(sensor(profile.kind,profile.temperatureMin-1),profile.supplyMax))
        }
    }

    private fun circuit(kind:Kind):Pair<Circuit,PlacedComponent> {
        val part=sensor(kind)
        val profile=IotSensorParts.profiles.getValue(kind)
        val supply=if(kind==Kind.AD22103_SENSOR) 3.3 else 5.0
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=0f,y=0f,parameters=mapOf("voltage" to supply))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="G1",x=0f,y=0f)
        fun wire(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))
        return Circuit("IoT sensor",listOf(part,source,ground),listOf(
            wire(source,0,part,profile.supplyPin),wire(source,1,part,profile.groundPin),wire(source,1,ground,0))) to part
    }

    @Test fun everySensorSolvesInDcAndTransientWithItsPhysicalPins() {
        IotSensorParts.profiles.values.forEach { profile ->
            val (circuit,part)=circuit(profile.kind)
            assertEquals(3,part.terminalCount)
            val expected=iotSensorOutputVoltage(part,if(part.kind==Kind.AD22103_SENSOR) 3.3 else 5.0)!!
            val dc=DcSolver().solve(circuit)
            assertNull("${part.kind}: ${dc.error}",dc.error)
            assertEquals(expected,dc.nodeVoltages.getValue(TerminalRef(part.id,profile.outputPin)),1e-5)
            assertEquals(profile.current,dc.readings.getValue(part.id).current,1e-9)
            val transient=TransientSolver().simulate(circuit,.002,.001)
            assertNull("${part.kind}: ${transient.error}",transient.error)
            assertEquals(expected,transient.frames.last().nodeVoltages.getValue(TerminalRef(part.id,profile.outputPin)),1e-5)
            val definition=ComponentRegistry.definitions.getValue(part.kind)
            assertEquals(profile.pins,definition.datasheet.physicalPinOrder)
            assertTrue(ComponentRegistry.search(profile.part).any { it.kind==part.kind })
        }
        assertEquals(listOf("GND","VOUT","V+"),IotSensorParts.pinNames.getValue(Kind.HIH4030_SENSOR))
    }

    @Test fun outputDrivesAdcLoadAndUnpoweredSensorDoesNotGenerateVoltage() {
        val (base,part)=circuit(Kind.TMP36_SENSOR)
        val load=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=0f,y=0f,
            parameters=mapOf("resistance" to 100000.0))
        val loaded=base.copy(components=base.components+load,wires=base.wires+listOf(
            Wire(start=TerminalRef(part.id,1),end=TerminalRef(load.id,0)),
            Wire(start=TerminalRef(part.id,2),end=TerminalRef(load.id,1))))
        val result=DcSolver().solve(loaded)
        assertNull(result.error)
        assertEquals(.75*100000.0/100001.0,result.nodeVoltages.getValue(TerminalRef(part.id,1)),1e-6)
        val unpowered=loaded.copy(components=loaded.components.map {
            if(it.kind==Kind.SOURCE) it.copy(parameters=mapOf("voltage" to 0.0)) else it
        })
        val off=DcSolver().solve(unpowered)
        assertNull(off.error)
        assertEquals(0.0,off.nodeVoltages.getValue(TerminalRef(part.id,1)),1e-9)
        assertNotNull(AcSolver().sweep(loaded,10.0,1000.0,10,SweepScale.LOGARITHMIC).error)
    }
}
