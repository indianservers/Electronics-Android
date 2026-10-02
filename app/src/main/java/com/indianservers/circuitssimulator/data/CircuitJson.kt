package com.indianservers.circuitssimulator.data

import com.indianservers.circuitssimulator.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Stable, versioned document containing topology and presentation coordinates only. */
object CircuitJson {
    const val SCHEMA_VERSION = 4
    const val MAX_DOCUMENT_CHARS = 2_000_000
    const val MAX_COMPONENTS = 512
    const val MAX_WIRES = 4096
    fun encode(circuit: Circuit): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("name", circuit.name)
        put("savedAtUtc", java.time.Instant.now().toString())
        put("simulationSettings", JSONObject().apply {
            put("analysis", circuit.settings.analysis)
            put("tolerance", circuit.settings.tolerance)
            put("maxIterations", circuit.settings.maxIterations)
        })
        put("components", JSONArray().apply { circuit.components.forEach { p -> put(JSONObject().apply {
            put("id", p.id); put("kind", p.kind.name); put("reference", p.reference)
            put("x", p.x.toDouble()); put("y", p.y.toDouble()); put("rotation", p.rotation)
            put("sizeScale",p.sizeScale.toDouble())
            put("closed", p.closed)
            p.databaseId?.let { put("databaseId",it) }
            put("modelVersion",p.modelVersion)
            put("parameters", JSONObject(p.parameters))
        }) } })
        put("environment", JSONObject().apply {
            put("temperatureC", circuit.environment.temperatureC)
            put("humidityPercent", circuit.environment.humidityPercent)
            put("illuminanceLux", circuit.environment.illuminanceLux)
            put("distanceCm", circuit.environment.distanceCm)
            put("motion", circuit.environment.motion)
            put("pressureHpa", circuit.environment.pressureHpa)
            put("soundDb", circuit.environment.soundDb)
        })
        put("firmware", JSONArray().apply { circuit.firmware.forEach { item -> put(JSONObject().apply {
            put("boardId", item.boardId);put("language", item.language.name)
            put("source", item.source);put("usbPower", item.usbPower)
        }) } })
        put("wires", JSONArray().apply { circuit.wires.forEach { w -> put(JSONObject().apply {
            put("id", w.id); put("startComponent", w.start.componentId); put("startPin", w.start.index)
            put("endComponent", w.end.componentId); put("endPin", w.end.index)
            w.label?.let { put("label",it) }
        }) } })
    }.toString()

    fun decode(text: String): Circuit {
        require(text.length<=MAX_DOCUMENT_CHARS) { "Project file is too large" }
        val o = JSONObject(text)
        val version=o.getInt("schemaVersion")
        require(version in 1..SCHEMA_VERSION) { "Unsupported circuit schema" }
        val settings = o.optJSONObject("simulationSettings")
        val array = o.getJSONArray("components")
        require(array.length()<=MAX_COMPONENTS) { "Project has too many components" }
        val components = (0 until array.length()).map { i ->
            val p = array.getJSONObject(i); val kind = Kind.valueOf(p.getString("kind"))
            val id=p.getString("id")
            val reference=p.getString("reference")
            val x=p.getDouble("x");val y=p.getDouble("y")
            val rotation=p.getInt("rotation")
            val sizeScale=p.optDouble("sizeScale",1.0)
            require(id.isNotBlank() && id.length<=128 && reference.isNotBlank() && reference.length<=32) {
                "Invalid component identity"
            }
            require(x.isFinite() && y.isFinite() && kotlin.math.abs(x)<=1e6 && kotlin.math.abs(y)<=1e6) {
                "Invalid component coordinates"
            }
            require(rotation in 0..270 && rotation%90==0) { "Invalid component rotation" }
            require(sizeScale.isFinite() && sizeScale in 0.6..1.8) { "Invalid component size" }
            val values = p.getJSONObject("parameters")
            val parameters=values.keys().asSequence().associateWith { key ->
                val number=values.getDouble(key)
                val spec=ComponentRegistry.definitions.getValue(kind).parameters.firstOrNull { it.key==key }
                require(spec!=null && number.isFinite() && number in spec.min..spec.max) {
                    "Invalid ${kind.name} parameter: $key"
                }
                number
            }
            PlacedComponent(id = id, kind = kind, reference = reference,
                x = x.toFloat(), y = y.toFloat(),
                rotation = rotation, sizeScale=sizeScale.toFloat(), closed = p.optBoolean("closed", true),
                parameters = parameters,
                databaseId=p.optString("databaseId").takeIf { it.isNotEmpty() }
                    ?: ComponentRegistry.definitions.getValue(kind).datasheet.let { source ->
                        if(source.manufacturer!=null && source.partNumber!=null) "${source.manufacturer}:${source.partNumber}" else null },
                modelVersion=p.optInt("modelVersion",1).also { require(it in 1..1000) { "Invalid model version" } })
        }
        val wiresArray = o.getJSONArray("wires")
        require(wiresArray.length()<=MAX_WIRES) { "Project has too many wires" }
        val wires = (0 until wiresArray.length()).map { i ->
            val w = wiresArray.getJSONObject(i)
            val label=w.optString("label").takeIf { it.isNotBlank() }
            require(label==null || Regex("[A-Za-z][A-Za-z0-9_ -]{0,23}").matches(label)) {
                "Invalid net label"
            }
            Wire(w.getString("id"), TerminalRef(w.getString("startComponent"), w.getInt("startPin")),
                TerminalRef(w.getString("endComponent"), w.getInt("endPin")),label)
        }
        val ids = components.map { it.id }.toSet()
        require(ids.size==components.size) { "Duplicate component ID" }
        require(components.map { it.reference }.distinct().size==components.size) { "Duplicate component reference" }
        require(wires.map { it.id }.distinct().size==wires.size) { "Duplicate wire ID" }
        require(wires.all { it.id.isNotBlank() && it.id.length<=128 && it.start!=it.end }) { "Invalid wire" }
        val connections=wires.map { if(it.start.toString()<=it.end.toString()) it.start to it.end else it.end to it.start }
        require(connections.distinct().size==connections.size) { "Duplicate wire connection" }
        require(wires.all { it.start.componentId in ids && it.end.componentId in ids }) { "Wire references missing component" }
        val byId=components.associateBy { it.id }
        require(wires.all { it.start.index in 0 until byId.getValue(it.start.componentId).terminalCount &&
            it.end.index in 0 until byId.getValue(it.end.componentId).terminalCount }) { "Invalid terminal index" }
        require(components.none { it.kind in setOf(Kind.OPAMP,Kind.IDEAL_OPAMP) &&
            it.value("upperRail")<=it.value("lowerRail") }) { "Invalid op-amp rails" }
        val name=o.getString("name")
        require(name.isNotBlank() && name.length<=80) { "Invalid project name" }
        val tolerance=settings?.optDouble("tolerance") ?: 1e-8
        val maxIterations=settings?.optInt("maxIterations") ?: 80
        require(tolerance.isFinite() && tolerance in 1e-12..1e-2 && maxIterations in 1..1000) {
            "Invalid simulation settings"
        }
        val environment=o.optJSONObject("environment")?.let { env ->
            EnvironmentState(
                env.optDouble("temperatureC",25.0),
                env.optDouble("humidityPercent",45.0),
                env.optDouble("illuminanceLux",300.0),
                env.optDouble("distanceCm",40.0),
                env.optBoolean("motion",false),
                env.optDouble("pressureHpa",1013.0),
                env.optDouble("soundDb",40.0))
        } ?: EnvironmentState()
        val firmwareArray=o.optJSONArray("firmware")
        val firmware=(0 until (firmwareArray?.length() ?: 0)).map { i ->
            val item=firmwareArray!!.getJSONObject(i)
            require(item.getString("source").length<=20000) { "Firmware source is too large" }
            FirmwareAttachment(item.getString("boardId"),
                FirmwareLanguage.valueOf(item.optString("language",FirmwareLanguage.ARDUINO_SUBSET.name)),
                item.getString("source"),item.optBoolean("usbPower",false))
        }
        return Circuit(name, components, wires,
            SimulationSettings(settings?.optString("analysis") ?: "DC", tolerance,maxIterations),
            firmware,environment)
    }
}
