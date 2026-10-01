package com.indianservers.circuitssimulator.data

import android.content.Context
import android.util.AtomicFile
import com.indianservers.circuitssimulator.domain.Circuit
import java.io.File
import java.util.UUID

data class SavedProject(val id:String,val name:String,val modifiedMillis:Long,
                        val unreadable:Boolean=false)

/** Named projects and the recoverable working session are separate versioned circuit documents. */
class ProjectStore(context:Context) {
    private val directory=File(context.filesDir,"projects").apply { mkdirs() }
    private val autosave=File(context.filesDir,"working_session.json")
    private fun project(id:String):File {
        require(Regex("[a-f0-9-]{36}").matches(id))
        return File(directory,"$id.json")
    }
    private fun atomicWrite(target:File,text:String) {
        val atomic=AtomicFile(target)
        val stream=atomic.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch(error:Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }
    private fun atomicRead(target:File):String=AtomicFile(target).openRead()
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
    fun save(id:String?,circuit:Circuit):String {
        val actual=id ?: UUID.randomUUID().toString()
        atomicWrite(project(actual),CircuitJson.encode(circuit))
        return actual
    }
    fun autosave(circuit:Circuit)=atomicWrite(autosave,CircuitJson.encode(circuit))
    fun recovery():Circuit?=runCatching { CircuitJson.decode(atomicRead(autosave)) }.getOrNull()
    fun discardRecovery() { AtomicFile(autosave).delete() }
    fun open(id:String):Circuit=CircuitJson.decode(atomicRead(project(id)))
    fun list():List<SavedProject> = directory.listFiles().orEmpty().mapNotNull { file ->
        val name=when {
            file.name.endsWith(".json.bak") -> file.name.removeSuffix(".bak")
            file.name.endsWith(".json") -> file.name
            else -> return@mapNotNull null
        }
        name.removeSuffix(".json").takeIf { Regex("[a-f0-9-]{36}").matches(it) }
    }.distinct().map { id ->
        val file=project(id)
        runCatching { SavedProject(id,CircuitJson.decode(atomicRead(file)).name,file.lastModified()) }
            .getOrElse { SavedProject(id,"Unreadable project",file.lastModified(),unreadable=true) }
    }.sortedByDescending { it.modifiedMillis }
    fun delete(id:String) { AtomicFile(project(id)).delete() }
    fun duplicate(id:String):String {
        val original=open(id)
        return save(null,original.copy(name="${original.name} Copy"))
    }
}
