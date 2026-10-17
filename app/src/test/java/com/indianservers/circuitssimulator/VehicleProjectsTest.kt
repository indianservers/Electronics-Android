package com.indianservers.circuitssimulator
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.validation.VehicleProjectValidation
import com.indianservers.circuitssimulator.validation.CountedChecks
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
@RunWith(Parameterized::class)
class VehicleProjectsTest(private val kind:Kind) {
    companion object {@JvmStatic @Parameterized.Parameters(name="{0}") fun projects()=BoardRegistry.boards.keys
        .filter {System.getenv("VALIDATION_BOARD")?.let {n->it.name==n} ?: true}.map {arrayOf(it)}}
    @Test fun executeTwoControllerVehicleCircuit() {
        val dir=File("../testing/engineering-validation/domain-projects/${kind.name.lowercase()}-13").apply {mkdirs()}
        val c=VehicleProjects.circuit(kind);File(dir,"circuit.json").writeText(CircuitJson.encode(c))
        c.firmware.forEach {File(dir,"${it.boardId}.ino").writeText(it.source)};File(dir,"result.txt").writeText("RUNNING\n")
        try{CountedChecks.start();val rows=VehicleProjectValidation.run(kind)
            CountedChecks.save(File(dir,"assertion-count.json"),File(dir,"circuit.json"),rows)
            File(dir,"result.txt").writeText("PASSED_DOMAIN_MONITORING\n"+rows.joinToString("\n"))}
        catch(e:Throwable){File(dir,"result.txt").writeText("FAILED\n"+e.stackTraceToString());throw e}
    }
}
