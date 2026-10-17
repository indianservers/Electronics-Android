package com.indianservers.circuitssimulator
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.validation.DomainProjectValidation
import com.indianservers.circuitssimulator.validation.CountedChecks
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
@RunWith(Parameterized::class)
class DomainMonitoringProjectsTest(private val kind:Kind,private val number:Int) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}-{1}") fun projects()=BoardRegistry.boards.keys
            .filter {System.getenv("VALIDATION_BOARD")?.let {n->it.name==n} ?: true}
            .flatMap { k->listOf(7,9,10,12,19,20).filter {System.getenv("VALIDATION_PROJECT")?.let {n->it==n.toInt()} ?: true}.map {arrayOf<Any>(k,it)} }
    }
    @Test fun executeActualDomainCircuit() {
        val id="${kind.name.lowercase()}-${number.toString().padStart(2,'0')}"
        val dir=File("../testing/engineering-validation/domain-projects/$id").apply {mkdirs()}
        val c=DomainProjectValidation.circuit(kind,number)
        File(dir,"circuit.json").writeText(CircuitJson.encode(c));File(dir,"firmware.ino").writeText(c.firmware.single().source)
        File(dir,"result.txt").writeText("RUNNING\n")
        try {CountedChecks.start();val rows=DomainProjectValidation.run(kind,number)
            CountedChecks.save(File(dir,"assertion-count.json"),File(dir,"circuit.json"),rows)
            File(dir,"result.txt").writeText("PASSED_DOMAIN_MONITORING\n"+rows.joinToString("\n"))}
        catch(e:Throwable){File(dir,"result.txt").writeText("FAILED\n"+e.stackTraceToString());throw e}
    }
}
