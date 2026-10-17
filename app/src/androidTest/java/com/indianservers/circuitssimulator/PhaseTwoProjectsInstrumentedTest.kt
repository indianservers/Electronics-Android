package com.indianservers.circuitssimulator
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.firmware.*
import com.indianservers.circuitssimulator.validation.*
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class PhaseTwoProjectsInstrumentedTest {
    @Test fun industrialEnvironment()=runNumber(1)
    @Test fun greenhouse()=runNumber(3)
    @Test fun medicalEnvironment()=runNumber(8)
    @Test fun motorCondition()=runNumber(5)
    @Test fun homeSecurity()=runNumber(6)
    @Test fun buildingAutomation()=runNumber(7)
    @Test fun waterQuality()=runNumber(9)
    @Test fun energyMonitoring()=runNumber(10)
    @Test fun gasMonitoring()=runNumber(12)
    @Test fun parking()=runNumber(13)
    @Test fun heatAndCo()=runNumber(15)
    @Test fun streetlight()=runNumber(16)
    @Test fun vehicleDiagnostics()=runNumber(17)
    @Test fun coldChain()=runNumber(18)
    @Test fun disasterWarning()=runNumber(19)
    @Test fun robotics()=runNumber(20)
    private fun runNumber(number:Int){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val failures=mutableListOf<String>()
        executeIndependentProjects(BoardRegistry.boards.keys,if(number==17)1 else 2){kind->
            val id="${kind.name.lowercase()}-${number.toString().padStart(2,'0')}"
            val file=File(context.filesDir,"phase2-$id-results.json")
            val row=JSONObject().put("project",id).put("board",kind.name).put("number",number).put("status","RUNNING")
            val started=System.nanoTime()
            try {
                val raw=instrumentation.context.assets.open("phase2-projects/$id/circuit.json").use {it.readBytes()}
                val decoded=CircuitJson.decode(raw.toString(Charsets.UTF_8));check(decoded==PhaseTwoProjects.circuit(kind,number))
                val saved=File(context.filesDir,"phase2-$id-circuit.json");saved.writeBytes(raw)
                check(CircuitJson.decode(saved.readText())==decoded)
                row.put("circuitAssetSha256",MessageDigest.getInstance("SHA-256").digest(raw).joinToString(""){"%02x".format(it)})
                if(number in PhaseTwoProjects.newNumbers || number==13){
                    val progress:(JSONArray)->Unit={records->row.put("scenarios",records);file.writeText(row.toString(2))}
                    val scenarios=if(number==13)PhaseTwoParkingValidation.run(decoded,progress)else PhaseTwoLightValidation.run(decoded,number,progress)
                    row.put("scenarios",scenarios).put("assertions",(0 until scenarios.length()).sumOf {scenarios.getJSONObject(it).getJSONArray("assertions").length()})
                        .put("independentlyVerifiedSensorInstances",10)
                }else if(number in PhaseTwoProjects.occupancyNumbers){
                    CountedChecks.start()
                    val p=AdvancedProjects.create(kind,PhaseTwoProjects.occupancyNumbers.getValue(number)).copy(circuit=decoded)
                    val evidence=AdvancedProjectChecks.run(p)
                    row.put("scenarioRecords",JSONArray(evidence.map {e->JSONObject().put("scenario",e.scenario).put("checks",e.checks)
                        .put("simulatedMicros",e.simulatedMicros).put("expected",JSONObject(e.expected)).put("observed",JSONObject(e.observed))}))
                        .put("assertions",CountedChecks.total()).put("functionalChecks",evidence.sumOf {it.checks}).put("independentlyVerifiedSensorInstances",10)
                }else {
                    CountedChecks.start()
                    val old=PhaseTwoProjects.priorNumbers.getValue(number)
                    row.put("legacyHarnessScenarioRecords",JSONArray(when(old){8->ChemistryProjectValidation.run(kind,old);13->VehicleProjectValidation.run(kind);else->DomainProjectValidation.run(kind,old)}))
                        .put("assertions",CountedChecks.total()).put("independentlyVerifiedSensorInstances",JSONObject.NULL)
                }
                row.put("status","PASSED_MODELED_SCOPE")
            }catch(e:Throwable){row.put("status","FAILED_VERIFICATION").put("error",e.stackTraceToString());synchronized(failures){failures+="$id ${e.message}"}}
            finally{row.put("elapsedMillis",(System.nanoTime()-started)/1e6);file.writeText(row.toString(2))}
        }
        check(failures.isEmpty()){failures.joinToString("\n")}
    }
}
