package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import org.junit.Assert.*
import org.junit.Test

class ComponentPropertiesTest {
    private fun part(kind:Kind)=PlacedComponent(kind=kind,reference="P",x=0f,y=0f)
    private fun spec(kind:Kind,key:String)=ComponentRegistry.definitions.getValue(kind).parameters.first { it.key==key }
    @Test fun everyCatalogPropertyRoundTripsWithoutUnitOrPrecisionLoss() {
        assertEquals(emptyList<String>(),ComponentProperties.audit())
        ComponentRegistry.catalog.forEach { definition ->
            definition.parameters.forEach { parameter ->
                val part=part(definition.kind)
                for(value in listOf(parameter.min,parameter.max)) {
                    if(ComponentProperties.validate(part,parameter,value).error!=null) continue
                    val result=ComponentProperties.parse(part,parameter,ComponentProperties.input(parameter,value))
                    assertNull("${definition.kind}.${parameter.key}: ${result.error}",result.error)
                    assertEquals(value,result.value!!,maxOf(1e-15,kotlin.math.abs(value)*1e-12))
                }
            }
        }
    }
    @Test fun unitsScientificNotationAndPercentAreExplicit() {
        assertEquals(100e-6,EngineeringUnits.parse("100 uF","F")!!,1e-15)
        assertEquals(100e-6,EngineeringUnits.parse("100uf","F")!!,1e-15)
        assertEquals(1000.0,EngineeringUnits.parse("1khz","Hz")!!,0.0)
        assertEquals(1e-12,EngineeringUnits.parse("1e-12 A","A")!!,1e-24)
        assertEquals(.04,EngineeringUnits.parse("0.04 A/V²","A/V²")!!,1e-12)
        assertEquals(3000.0,EngineeringUnits.parse("3000 ms","ms")!!,0.0)
        assertEquals(3000.0,EngineeringUnits.parse("3 s","ms")!!,0.0)
        assertEquals(-10.0,EngineeringUnits.parse("-10°C","°C")!!,0.0)
        assertEquals(50.0,EngineeringUnits.parse("50% RH","% RH")!!,0.0)
        assertNull(EngineeringUnits.parse("5 A","V"))
        assertNull(EngineeringUnits.parse("1e999 V","V"))
        val pot=part(Kind.POTENTIOMETER)
        assertEquals(.25,ComponentProperties.parse(pot,spec(pot.kind,"position"),"25%").value!!,0.0)
        assertEquals(.25,ComponentProperties.parse(pot,spec(pot.kind,"position"),"25").value!!,0.0)
        val sensor=part(Kind.I2C_TEMP_SENSOR)
        assertEquals(73.0,ComponentProperties.parse(sensor,spec(sensor.kind,"address"),"0x49").value!!,0.0)
        assertNotNull(ComponentProperties.parse(sensor,spec(sensor.kind,"address"),"72.5").error)
    }
    @Test fun rejectsOutOfRangeValuesAndContradictoryRailsInsteadOfClamping() {
        val r=part(Kind.RESISTOR)
        assertNull(ComponentProperties.parse(r,spec(r.kind,"resistance"),"0 Ω").value)
        assertNotNull(ComponentProperties.parse(r,spec(r.kind,"resistance"),"-1 Ω").error)
        val op=part(Kind.OPAMP)
        assertNotNull(ComponentProperties.validate(op,spec(op.kind,"lowerRail"),15.0).error)
        assertNotNull(ComponentProperties.validate(op,spec(op.kind,"upperRail"),-15.0).error)
        assertNotNull(ComponentProperties.validate(op,spec(op.kind,"gain"),Double.NaN).error)
    }
    @Test fun electricalPinIndicesRemainStableAndPackageNumbersAreCorrect() {
        for(kind in listOf(Kind.NPN_BJT,Kind.PNP_BJT)) {
            val p=part(kind)
            assertEquals(listOf("C","B","E"),(0..2).map { ComponentPins.name(p,it) })
            assertEquals(listOf(3,2,1),(0..2).map { ComponentPins.packageNumber(p,it) })
        }
        val mos=part(Kind.NMOS)
        assertEquals(listOf("D","G","S"),(0..2).map { ComponentPins.name(mos,it) })
        assertEquals(listOf(3,2,1),(0..2).map { ComponentPins.packageNumber(mos,it) })
        assertFalse(ComponentPins.canConnect(part(Kind.LM741),7))
        assertFalse(ComponentPins.canConnect(part(Kind.ESP32_S3_DEVKIT),definitionPin(Kind.ESP32_S3_DEVKIT,"IO35")))
        assertFalse(ComponentPins.canConnect(part(Kind.RASPBERRY_PICO_2_W),definitionPin(Kind.RASPBERRY_PICO_2_W,"WL_LED")))
        assertEquals(8,ComponentPins.packageNumber(part(Kind.LM358),7))
        ComponentRegistry.catalog.forEach { d ->
            val p=part(d.kind)
            (0 until p.terminalCount).forEach { i ->
                assertTrue("${d.kind} $i",ComponentPins.name(p,i).isNotBlank())
                assertTrue(ComponentPins.role(p,i).isNotBlank())
            }
        }
    }
    private fun definitionPin(kind:Kind,name:String)=BoardRegistry.boards.getValue(kind).index(name)
    @Test fun legacySharedStimuliAreExplicitAndIndependentIotSensorsStayIndependent() {
        val env=EnvironmentState(temperatureC=40.0)
        assertEquals(25.0,ComponentProperties.environmentAfter(Kind.LM35_SENSOR,"temperatureC",25.0,env).temperatureC,0.0)
        assertEquals(100.0,ComponentProperties.environmentAfter(Kind.LDR,"illuminanceLux",100.0,env).illuminanceLux,0.0)
        assertEquals(env,ComponentProperties.environmentAfter(Kind.TMP36_SENSOR,"temperatureC",75.0,env))
        assertEquals("bf",ComponentProperties.primary(ComponentRegistry.definitions.getValue(Kind.NPN_BJT)).first().key)
    }
    @Test fun validatesConnectionsAllowsRealJumpersAndFollowsSharedNets() {
        val board=part(Kind.ARDUINO_UNO)
        val resistor=part(Kind.RESISTOR)
        val a=TerminalRef(board.id,definitionPin(board.kind,"A4"))
        val sda=TerminalRef(board.id,definitionPin(board.kind,"SDA"))
        val r=TerminalRef(resistor.id,0)
        val circuit=Circuit("Wiring",listOf(board,resistor),listOf(Wire(start=a,end=r)))
        assertTrue(r in ComponentPins.net(circuit,sda))
        assertNotNull(ComponentPins.connectionError(circuit,a,r))
        assertNotNull(ComponentPins.connectionError(circuit,a,a))
        assertNotNull(ComponentPins.connectionError(circuit,a,TerminalRef(resistor.id,99)))
        assertNull(ComponentPins.connectionError(circuit,a,TerminalRef(board.id,definitionPin(board.kind,"A5"))))
        val labelWire=Wire(start=TerminalRef(resistor.id,1),end=TerminalRef(board.id,definitionPin(board.kind,"D2")),label="SIGNAL")
        val joined=circuit.copy(wires=listOf(circuit.wires.first().copy(label="SIGNAL"),labelWire))
        assertTrue(TerminalRef(resistor.id,1) in ComponentPins.net(joined,a))
    }
}
