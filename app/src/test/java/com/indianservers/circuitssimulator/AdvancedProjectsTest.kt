package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.BoardRegistry
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.validation.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import org.json.JSONObject
import org.json.JSONArray

@RunWith(Parameterized::class)
class AdvancedProjectsTest(private val project:AdvancedProjects.Project) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{index}") fun projects() = BoardRegistry.boards.keys
            .filter { System.getenv("VALIDATION_BOARD")?.let { wanted -> it.name==wanted } ?: true }
            .flatMap { kind -> (1..20).filter { System.getenv("VALIDATION_PROJECT")?.let { n -> it==n.toInt() } ?: true }
                .map { arrayOf(AdvancedProjects.create(kind,it)) } }
    }
    @Test fun executeSavedTenSensorProject() {
        val dir=File("../testing/engineering-validation/projects/${project.id}").apply { mkdirs() }
        File(dir,"circuit.json").writeText(CircuitJson.encode(project.circuit))
        File(dir,"firmware.ino").writeText(project.circuit.firmware.single().source)
        File(dir,"spec.json").writeText(JSONObject().apply {
            put("id",project.id);put("boardId",project.board.kind.name);put("revision",project.board.revision)
            put("scope",project.scenario.purpose);put("lowC",project.scenario.low);put("highC",project.scenario.high)
            put("fullDomainStatus",if(project.scenario.purpose.contains("unsupported"))"BLOCKED_UNMODELED_DOMAIN_FUNCTIONS" else "MODELED_SUBSYSTEM_ONLY")
            put("quorum",project.scenario.quorum);put("normalTemperaturesC",JSONArray(project.normalTemperatures))
            put("expectedNormalReadingsC",JSONArray(project.normalTemperatures.map { kotlin.math.floor(it*2+.5)/2 }))
            put("expectedNormalOutputs",JSONObject().put("alarm",0).put("faults",0).put("motion0",0).put("motion1",0))
            put("faultStimuli",JSONObject().put("minimumC",-55).put("maximumC",125).put("outOfRangeC",126)
                .put("invalidReading","NaN").put("wrongSupplyV",2).put("sensorPowerLossV",0)
                .put("collisionAddress",72).put("pirWarmupMs",60000).put("pirSupplyLossV",2))
            put("motionPins",JSONArray(project.motionPins));put("outputPin",project.outputPin);put("uart",project.port)
            put("modeledSensors",10);put("interfaces",JSONArray(listOf("I2C","UART","GPIO")))
            put("datasheet","https://www.ti.com/lit/ds/symlink/lm75b.pdf")
            put("datasheetRevision","SNIS153D");put("nominalResolutionC",.5)
            put("modelToleranceC",0.0);put("physicalAccuracyClaim",false)
            put("supplyBudget","External logic rail and 5 V rail, each allocated 100 mA; calculated model loads below 20 mA. Not a regulator/thermal model.")
            put("faultInputs",JSONArray(listOf("normal","minimum","maximum","out-of-range","disconnected","wrong-supply",
                "communication-collision","delayed-availability-and-recovery","invalid-reading","simultaneous")))
        }.toString(2))
        File(dir,"scope.txt").writeText(project.scenario.purpose+"\n8 independent LM75B modules, 2 independent PIR modules. I2C + physical UART.\n"+
            "External sensor rails; removable module pull-ups disabled; shared 4.7k pull-ups. LED has 1k series resistance.\n"+
            "Native CPU execution, gas/water chemistry, conversion latency, noise and PIR optics are not modeled.\n")
        File(dir,"result.txt").writeText("RUNNING\n")
        try {
            val result=AdvancedProjectChecks.run(project)
            File(dir,"result.txt").writeText("PASSED_MODELED_SCOPE\n"+result.joinToString("\n") { "${it.scenario},${it.checks},${it.simulatedMicros}" }+"\n")
        } catch(e:Throwable) {
            File(dir,"result.txt").writeText("FAILED\n${e.stackTraceToString()}")
            throw e
        }
    }
}
