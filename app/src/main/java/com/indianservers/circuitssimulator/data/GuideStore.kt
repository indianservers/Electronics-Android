package com.indianservers.circuitssimulator.data

import android.content.Context
import android.util.AtomicFile
import com.indianservers.circuitssimulator.domain.Circuit
import com.indianservers.circuitssimulator.guide.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The in-progress lesson is stored independently from the user's working circuit. */
class GuideStore(context:Context) {
    private val active=AtomicFile(File(context.filesDir,"active_guide.json"))
    private val prefs=context.getSharedPreferences("guide_progress",0)

    fun save(session:GuideSession,circuit:Circuit) {
        val json=JSONObject().put("version",1).put("lessonId",session.lessonId)
            .put("stepIndex",session.stepIndex).put("started",session.startedAtMillis)
            .put("completed",session.completedAtMillis ?: JSONObject.NULL)
            .put("practice",session.practice).put("circuit",CircuitJson.encode(circuit))
        val hints=JSONObject();session.hintLevels.forEach { (id,level) -> hints.put(id,level) }
        json.put("hints",hints)
        val events=JSONArray();session.eventLog.forEach { event -> events.put(JSONObject()
            .put("type",event.type.name).put("detail",event.detail).put("at",event.atMillis)) }
        json.put("events",events)
        val stream=active.startWrite()
        try { stream.write(json.toString().toByteArray(Charsets.UTF_8));active.finishWrite(stream) }
        catch(e:Exception) { active.failWrite(stream);throw e }
    }

    fun load():Pair<GuideSession,Circuit>?=runCatching {
        val json=JSONObject(active.openRead().bufferedReader().use { it.readText() })
        val id=json.getString("lessonId")
        require(id in LessonCatalog.byId)
        val hints=json.optJSONObject("hints") ?: JSONObject()
        val hintMap=(0 until hints.length()).associate { i -> val key=hints.keys().asSequence().toList()[i];key to hints.getInt(key) }
        val events=json.optJSONArray("events") ?: JSONArray()
        val log=(0 until events.length()).mapNotNull { i -> runCatching {
            val e=events.getJSONObject(i)
            GuideEvent(GuideEventType.valueOf(e.getString("type")),e.optString("detail"),e.optLong("at"))
        }.getOrNull() }
        GuideSession(id,json.optInt("stepIndex"),hintMap,json.optLong("started"),
            if(json.isNull("completed")) null else json.optLong("completed"),log,
            practice=json.optBoolean("practice")) to CircuitJson.decode(json.getString("circuit"))
    }.getOrNull()

    fun clear()=active.delete()
    fun completion(id:String):Long=prefs.getLong("done_$id",0L)
    fun markComplete(id:String,at:Long) { prefs.edit().putLong("done_$id",at).apply() }
    fun onboardingSeen()=prefs.getBoolean("onboarding_seen",false)
    fun markOnboardingSeen() { prefs.edit().putBoolean("onboarding_seen",true).apply() }
}
