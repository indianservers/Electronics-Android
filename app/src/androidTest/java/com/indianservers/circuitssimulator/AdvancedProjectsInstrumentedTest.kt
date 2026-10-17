package com.indianservers.circuitssimulator

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import kotlin.math.floor
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AdvancedProjectsInstrumentedTest {
    @Test fun executeAllSavedProjectsOnAndroidAndRoundTripPrivateStorage() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val assets=instrumentation.context.assets
        val context=instrumentation.targetContext
        val evidence=JSONArray();val failures=mutableListOf<String>()
        val ids=assets.list("")!!.filter { assets.list(it)?.contains("circuit.json")==true }.sorted()
        check(ids.size==BoardRegistry.boards.size*20) { "Expected ${BoardRegistry.boards.size*20} saved projects; found ${ids.size}" }
        val report=File(context.filesDir,"advanced-project-results.json")
        fun run(c:Circuit,faults:Int,temps:List<Double>,alarm:Int):FirmwareSession {
            val board=FirmwareBoard(c,"B1")
            val session=FirmwareSession(ArduinoSubset.compile(c.firmware.single().source,board),board)
            repeat(3) { check(session.advance(10_000_000).error==null) }
            val result=session.advance(20_000)
            check(result.error==null) { result.error.toString() }
            fun value(k:String)=session.variables().getValue(k).toDouble()
            check(value("cycles")>=1 && value("faults")==faults.toDouble())
            if(faults==0)temps.forEachIndexed { i,t -> check(value("t$i")==floor(t*2+.5)/2) }
            check(value("alarm")==alarm.toDouble())
            val current=abs(board.latest!!.readings.getValue("ALARM").current)
            check(if(alarm==0)current<1e-6 else current>1e-4)
            check(session.fabric.displayText()["TELEMETRY"].orEmpty().contains(faults.toString()))
            return session
        }
        executeIndependentProjects(ids) { id ->
            val started=System.nanoTime()
            val row=JSONObject().put("project",id)
            try {
                val text=assets.open("$id/circuit.json").bufferedReader().use { it.readText() }
                val spec=JSONObject(assets.open("$id/spec.json").bufferedReader().use { it.readText() })
                val circuit=CircuitJson.decode(text)
                val temps=(0..7).map { spec.getJSONArray("normalTemperaturesC").getDouble(it) }
                run(circuit,0,temps,0)
                val active=circuit.copy(components=circuit.components.map { p -> when(p.kind) {
                    Kind.I2C_TEMP_SENSOR -> p.copy(parameters=p.parameters+("temperatureC" to 125.0))
                    Kind.PIR_SENSOR -> p.copy(parameters=p.parameters+("motion" to 1.0))
                    else -> p
                } })
                run(active,0,List(8){125.0},1)
                val fault=circuit.copy(wires=circuit.wires.filterNot { it.end==TerminalRef("T0",2) })
                run(fault,1,temps,1)
                val saved=File(context.cacheDir,"advanced-project-$id.json")
                saved.writeText(CircuitJson.encode(circuit))
                val restored=CircuitJson.decode(saved.readText())
                check(restored==circuit)
                val session=run(restored,0,temps,0)
                session.reset();repeat(3) { check(session.advance(10_000_000).error==null) }
                check(session.advance(20_000).error==null)
                check(session.variables().getValue("faults").toDouble()==0.0)
                row.put("status","PASSED_MODELED_SCOPE").put("scenarios",5).put("sensors",10)
            } catch(e:Throwable) {
                synchronized(failures){failures+="$id: ${e.message}"};row.put("status","FAILED").put("error",e.stackTraceToString())
            }
            row.put("elapsedMillis",(System.nanoTime()-started)/1_000_000.0)
            synchronized(evidence){evidence.put(row);report.writeText(evidence.toString(2))}
        }
        check(failures.isEmpty()) { failures.joinToString("\n") }
    }
}
