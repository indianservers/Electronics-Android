package com.indianservers.circuitssimulator

import android.util.AtomicFile
import android.util.Xml
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import java.io.File

/** Persist actual JUnit callbacks on device even if the host ADB connection disappears. */
class DeviceEvidenceListener : RunListener() {
    private data class Row(val description: Description, val started: Long = System.nanoTime(),
        var status: String = "running", var seconds: Double = 0.0, var trace: String = "")
    private val rows = linkedMapOf<String, Row>()
    private var complete = false
    private fun key(d: Description) = "${d.className}#${d.methodName}"
    @Synchronized override fun testRunStarted(description: Description) { rows.clear(); complete=false; persist() }
    @Synchronized override fun testStarted(description: Description) { rows[key(description)]=Row(description); persist() }
    @Synchronized override fun testFailure(failure: Failure) {
        rows.getOrPut(key(failure.description)){Row(failure.description)}.apply { status="failed";trace=failure.trace }
        persist()
    }
    @Synchronized override fun testAssumptionFailure(failure: Failure) {
        rows.getOrPut(key(failure.description)){Row(failure.description)}.apply { status="skipped";trace=failure.trace }
        persist()
    }
    @Synchronized override fun testIgnored(description: Description) {
        rows[key(description)]=Row(description,status="skipped");persist()
    }
    @Synchronized override fun testFinished(description: Description) {
        rows.getOrPut(key(description)){Row(description)}.apply {
            seconds=(System.nanoTime()-started)/1_000_000_000.0
            if(status=="running")status="passed"
        }
        persist()
    }
    @Synchronized override fun testRunFinished(result: Result) { complete=true;persist() }
    private fun persist() {
        val file=AtomicFile(File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"instrumented-device-results.xml"))
        val stream=file.startWrite()
        try {
            val xml=Xml.newSerializer();xml.setOutput(stream,"UTF-8");xml.startDocument("UTF-8",true)
            xml.startTag(null,"testsuite")
            xml.attribute(null,"name","on-device-instrumentation")
            mapOf("tests" to rows.size,"failures" to rows.values.count {it.status=="failed"},
                "errors" to rows.values.count {it.status=="running"},"skipped" to rows.values.count {it.status=="skipped"},
                "time" to rows.values.sumOf {it.seconds}).forEach {(k,v)->xml.attribute(null,k,v.toString())}
            xml.startTag(null,"properties");xml.startTag(null,"property")
            xml.attribute(null,"name","deviceRunComplete");xml.attribute(null,"value",complete.toString())
            xml.endTag(null,"property");xml.endTag(null,"properties")
            rows.values.forEach { row ->
                xml.startTag(null,"testcase");xml.attribute(null,"classname",row.description.className ?: "unknown")
                xml.attribute(null,"name",row.description.methodName ?: row.description.displayName)
                xml.attribute(null,"time",row.seconds.toString())
                val child=when(row.status){"failed"->"failure";"skipped"->"skipped";"running"->"error";else->null}
                if(child!=null){xml.startTag(null,child);xml.text(if(row.status=="running")"Test has not finished" else row.trace);xml.endTag(null,child)}
                xml.endTag(null,"testcase")
            }
            xml.endTag(null,"testsuite");xml.endDocument();xml.flush();file.finishWrite(stream)
        } catch(t: Throwable) {file.failWrite(stream);throw t}
    }
}
