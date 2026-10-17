package com.indianservers.circuitssimulator
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.firmware.*
import com.indianservers.circuitssimulator.validation.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.security.MessageDigest

@RunWith(Parameterized::class)
class PhaseTwoProjectsTest(private val kind:Kind,private val number:Int){
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}-{1}") fun projects()=BoardRegistry.boards.keys
            .filter {System.getenv("VALIDATION_BOARD")?.let {n->it.name==n} ?: true}
            .flatMap {kind->(1..20).filter {PhaseTwoProjects.supported(it) && (System.getenv("VALIDATION_PROJECT")?.split(',')?.let {ns->it in ns.map(String::toInt)} ?: true)}.map {arrayOf<Any>(kind,it)}}
    }
    @Test fun executeProductionCircuit(){
        val folder=File("../testing/phase2/projects/${kind.name.lowercase()}-${number.toString().padStart(2,'0')}").apply {mkdirs()}
        val circuit=PhaseTwoProjects.circuit(kind,number)
        val raw=CircuitJson.encode(circuit);File(folder,"circuit.json").writeText(raw)
        circuit.firmware.forEach {File(folder,"firmware-${it.boardId}.ino").writeText(it.source)}
        val result=JSONObject().put("board",kind.name).put("number",number).put("status","RUNNING")
            .put("circuitSha256",MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString(""){"%02x".format(it)})
        File(folder,"jvm-results.json").writeText(result.toString(2))
        val started=System.nanoTime()
        try {
            check(CircuitJson.decode(raw)==circuit)
            if(number in PhaseTwoProjects.newNumbers || number==13){
                val progress:(JSONArray)->Unit={records->result.put("scenarios",records);File(folder,"jvm-results.json").writeText(result.toString(2))}
                val scenarios=if(number==13)PhaseTwoParkingValidation.run(circuit,progress)else PhaseTwoLightValidation.run(circuit,number,progress)
                result.put("scenarios",scenarios).put("assertions",(0 until scenarios.length()).sumOf {scenarios.getJSONObject(it).getJSONArray("assertions").length()})
                    .put("independentlyVerifiedSensorInstances",10)
            }else if(number in PhaseTwoProjects.occupancyNumbers){
                CountedChecks.start()
                val p=AdvancedProjects.create(kind,PhaseTwoProjects.occupancyNumbers.getValue(number)).copy(circuit=circuit)
                val evidence=AdvancedProjectChecks.run(p)
                result.put("scenarioRecords",JSONArray(evidence.map {e->JSONObject().put("scenario",e.scenario).put("checks",e.checks)
                    .put("simulatedMicros",e.simulatedMicros).put("expected",JSONObject(e.expected)).put("observed",JSONObject(e.observed))}))
                    .put("assertions",CountedChecks.total()).put("functionalChecks",evidence.sumOf {it.checks}).put("independentlyVerifiedSensorInstances",10)
            }else {
                CountedChecks.start()
                val old=PhaseTwoProjects.priorNumbers.getValue(number)
                val rows=when(old){8->ChemistryProjectValidation.run(kind,old);13->VehicleProjectValidation.run(kind);else->DomainProjectValidation.run(kind,old)}
                result.put("legacyHarnessScenarioRecords",JSONArray(rows)).put("assertions",CountedChecks.total())
                    .put("independentlyVerifiedSensorInstances",JSONObject.NULL)
            }
            result.put("status","PASSED_MODELED_SCOPE")
        }catch(e:Throwable){result.put("status","FAILED_VERIFICATION").put("error",e.stackTraceToString());throw e}
        finally {result.put("elapsedMillis",(System.nanoTime()-started)/1e6);File(folder,"jvm-results.json").writeText(result.toString(2))}
    }
}
