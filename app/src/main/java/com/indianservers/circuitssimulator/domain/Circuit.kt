package com.indianservers.circuitssimulator.domain

import java.util.UUID

enum class Kind(val title: String, val prefix: String, val category: String) {
    BATTERY("Battery", "B", "Sources"), SOURCE("DC source", "V", "Sources"),
    FUNCTION_GENERATOR("Function generator", "FG", "Sources"),
    RESISTOR("Resistor", "R", "Basic"), FUSE("Fuse", "F", "Basic"), SWITCH("Switch", "SW", "Basic"),
    LDR("Photoresistor", "LDR", "Sensors"), THERMISTOR("NTC thermistor", "TH", "Sensors"),
    POTENTIOMETER("Potentiometer", "RV", "Sensors"),
    LED("LED", "D", "Semiconductors"), DIODE("Diode", "D", "Semiconductors"),
    RECTIFIER("1N4007 rectifier", "D", "Semiconductors"),
    NPN_BJT("2N3904 NPN", "Q", "Semiconductors"), PNP_BJT("2N3906 PNP", "Q", "Semiconductors"),
    NMOS("2N7000 NMOS", "M", "Semiconductors"), PMOS("Generic PMOS", "M", "Semiconductors"),
    IDEAL_OPAMP("Ideal op-amp", "U", "Semiconductors"), OPAMP("Generic op-amp", "U", "Semiconductors"),
    LAMP("Bulb", "LAMP", "Outputs"), CAPACITOR("Capacitor", "C", "Basic"),
    ELECTROLYTIC("Electrolytic capacitor", "C", "Basic"), INDUCTOR("Inductor", "L", "Basic"),
    GROUND("Ground", "GND", "Basic"), AMMETER("Ammeter", "A", "Measurement"),
    VOLTMETER("Voltmeter", "VM", "Measurement"),
    JUNCTION("Junction", "J", "Internal")
}

data class DatasheetMetadata(
    val manufacturer: String? = null, val partNumber: String? = null,
    val packageName: String? = null, val referenceUrl: String? = null,
    val sourceNotes: String? = null, val revision:String?=null,
    val physicalPinOrder:List<String> = emptyList(), val modelVersion:Int=1,
    val lastVerifiedUtc:String?=null
)
enum class ModelAccuracy { IDEAL, SIMPLIFIED, DATASHEET_FITTED, MANUFACTURER_SPICE, EMPIRICAL }
data class ThermalMetadata(val ambientC:Double=25.0,val junctionToAmbientCPerW:Double?=null,
                           val thermalCapacitanceJPerC:Double?=null)

data class Parameter(val key: String, val label: String, val unit: String, val default: Double,
                     val min: Double, val max: Double)

data class Definition(val kind: Kind, val description: String, val parameters: List<Parameter>,
                      val modelId: String = kind.name.lowercase(), val rendererId: String = kind.name.lowercase(),
                      val datasheet: DatasheetMetadata = DatasheetMetadata(),
                      val thermal:ThermalMetadata=ThermalMetadata(),
                      val modelAccuracy:ModelAccuracy=ModelAccuracy.SIMPLIFIED)

object ComponentRegistry {
    private val entries = listOf(
        Definition(Kind.BATTERY, "Simple battery with optional internal resistance", listOf(
            Parameter("voltage", "Open-circuit voltage", "V", 9.0, .1, 48.0),
            Parameter("internalResistance", "Internal resistance", "Ω", 0.0, 0.0, 100.0))),
        Definition(Kind.SOURCE, "DC voltage source with optional output resistance", listOf(
            Parameter("voltage", "Voltage", "V", 9.0, .1, 100.0),
            Parameter("internalResistance", "Output resistance", "Ω", 0.0, 0.0, 100.0),
            Parameter("acAmplitude", "AC small-signal amplitude", "V", 1.0, 0.0, 100.0))),
        Definition(Kind.FUNCTION_GENERATOR, "Time-varying voltage source", listOf(
            Parameter("frequency", "Frequency", "Hz", 1000.0, .01, 1e6),
            Parameter("amplitude", "Peak amplitude", "V", 2.5, 0.0, 100.0),
            Parameter("offset", "DC offset", "V", 0.0, -100.0, 100.0),
            Parameter("duty", "Duty fraction", "", .5, .01, .99),
            Parameter("phase", "Phase", "°", 0.0, -360.0, 360.0),
            Parameter("waveform", "Waveform (0 sine, 1 square, 2 triangle, 3 saw, 4 pulse)", "", 0.0, 0.0, 4.0),
            Parameter("rise", "Pulse rise", "s", 1e-6, 0.0, 1.0),
            Parameter("fall", "Pulse fall", "s", 1e-6, 0.0, 1.0),
            Parameter("internalResistance", "Output resistance", "Ω", 0.0, 0.0, 1000.0))),
        Definition(Kind.RESISTOR, "Limits current", listOf(Parameter("resistance", "Resistance", "Ω", 330.0, 1.0, 1e7), Parameter("rating", "Power rating", "W", .25, .01, 10.0), Parameter("tolerance", "Tolerance", "%", 5.0, .1, 20.0)),
            thermal=ThermalMetadata(junctionToAmbientCPerW=180.0,thermalCapacitanceJPerC=.8)),
        Definition(Kind.LDR, "Light-dependent resistor; illumination changes circuit resistance", listOf(
            Parameter("illuminanceLux", "Illumination", "lx", 100.0, 0.0, 100000.0),
            Parameter("referenceResistance", "Resistance at 100 lx", "Ω", 10000.0, 10.0, 1e8),
            Parameter("gamma", "Light exponent", "", 0.7, 0.1, 2.0)),
            modelAccuracy=ModelAccuracy.SIMPLIFIED),
        Definition(Kind.THERMISTOR, "NTC resistor using the beta equation and ambient temperature", listOf(
            Parameter("temperatureC", "Ambient temperature", "°C", 25.0, -40.0, 150.0),
            Parameter("resistance25C", "Resistance at 25 °C", "Ω", 10000.0, 10.0, 1e8),
            Parameter("beta", "Beta constant", "K", 3950.0, 100.0, 10000.0)),
            modelAccuracy=ModelAccuracy.SIMPLIFIED),
        Definition(Kind.POTENTIOMETER, "Three-terminal resistor with an adjustable wiper", listOf(
            Parameter("position", "Wiper position", "", 0.5, 0.0, 1.0),
            Parameter("resistance", "End-to-end resistance", "Ω", 10000.0, 10.0, 1e7)),
            modelAccuracy=ModelAccuracy.IDEAL),
        Definition(Kind.FUSE, "Simplified I²t fuse that opens under sustained overload",listOf(
            Parameter("resistance", "Cold resistance", "Ω", .1, .001, 10.0),
            Parameter("currentRating", "Current rating", "A", .5, .01, 100.0),
            Parameter("i2t", "Opening I²t", "A²s", .01, 1e-6, 1e4))),
        Definition(Kind.SWITCH, "Opens or closes a circuit", emptyList()),
        Definition(Kind.LED, "Emits light when forward biased", listOf(
            Parameter("maxCurrent", "Maximum current", "A", .02, .001, .1),
            Parameter("isat", "Model saturation current", "A", 1e-18, 1e-22, 1e-6),
            Parameter("ideality", "Model ideality", "", 2.0, 1.0, 4.0),
            Parameter("temperatureC", "Junction temperature", "°C", 25.0, -55.0, 150.0))),
        Definition(Kind.DIODE, "1N4148 high-speed switching diode", listOf(
            Parameter("isat", "Model saturation current", "A", 1e-12, 1e-20, 1e-6),
            Parameter("ideality", "Model ideality", "", 1.8, 1.0, 4.0),
            Parameter("temperatureC", "Junction temperature", "°C", 25.0, -55.0, 150.0)),
            datasheet=DatasheetMetadata(manufacturer="NXP Semiconductors",partNumber="1N4148",
                packageName="SOD27 / DO-35",referenceUrl="https://assets.nexperia.com/documents/data-sheet/1N4148_1N4448.pdf",
                sourceNotes="Continuous forward current 200 mA; reverse voltage 100 V; forward voltage ≤1 V at 10 mA. RθJA 350 K/W assumes specified board and lead geometry. Generic diode solver approximation is not a fitted SPICE model.",
                physicalPinOrder=listOf("Anode","Cathode")),
            thermal=ThermalMetadata(junctionToAmbientCPerW=350.0)),
        Definition(Kind.RECTIFIER,"1N4007 1 A rectifier",listOf(
            Parameter("isat", "Model saturation current", "A", 1e-12, 1e-20, 1e-6),
            Parameter("ideality", "Model ideality", "", 1.8, 1.0, 4.0),
            Parameter("temperatureC", "Junction temperature", "°C", 25.0, -55.0, 150.0)),
            datasheet=DatasheetMetadata(manufacturer="Diodes Incorporated",partNumber="1N4007",
                packageName="DO-41",referenceUrl="https://www.diodes.com/datasheet/download/1N4007.pdf",
                sourceNotes="Average rectified current 1 A at 75 °C; reverse voltage 1000 V; forward voltage 1 V at 1 A. Typical RθJA 100 K/W assumes specified leads. Generic diode solver approximation is not a fitted SPICE model.",
                physicalPinOrder=listOf("Anode","Cathode")),
            thermal=ThermalMetadata(junctionToAmbientCPerW=100.0)),
        Definition(Kind.NPN_BJT,"NPN transistor, C/B/E electrical pins",listOf(
            Parameter("is", "Junction saturation current", "A", 1e-14, 1e-20, 1e-8),
            Parameter("bf", "Forward gain", "", 100.0, 1.0, 1000.0),
            Parameter("br", "Reverse gain", "", 1.0, .1, 100.0),
            Parameter("vaf", "Early voltage", "V", 100.0, 1.0, 1000.0)),
            datasheet=DatasheetMetadata("onsemi","2N3904","TO-92","https://www.onsemi.com/pdf/datasheet/2n3904-d.pdf",
                "Maximum VCEO 40 V, IC 200 mA, PD 625 mW at 25 °C. Ebers-Moll parameters are educational estimates, not a fitted manufacturer SPICE model.",
                "October 2024 Rev. 3",listOf("E","B","C")),thermal=ThermalMetadata(junctionToAmbientCPerW=200.0)),
        Definition(Kind.PNP_BJT,"PNP transistor, C/B/E electrical pins",listOf(
            Parameter("is", "Junction saturation current", "A", 1e-14, 1e-20, 1e-8),
            Parameter("bf", "Forward gain", "", 100.0, 1.0, 1000.0),
            Parameter("br", "Reverse gain", "", 1.0, .1, 100.0),
            Parameter("vaf", "Early voltage", "V", 100.0, 1.0, 1000.0)),
            datasheet=DatasheetMetadata("onsemi","2N3906","TO-92","https://www.onsemi.com/pdf/datasheet/2n3906-d.pdf",
                "Maximum VCEO 40 V, IC 200 mA, PD 625 mW at 25 °C. Ebers-Moll parameters are educational estimates, not a fitted manufacturer SPICE model.",
                "February 2010 Rev. 4",listOf("E","B","C")),thermal=ThermalMetadata(junctionToAmbientCPerW=200.0)),
        Definition(Kind.NMOS,"N-channel MOSFET, D/G/S electrical pins",listOf(
            Parameter("vth", "Threshold", "V", 2.1, .1, 10.0),
            Parameter("kp", "Transconductance factor", "A/V²", .04, 1e-6, 10.0),
            Parameter("lambda", "Channel modulation", "1/V", .02, 0.0, 1.0)),
            datasheet=DatasheetMetadata("onsemi","2N7000","TO-92","https://www.onsemi.com/pdf/datasheet/nds7002a-d.pdf",
                "Maximum VDS 60 V, ID 200 mA; threshold 0.8–3 V at ID 1 mA. Square-law model is an educational fit, not manufacturer SPICE.",
                "March 2025 Rev. 11",listOf("S","G","D")),thermal=ThermalMetadata(junctionToAmbientCPerW=312.5)),
        Definition(Kind.PMOS,"Generic P-channel square-law model",listOf(
            Parameter("vth", "Threshold magnitude", "V", 2.0, .1, 10.0),
            Parameter("kp", "Transconductance factor", "A/V²", .04, 1e-6, 10.0),
            Parameter("lambda", "Channel modulation", "1/V", .02, 0.0, 1.0))),
        Definition(Kind.IDEAL_OPAMP,"Idealized high-gain op-amp with configurable supply rails",listOf(
            Parameter("lowerRail", "Negative supply rail", "V", -15.0, -100.0, 99.0),
            Parameter("upperRail", "Positive supply rail", "V", 15.0, -99.0, 100.0),
            Parameter("gain", "Open-loop gain", "", 1e8, 1e3, 1e10)),
            modelAccuracy=ModelAccuracy.IDEAL),
        Definition(Kind.OPAMP,"Finite-gain generic op-amp with rail saturation and AC bandwidth",listOf(
            Parameter("lowerRail", "Negative supply rail", "V", -15.0, -100.0, 99.0),
            Parameter("upperRail", "Positive supply rail", "V", 15.0, -99.0, 100.0),
            Parameter("gain", "Open-loop gain", "", 100000.0, 100.0, 1e8),
            Parameter("gbw", "Gain-bandwidth product", "Hz", 1e6, 100.0, 1e9),
            Parameter("outputResistance", "Output resistance", "Ω", 1.0, 0.0, 1000.0),
            Parameter("inputOffset", "Input offset", "V", 0.0, -.1, .1))),
        Definition(Kind.LAMP, "Resistive lamp approximation", listOf(Parameter("resistance", "Hot resistance", "Ω", 100.0, 1.0, 1e5), Parameter("rating", "Power rating", "W", 1.0, .01, 100.0))),
        Definition(Kind.CAPACITOR, "Stores electric charge", listOf(Parameter("capacitance", "Capacitance", "F", 100e-6, 1e-12, 1.0), Parameter("initialVoltage", "Initial voltage", "V", 0.0, -100.0, 100.0), Parameter("maxVoltage", "Maximum voltage", "V", 25.0, 1.0, 1000.0))),
        Definition(Kind.ELECTROLYTIC, "Polarized charge storage", listOf(Parameter("capacitance", "Capacitance", "F", 100e-6, 1e-9, 1.0), Parameter("initialVoltage", "Initial voltage", "V", 0.0, -100.0, 100.0), Parameter("maxVoltage", "Maximum voltage", "V", 25.0, 1.0, 1000.0))),
        Definition(Kind.INDUCTOR, "Stores magnetic energy", listOf(Parameter("inductance", "Inductance", "H", .01, 1e-6, 100.0), Parameter("initialCurrent", "Initial current", "A", 0.0, -10.0, 10.0))),
        Definition(Kind.GROUND, "Electrical reference", emptyList()),
        Definition(Kind.AMMETER, "Low impedance current meter", emptyList()),
        Definition(Kind.VOLTMETER, "High impedance voltage meter", emptyList()),
        Definition(Kind.JUNCTION, "Electrical wire junction", emptyList())
    )
    val definitions=entries.associateBy { it.kind }
    fun validate():List<String> {
        val errors=mutableListOf<String>()
        if(entries.map { it.kind }.distinct().size!=entries.size) errors+="Duplicate component kind"
        entries.forEach { d ->
            if(d.modelId.isBlank() || d.rendererId.isBlank()) errors+="${d.kind}: missing model or renderer"
            val source=d.datasheet
            if((source.manufacturer==null)!=(source.partNumber==null)) errors+="${d.kind}: incomplete manufacturer identity"
            if(source.partNumber!=null) {
                if(source.packageName.isNullOrBlank() || source.referenceUrl.isNullOrBlank())
                    errors+="${d.kind}: missing package or source"
                val pinCount=if(d.kind in setOf(Kind.NPN_BJT,Kind.PNP_BJT,Kind.NMOS,Kind.PMOS,Kind.IDEAL_OPAMP,Kind.OPAMP)) 3 else 2
                if(source.physicalPinOrder.size!=pinCount || source.physicalPinOrder.distinct().size!=pinCount)
                    errors+="${d.kind}: invalid physical pin order"
            }
            if(d.parameters.map { it.key }.distinct().size!=d.parameters.size) errors+="${d.kind}: duplicate parameter"
            d.parameters.forEach { p ->
                if(!p.default.isFinite() || !p.min.isFinite() || !p.max.isFinite() ||
                    p.min>p.max || p.default !in p.min..p.max) errors+="${d.kind}.${p.key}: invalid range"
                if(p.key in setOf("capacitance","inductance","resistance","rating","i2t") && p.min<0)
                    errors+="${d.kind}.${p.key}: negative physical value"
                if(p.key=="temperatureC" && p.min< -273.15) errors+="${d.kind}.${p.key}: below absolute zero"
            }
        }
        return errors
    }
}

data class PlacedComponent(
    val id: String = UUID.randomUUID().toString(), val kind: Kind, val reference: String,
    val x: Float, val y: Float, val rotation: Int = 0,
    val parameters: Map<String, Double> = ComponentRegistry.definitions.getValue(kind).parameters.associate { it.key to it.default },
    val closed: Boolean = true,
    val databaseId:String? = ComponentRegistry.definitions.getValue(kind).datasheet.let { source ->
        if(source.manufacturer!=null && source.partNumber!=null) "${source.manufacturer}:${source.partNumber}" else null },
    val modelVersion:Int = ComponentRegistry.definitions.getValue(kind).datasheet.modelVersion
) {
    fun value(key: String) = parameters[key] ?: ComponentRegistry.definitions.getValue(kind).parameters.firstOrNull { it.key == key }?.default ?: 0.0
    val terminalCount get() = when(kind) {
        Kind.GROUND,Kind.JUNCTION -> 1
        Kind.NPN_BJT,Kind.PNP_BJT,Kind.NMOS,Kind.PMOS,Kind.IDEAL_OPAMP,Kind.OPAMP,Kind.POTENTIOMETER -> 3
        else -> 2
    }
}

data class TerminalRef(val componentId: String, val index: Int)
data class Wire(val id: String = UUID.randomUUID().toString(), val start: TerminalRef, val end: TerminalRef,
                val label:String?=null)
data class SimulationSettings(val analysis: String = "DC", val tolerance: Double = 1e-8, val maxIterations: Int = 80)
data class Circuit(val name: String, val components: List<PlacedComponent>, val wires: List<Wire>,
                   val settings: SimulationSettings = SimulationSettings())

/** Physical wires plus logical joins between separate wire segments bearing the same net label. */
fun Circuit.electricalConnections():List<Pair<TerminalRef,TerminalRef>> =
    wires.map { it.start to it.end } + wires.filter { !it.label.isNullOrBlank() }
        .groupBy { it.label!!.uppercase(java.util.Locale.ROOT) }.values.flatMap { group ->
            group.drop(1).map { group.first().start to it.start }
        }

fun Circuit.nextReference(kind: Kind): String {
    val used = components.map { it.reference }.toSet()
    return generateSequence(1) { it + 1 }.map { "${kind.prefix}$it" }.first { it !in used }
}
