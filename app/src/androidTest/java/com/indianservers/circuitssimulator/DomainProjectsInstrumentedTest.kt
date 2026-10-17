package com.indianservers.circuitssimulator
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.firmware.*
import com.indianservers.circuitssimulator.validation.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
@RunWith(AndroidJUnit4::class)
class DomainProjectsInstrumentedTest {
    companion object { private val rows=JSONArray() }
    @Test fun airQuality()=runNumber(7)
    @Test fun waterTreatment()=runNumber(8)
    @Test fun heatAndCoDetection()=runNumber(9)
    @Test fun solarAndBattery()=runNumber(10)
    @Test fun robotics()=runNumber(12)
    @Test fun vehicleCan()=runNumber(13)
    @Test fun chemicalProcess()=runNumber(16)
    @Test fun aquaculture()=runNumber(18)
    @Test fun predictiveMaintenance()=runNumber(19)
    @Test fun disasterDetection()=runNumber(20)
    private fun runNumber(number:Int) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val report=File(context.filesDir,"domain-project-results.json")
        val failures=mutableListOf<String>()
        if(rows.length()==0)report.writeText("[]")
        // Each vehicle fixture owns its two-MCU scheduler/fabric. Bound those
        // heavier fixtures to two workers; all checks still execute per board.
        executeIndependentProjects(BoardRegistry.boards.keys,if(number==13)2 else 3) {kind->
            val id="${kind.name.lowercase()}-${number.toString().padStart(2,'0')}"
            val started=System.nanoTime()
            val row=JSONObject().put("project",id).put("board",kind.name).put("number",number)
            try {
                val expected=when(number){in ChemistryProjects.numbers->ChemistryProjects.circuit(kind,number);13->VehicleProjects.circuit(kind);else->DomainProjectValidation.circuit(kind,number)}
                val raw=instrumentation.context.assets.open("domain-projects/$id/circuit.json").use {it.readBytes()}
                val saved=CircuitJson.decode(raw.toString(Charsets.UTF_8));check(saved==expected){"JVM-tested circuit differs from Android template"}
                val deviceFile=File(context.filesDir,"domain-project-$id.json");deviceFile.writeText(CircuitJson.encode(saved))
                check(CircuitJson.decode(deviceFile.readText())==saved)
                CountedChecks.start()
                val evidence=when(number){in ChemistryProjects.numbers->ChemistryProjectValidation.run(kind,number);13->VehicleProjectValidation.run(kind);else->DomainProjectValidation.run(kind,number)}
                row.put("status","PASSED_DOMAIN_MONITORING").put("scenarios",JSONArray(evidence)).put("assertions",CountedChecks.total())
                row.put("circuitAssetSha256",MessageDigest.getInstance("SHA-256").digest(raw).joinToString(""){"%02x".format(it)})
                val sensorKinds=setOf(Kind.I2C_TEMP_SENSOR,Kind.BH1750_LIGHT,Kind.ADXL345_ACCEL,Kind.LSM6DSOX_IMU,Kind.SCD41_CO2,Kind.INA219_POWER,
                    Kind.EZO_PH,Kind.EZO_EC,Kind.EZO_DO,Kind.ZE07_CO,Kind.MPX5010_PRESSURE,Kind.PIR_SENSOR,Kind.ULTRASONIC)
                row.put("sensorCount",saved.components.count {it.kind in sensorKinds})
            } catch(e:Throwable){synchronized(failures){failures+="$id: ${e.message}"};row.put("status","FAILED").put("error",e.stackTraceToString())}
            row.put("elapsedMillis",(System.nanoTime()-started)/1_000_000.0)
            synchronized(rows){rows.put(row);report.writeText(rows.toString(2))}
        }
        check(failures.isEmpty()){failures.joinToString("\n")}
    }
}
