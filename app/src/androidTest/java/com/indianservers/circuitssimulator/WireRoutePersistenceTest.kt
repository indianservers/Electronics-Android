package com.indianservers.circuitssimulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WireRoutePersistenceTest {
    @Test fun manualRoutesRoundTripAndOldProjectsLoad() {
        val sample=SampleCircuits.divider()
        val circuit=sample.copy(wires=sample.wires.map { it.copy(waypoints=listOf(WirePoint(-32f,80f),WirePoint(120f,80f))) })
        assertEquals(circuit,CircuitJson.decode(CircuitJson.encode(circuit)))
        val old=JSONObject(CircuitJson.encode(circuit)).put("schemaVersion",5)
        val wires=old.getJSONArray("wires")
        (0 until wires.length()).forEach { wires.getJSONObject(it).remove("waypoints") }
        assertTrue(CircuitJson.decode(old.toString()).wires.all { it.waypoints.isEmpty() })
        wires.getJSONObject(0).put("waypoints",org.json.JSONArray().put(JSONObject().put("x",1e100).put("y",0)))
        assertTrue(runCatching { CircuitJson.decode(old.toString()) }.isFailure)
    }
}
