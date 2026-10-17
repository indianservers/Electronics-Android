package com.indianservers.circuitssimulator.validation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Counts actual check invocations per independent project worker, including failures. */
object CountedChecks {
    private val calls=ThreadLocal.withInitial {0L}
    fun start(){calls.set(0L)}
    fun total():Long=calls.get() ?: 0L
    fun save(file:File,circuit:File,scenarios:List<String>){
        val hash=MessageDigest.getInstance("SHA-256").digest(circuit.readBytes()).joinToString(""){"%02x".format(it)}
        file.writeText(JSONObject().put("status","PASSED_DOMAIN_MONITORING").put("assertions",total())
            .put("circuitSha256",hash).put("scenarios",JSONArray(scenarios)).toString(2))
    }
    fun check(value:Boolean,lazyMessage:()->Any={"Check failed."}){
        calls.set((calls.get() ?: 0L)+1L)
        kotlin.check(value,lazyMessage)
    }
}
