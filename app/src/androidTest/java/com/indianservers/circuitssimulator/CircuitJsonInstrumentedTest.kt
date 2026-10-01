package com.indianservers.circuitssimulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CircuitJsonInstrumentedTest {
    @Test fun namedNetRoundTripsAndOlderSchemaStillOpens() {
        val original=SampleCircuits.divider()
        val circuit=original.copy(wires=original.wires.mapIndexed { index,wire ->
            if(index==0) wire.copy(label="VCC") else wire
        })
        assertEquals(circuit,CircuitJson.decode(CircuitJson.encode(circuit)))
        val old=JSONObject(CircuitJson.encode(circuit))
        old.put("schemaVersion",2)
        old.getJSONArray("wires").getJSONObject(0).remove("label")
        assertEquals(null,CircuitJson.decode(old.toString()).wires.first().label)
        old.getJSONArray("wires").getJSONObject(0).put("label","1 invalid")
        assertTrue(runCatching { CircuitJson.decode(old.toString()) }.isFailure)
    }

    @Test fun everyBundledSampleRoundTrips() {
        val samples=listOf(SampleCircuits.led(),SampleCircuits.lamp(),SampleCircuits.divider(),
            SampleCircuits.rc(),SampleCircuits.rl(),SampleCircuits.generator(),
            SampleCircuits.rcLowPass(),SampleCircuits.rcHighPass(),SampleCircuits.rlLowPass(),
            SampleCircuits.lightDivider(),SampleCircuits.potentiometerDemo(),
            SampleCircuits.fuseFault(),SampleCircuits.nonInvertingOpAmp(),
            SampleCircuits.npnSwitch(),SampleCircuits.nmosSwitch())
        samples.forEach { assertEquals(it.name,it,CircuitJson.decode(CircuitJson.encode(it))) }
    }

    @Test fun rejectsMalformedOrOversizedImportedDocuments() {
        val original=JSONObject(CircuitJson.encode(SampleCircuits.lightDivider()))
        fun rejected(change:(JSONObject)->Unit):Boolean {
            val document=JSONObject(original.toString())
            change(document)
            return runCatching { CircuitJson.decode(document.toString()) }.isFailure
        }
        assertTrue(rejected { it.getJSONArray("components").getJSONObject(0)
            .getJSONObject("parameters").put("unrecognized",1.0) })
        assertTrue(rejected { it.getJSONArray("components").getJSONObject(0).put("x",1e100) })
        assertTrue(rejected { it.getJSONObject("simulationSettings").put("maxIterations",0) })
        assertTrue(rejected { it.getJSONArray("wires").put(it.getJSONArray("wires").getJSONObject(0)) })
        assertTrue(runCatching { CircuitJson.decode(" ".repeat(CircuitJson.MAX_DOCUMENT_CHARS+1)) }.isFailure)
    }

    @Test fun reactiveCircuitRoundTripsWithTopologyAndProperties() {
        val battery=PlacedComponent(kind=Kind.BATTERY,reference="B1",x=100f,y=230f,
            parameters=mapOf("voltage" to 12.0))
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=320f,y=200f,
            rotation=90,parameters=mapOf("resistance" to 4700.0,"rating" to .5,"tolerance" to 1.0))
        val capacitor=PlacedComponent(kind=Kind.CAPACITOR,reference="C1",x=500f,y=200f,
            parameters=mapOf("capacitance" to 220e-6,"initialVoltage" to 1.0,"maxVoltage" to 35.0))
        val switch=PlacedComponent(kind=Kind.SWITCH,reference="SW1",x=200f,y=200f,closed=false)
        val led=PlacedComponent(kind=Kind.LED,reference="D1",x=640f,y=200f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=500f,y=470f)
        fun wire(a:PlacedComponent,ap:Int,b:PlacedComponent,bp:Int)=Wire(
            start=TerminalRef(a.id,ap),end=TerminalRef(b.id,bp))
        val circuit=Circuit("Serialization check",listOf(battery,resistor,capacitor,switch,led,ground),
            listOf(wire(battery,0,switch,0),wire(switch,1,resistor,0),wire(resistor,1,capacitor,0),
                wire(capacitor,1,led,0),wire(led,1,battery,1),wire(battery,1,ground,0)))
        assertEquals(circuit,CircuitJson.decode(CircuitJson.encode(circuit)))
    }

    @Test fun realPartIdentityRoundTripsAndOlderDocumentOpens() {
        val transistor=PlacedComponent(kind=Kind.NPN_BJT,reference="Q1",x=100f,y=100f)
        val circuit=Circuit("Real part",listOf(transistor),emptyList())
        val encoded=CircuitJson.encode(circuit)
        val restored=CircuitJson.decode(encoded).components.single()
        assertEquals(transistor.databaseId,restored.databaseId)
        assertEquals(transistor.modelVersion,restored.modelVersion)
        val old=JSONObject(encoded)
        old.put("schemaVersion",1)
        old.getJSONArray("components").getJSONObject(0).remove("databaseId")
        old.getJSONArray("components").getJSONObject(0).remove("modelVersion")
        assertNotNull(CircuitJson.decode(old.toString()).components.single().databaseId)
    }
}
