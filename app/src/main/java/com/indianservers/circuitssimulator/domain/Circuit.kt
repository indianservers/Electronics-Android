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
    JUNCTION("Junction", "J", "Internal"),
    VCCS("Voltage-controlled current source", "G", "Sources"),
    VCVS("Voltage-controlled voltage source", "E", "Sources"),
    CCCS("Current-controlled current source", "F", "Sources"),
    CCVS("Current-controlled voltage source", "H", "Sources"),
    TRANSFORMER("Transformer", "T", "Basic"),
    PUSH_BUTTON("Push button", "PB", "Basic"), SPDT_SWITCH("SPDT switch", "SW", "Basic"),
    RELAY("Relay", "K", "Basic"), ZENER("Zener diode", "ZD", "Semiconductors"),
    RGB_LED("RGB LED", "RGB", "Outputs"), DC_MOTOR("DC motor", "M", "Outputs"),
    TIMER_555("555 timer", "U", "Digital"), D_FLIP_FLOP("D flip-flop", "U", "Digital"),
    T_FLIP_FLOP("T flip-flop", "U", "Digital"), COUNTER_4("4-bit counter", "U", "Digital"),
    ADC_2("2-bit ADC", "U", "Digital"), DAC_2("2-bit DAC", "U", "Digital"),
    SEVEN_SEGMENT("Seven-segment display", "DS", "Outputs"),
    CLOCK("Clock", "CLK", "Digital"), NOT_GATE("NOT gate", "U", "Digital"),
    AND_GATE("AND gate", "U", "Digital"), OR_GATE("OR gate", "U", "Digital"),
    NAND_GATE("NAND gate", "U", "Digital"), NOR_GATE("NOR gate", "U", "Digital"),
    XOR_GATE("XOR gate", "U", "Digital"), XNOR_GATE("XNOR gate", "U", "Digital"),
    ARDUINO_UNO("Arduino Uno R3", "UNO", "Development Boards"),
    ARDUINO_NANO("Arduino Nano", "NANO", "Development Boards"),
    ARDUINO_MEGA("Arduino Mega 2560", "MEGA", "Development Boards"),
    RASPBERRY_PICO("Raspberry Pi Pico", "PICO", "Development Boards"),
    RASPBERRY_PICO_W("Raspberry Pi Pico W", "PICOW", "Development Boards"),
    NODEMCU_ESP8266("NodeMCU ESP8266", "ESP", "Development Boards"),
    ESP32_DEVKIT("ESP32 DevKitC V4", "ESP32", "Development Boards"),
    WEMOS_D1_MINI("Wemos D1 Mini", "D1", "Development Boards"),
    ESP32_C3_DEVKIT("ESP32-C3 DevKitM-1", "C3", "Development Boards"),
    ARDUINO_NANO_33_IOT("Arduino Nano 33 IoT", "N33", "Development Boards"),
    RASPBERRY_PICO_2("Raspberry Pi Pico 2", "PICO2", "Development Boards"),
    SINGLE_CELL("Single-cell battery", "CELL", "Sources"),
    BATTERY_PACK("Battery pack", "PACK", "Sources"),
    VARIABLE_DC_SUPPLY("Variable DC supply", "PS", "Sources"),
    DC_CURRENT_SOURCE("DC current source", "I", "Sources"),
    AC_VOLTAGE_SOURCE("AC voltage source", "VAC", "Sources"),
    SINE_GENERATOR("Sine-wave generator", "SIN", "Sources"),
    SQUARE_GENERATOR("Square-wave generator", "SQ", "Sources"),
    PULSE_GENERATOR("Pulse generator", "PULSE", "Sources"),
    RHEOSTAT("Rheostat", "RV", "Basic"),
    PTC_THERMISTOR("PTC thermistor", "PTC", "Sensors"),
    VARIABLE_CAPACITOR("Variable capacitor", "CV", "Basic"),
    SCHOTTKY_DIODE("Schottky diode", "SD", "Semiconductors"),
    RED_LED("Red LED", "D", "Outputs"),
    GREEN_LED("Green LED", "D", "Outputs"),
    BLUE_LED("Blue LED", "D", "Outputs"),
    PHOTODIODE("Photodiode", "PD", "Sensors"),
    NC_PUSH_BUTTON("Normally-closed push button", "PB", "Basic"),
    BUFFER_GATE("Buffer", "U", "Digital"),
    TRI_STATE_BUFFER("Tri-state buffer", "U", "Digital"),
    SR_LATCH("SR latch", "U", "Digital"),
    D_LATCH("D latch", "U", "Digital"),
    JK_FLIP_FLOP("JK flip-flop", "U", "Digital"),
    MULTIPLEXER_2("2:1 multiplexer", "U", "Digital"),
    DEMULTIPLEXER_2("1:2 demultiplexer", "U", "Digital"),
    ENCODER_4("4:2 priority encoder", "U", "Digital"),
    DECODER_2("2:4 decoder", "U", "Digital"),
    LOGIC_INPUT("Logic input", "IN", "Digital"),
    LOGIC_OUTPUT("Logic output", "OUT", "Digital"),
    BUZZER("Active buzzer", "BZ", "Outputs"),
    SPEAKER("Speaker", "SPK", "Outputs"),
    SERVO_MOTOR("Servo motor", "SERVO", "Outputs"),
    SOLENOID("Solenoid", "SOL", "Outputs"),
    DIP_SWITCH_4("4-way DIP switch", "DIP", "Basic"),
    DPDT_RELAY("DPDT relay", "K", "Basic"),
    REED_SWITCH("Reed switch", "RS", "Sensors"),
    LM35_SENSOR("LM35 temperature sensor", "TMP", "Sensors"),
    HALL_SENSOR("Linear Hall sensor", "HALL", "Sensors"),
    PHOTOTRANSISTOR("Phototransistor", "Q", "Sensors"),
    DARLINGTON("Darlington NPN", "Q", "Semiconductors"),
    SCR("SCR thyristor", "SCR", "Semiconductors"),
    TRIAC("TRIAC", "TRIAC", "Semiconductors"),
    LM358("LM358 dual op-amp", "U", "Semiconductors"),
    LM741("LM741 op-amp", "U", "Semiconductors"),
    COMPARATOR("Comparator", "U", "Semiconductors"),
    REGULATOR_7805("7805 regulator", "U", "Semiconductors"),
    REGULATOR_LM317("LM317 regulator", "U", "Semiconductors"),
    L293D("L293D motor driver", "U", "Digital"),
    ULN2003("ULN2003 driver", "U", "Digital"),
    SHIFT_74HC595("74HC595 shift register", "U", "Digital"),
    COUNTER_CD4017("CD4017 decade counter", "U", "Digital"),
    SERIAL_TERMINAL("Serial terminal", "TERM", "Embedded"),
    I2C_TEMP_SENSOR("I2C temperature sensor", "TMP", "Embedded"),
    I2C_LCD("I2C LCD 16x2", "LCD", "Embedded"),
    OLED_SSD1306("I2C OLED display", "OLED", "Embedded"),
    I2C_EEPROM("I2C EEPROM", "EE", "Embedded"),
    ULTRASONIC("Ultrasonic ranger", "US", "Embedded"),
    PIR_SENSOR("PIR motion sensor", "PIR", "Embedded"),
    SPI_MEMORY("SPI memory", "MEM", "Embedded")
}

val Kind.isBoard get() = category == "Development Boards"
val Kind.isDigital get() = (category == "Digital" && this !in setOf(Kind.L293D,Kind.ULN2003)) || isBoard
val Kind.isLogicGate get() = this in setOf(Kind.NOT_GATE,Kind.AND_GATE,Kind.OR_GATE,
    Kind.NAND_GATE,Kind.NOR_GATE,Kind.XOR_GATE,Kind.XNOR_GATE,Kind.BUFFER_GATE)

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
                      val modelAccuracy:ModelAccuracy=ModelAccuracy.SIMPLIFIED,
                      val keywords:List<String> = emptyList(),
                      val applications:List<String> = emptyList(),
                      val limitations:List<String> = emptyList()) {
    val id:String get()=kind.name.lowercase()
    val supportStatus:ComponentSupportStatus get()=when(modelAccuracy) {
        ModelAccuracy.IDEAL,ModelAccuracy.DATASHEET_FITTED,ModelAccuracy.MANUFACTURER_SPICE -> ComponentSupportStatus.SUPPORTED
        else -> ComponentSupportStatus.SIMPLIFIED_MODEL
    }
}

enum class ComponentSupportStatus { SUPPORTED, SIMPLIFIED_MODEL, PREVIEW }

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
        Definition(Kind.PUSH_BUTTON,"Normally-open momentary contact; press in the component panel",emptyList()),
        Definition(Kind.SPDT_SWITCH,"Common contact selects throw A or B",emptyList()),
        Definition(Kind.RELAY,"SPDT relay: isolated coil, common, normally-open and normally-closed contacts",listOf(
            Parameter("coilResistance","Coil resistance","Ω",120.0,1.0,1e6),
            Parameter("pullInVoltage","Pull-in voltage","V",5.0,0.1,100.0))),
        Definition(Kind.VCCS,"Current from output + to − equals gain times control voltage",listOf(
            Parameter("gain","Transconductance","A/V",0.01,-100.0,100.0))),
        Definition(Kind.VCVS,"Output voltage equals gain times control voltage",listOf(
            Parameter("gain","Voltage gain","",2.0,-1000.0,1000.0))),
        Definition(Kind.CCCS,"Output current equals gain times current through the control port",listOf(
            Parameter("gain","Current gain","",2.0,-1000.0,1000.0))),
        Definition(Kind.CCVS,"Output voltage equals transresistance times control-port current",listOf(
            Parameter("gain","Transresistance","Ω",100.0,-1e6,1e6))),
        Definition(Kind.TRANSFORMER,"Two coupled windings with a configurable turns ratio",listOf(
            Parameter("primaryInductance","Primary inductance","H",0.1,1e-6,100.0),
            Parameter("turnsRatio","Secondary / primary turns","",2.0,0.01,100.0),
            Parameter("coupling","Magnetic coupling","",0.98,0.01,0.9999))),
        Definition(Kind.ZENER,"Forward diode with reverse breakdown knee",listOf(
            Parameter("breakdownVoltage","Breakdown voltage","V",5.1,1.0,100.0),
            Parameter("dynamicResistance","Breakdown resistance","Ω",10.0,0.1,10000.0))),
        Definition(Kind.RGB_LED,"Common-cathode red, green and blue LED branches",listOf(
            Parameter("seriesResistance","Per-channel resistance","Ω",100.0,1.0,10000.0))),
        Definition(Kind.DC_MOTOR,"Armature R/L with inertia and back EMF",listOf(
            Parameter("resistance","Armature resistance","Ω",20.0,0.1,10000.0),
            Parameter("inductance","Armature inductance","H",0.01,1e-6,100.0),
            Parameter("motorConstant","Torque/back-EMF constant","",0.1,0.001,10.0),
            Parameter("inertia","Rotor inertia","kg·m²",0.001,1e-6,1.0),
            Parameter("friction","Viscous friction","",0.001,0.0,1.0))),
        Definition(Kind.TIMER_555,"Astable 555 output with RESET input",listOf(
            Parameter("frequency","Oscillation frequency","Hz",10.0,0.1,10000.0),
            Parameter("duty","High fraction","",0.66,0.01,0.99))),
        Definition(Kind.D_FLIP_FLOP,"Rising-edge D storage",listOf(Parameter("delay","Clock-to-Q delay","s",1e-5,1e-6,0.1))),
        Definition(Kind.T_FLIP_FLOP,"Rising-edge toggle storage",listOf(Parameter("delay","Clock-to-Q delay","s",1e-5,1e-6,0.1))),
        Definition(Kind.COUNTER_4,"Rising-edge four-bit binary counter",listOf(Parameter("delay","Clock-to-output delay","s",1e-5,1e-6,0.1))),
        Definition(Kind.ADC_2,"Two-bit comparator ADC, 0–3.3 V full scale",emptyList()),
        Definition(Kind.DAC_2,"Two-bit DAC, 0–3.3 V output",emptyList()),
        Definition(Kind.SEVEN_SEGMENT,"Seven independent LED segments with common cathode",listOf(
            Parameter("seriesResistance","Per-segment resistance","Ω",330.0,1.0,10000.0))),
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
        Definition(Kind.JUNCTION, "Electrical wire junction", emptyList()),
        Definition(Kind.CLOCK, "3.3 V digital clock output", listOf(
            Parameter("frequency", "Frequency", "Hz", 10.0, 0.1, 10000.0),
            Parameter("duty", "High fraction", "", 0.5, 0.01, 0.99))),
        *listOf(Kind.NOT_GATE,Kind.AND_GATE,Kind.OR_GATE,Kind.NAND_GATE,
            Kind.NOR_GATE,Kind.XOR_GATE,Kind.XNOR_GATE).map { kind ->
            Definition(kind,"3.3 V logic with finite output drive",listOf(
                Parameter("delay", "Propagation delay", "s", 0.00001, 0.000001, 0.1)))
        }.toTypedArray(),
        *PhaseOneParts.definitions.toTypedArray(),
        *DigitalParts.definitions.toTypedArray(),
        *ElectromechanicalParts.definitions.toTypedArray(),
        *SemiconductorSwitchParts.definitions.toTypedArray(),
        *IcParts.definitions.toTypedArray(),
        *PhaseFiveParts.definitions.toTypedArray(),
        *BoardRegistry.boards.values.map { board -> Definition(board.kind,
            "${board.mcu} development board; ${board.logicVoltage} V GPIO learning model",
            board.pins.filter { it.capabilities.contains(PinCapability.DIGITAL_OUTPUT) }.map { pin ->
                Parameter("gpio_${pin.name}","${pin.name} mode (0 input, 1 low, 2 high)","",0.0,0.0,2.0)
            }+listOf(Parameter("usbPower","USB power (0 off, 1 on)","",1.0,0.0,1.0)),
            datasheet=DatasheetMetadata(manufacturer=board.manufacturer,partNumber=board.product,
                packageName="Development board",referenceUrl=board.sourceUrl,
                sourceNotes=board.notes,physicalPinOrder=board.pins.map { it.name }),
            keywords=board.keywords,applications=listOf("GPIO wiring", "Embedded systems learning"),
            limitations=listOf("Supported firmware subset only; Wi-Fi and Bluetooth are not simulated.",
                "USB power is an explicit board option. UART/I²C/SPI are event-based educational buses.")) }.toTypedArray()
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
                val pinCount=if(d.kind.isBoard) BoardRegistry.boards.getValue(d.kind).pins.size
                    else if(d.kind in ElectromechanicalParts.pinNames) ElectromechanicalParts.pinNames.getValue(d.kind).size
                    else if(d.kind in IcParts.pinNames) IcParts.pinNames.getValue(d.kind).size
                    else if(d.kind in PhaseFiveParts.pinNames) PhaseFiveParts.pinNames.getValue(d.kind).size
                    else if(d.kind in setOf(Kind.NPN_BJT,Kind.PNP_BJT,Kind.NMOS,Kind.PMOS,Kind.IDEAL_OPAMP,Kind.OPAMP)) 3 else 2
                if(source.physicalPinOrder.size!=pinCount ||
                    (!d.kind.isBoard && d.kind !in IcParts.pinNames &&
                        source.physicalPinOrder.distinct().size!=pinCount))
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
        if(definitions.keys!=Kind.entries.toSet()) errors+="Registry does not cover every component kind"
        return errors
    }
    val catalog:List<Definition> = entries.filter { it.kind!=Kind.JUNCTION }
    val common:List<Definition> = listOf(Kind.RESISTOR,Kind.LED,Kind.BATTERY,Kind.SWITCH,
        Kind.CAPACITOR,Kind.GROUND,Kind.ARDUINO_UNO).map(definitions::getValue)
    val categories:List<String> = catalog.map { it.kind.category }.distinct()
    fun search(query:String,category:String?=null):List<Definition> = catalog.filter { definition ->
        (category==null || category=="All" || definition.kind.category==category) &&
            (query.isBlank() || listOf(definition.kind.title,definition.kind.name,
                definition.kind.category,definition.description,definition.datasheet.partNumber.orEmpty(),
                definition.datasheet.manufacturer.orEmpty()).plus(definition.keywords)
                .plus(definition.applications)
                .plus(if(definition.kind.isBoard) {
                    val board=BoardRegistry.boards.getValue(definition.kind)
                    listOf(board.family.name,board.mcu,board.architecture,board.module.orEmpty(),
                        board.revision,board.supportLabel())
                } else emptyList())
                .any { it.contains(query,ignoreCase=true) })
    }
}

data class PlacedComponent(
    val id: String = UUID.randomUUID().toString(), val kind: Kind, val reference: String,
    val x: Float, val y: Float, val rotation: Int = 0,
    val parameters: Map<String, Double> = ComponentRegistry.definitions.getValue(kind).parameters.associate { it.key to it.default },
    val closed: Boolean = kind != Kind.PUSH_BUTTON,
    val databaseId:String? = ComponentRegistry.definitions.getValue(kind).datasheet.let { source ->
        if(source.manufacturer!=null && source.partNumber!=null) "${source.manufacturer}:${source.partNumber}" else null },
    val modelVersion:Int = ComponentRegistry.definitions.getValue(kind).datasheet.modelVersion,
    val sizeScale: Float = 1f
) {
    fun value(key: String) = parameters[key] ?: ComponentRegistry.definitions.getValue(kind).parameters.firstOrNull { it.key == key }?.default ?: 0.0
    val terminalCount get() = if(kind.isBoard) BoardRegistry.boards.getValue(kind).pins.size
        else DigitalParts.pinNames[kind]?.size ?: ElectromechanicalParts.pinNames[kind]?.size ?:
            IcParts.pinNames[kind]?.size ?: PhaseFiveParts.pinNames[kind]?.size ?: when(kind) {
        Kind.GROUND,Kind.JUNCTION,Kind.CLOCK -> 1
        Kind.VCCS,Kind.VCVS,Kind.CCCS,Kind.CCVS,Kind.TRANSFORMER,Kind.RGB_LED,
        Kind.ADC_2,Kind.DAC_2 -> 4
        Kind.COUNTER_4,Kind.RELAY -> 5
        Kind.SEVEN_SEGMENT -> 8
        Kind.NPN_BJT,Kind.PNP_BJT,Kind.NMOS,Kind.PMOS,Kind.IDEAL_OPAMP,Kind.OPAMP,Kind.POTENTIOMETER,
            Kind.AND_GATE,Kind.OR_GATE,Kind.NAND_GATE,Kind.NOR_GATE,Kind.XOR_GATE,Kind.XNOR_GATE,
            Kind.SPDT_SWITCH,Kind.TIMER_555,Kind.D_FLIP_FLOP,Kind.T_FLIP_FLOP,
            Kind.DARLINGTON,Kind.SCR,Kind.TRIAC -> 3
        else -> 2
    }
}

data class TerminalRef(val componentId: String, val index: Int)
data class Wire(val id: String = UUID.randomUUID().toString(), val start: TerminalRef, val end: TerminalRef,
                val label:String?=null)
data class SimulationSettings(val analysis: String = "DC", val tolerance: Double = 1e-8, val maxIterations: Int = 80)
data class Circuit(val name: String, val components: List<PlacedComponent>, val wires: List<Wire>,
                   val settings: SimulationSettings = SimulationSettings(),
                   val firmware:List<FirmwareAttachment> = emptyList(),
                   val environment:EnvironmentState=EnvironmentState())

/** Physical wires plus logical joins between separate wire segments bearing the same net label. */
fun Circuit.electricalConnections():List<Pair<TerminalRef,TerminalRef>> =
    wires.map { it.start to it.end } + wires.filter { !it.label.isNullOrBlank() }
        .groupBy { it.label!!.uppercase(java.util.Locale.ROOT) }.values.flatMap { group ->
            group.drop(1).map { group.first().start to it.start }
        } + components.filter { it.kind.isBoard }.flatMap { part ->
            val pins=BoardRegistry.boards.getValue(part.kind).pins
            val groups=pins.withIndex().groupBy { it.value.signal }
            groups.values.flatMap { pinsWithName -> pinsWithName.drop(1).map { other ->
                TerminalRef(part.id,pinsWithName.first().index) to TerminalRef(part.id,other.index)
            } }
        } + components.filter { it.kind in IcParts.pinNames }.flatMap { part ->
            IcParts.pinNames.getValue(part.kind).withIndex().groupBy { it.value }.values
                .flatMap { group -> group.drop(1).map { other ->
                    TerminalRef(part.id,group.first().index) to TerminalRef(part.id,other.index)
                } }
        }

fun Circuit.nextReference(kind: Kind): String {
    val used = components.map { it.reference }.toSet()
    return generateSequence(1) { it + 1 }.map { "${kind.prefix}$it" }.first { it !in used }
}
