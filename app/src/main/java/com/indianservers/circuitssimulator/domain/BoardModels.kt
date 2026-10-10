package com.indianservers.circuitssimulator.domain

enum class PinType {
    GPIO, ANALOG_INPUT, ANALOG_OUTPUT, POWER_INPUT, POWER_OUTPUT, GROUND,
    RESET, ENABLE, REFERENCE, USB, BOOT, SPECIAL, RESERVED, ONBOARD
}

enum class BoardFamily { ARDUINO_AVR, ARDUINO_SAMD, RP2040, RP2350, ESP8266, ESP32, ESP32_C3 }

enum class BoardSupportLevel { FULL, FUNCTIONAL, SIMPLIFIED, PREVIEW }

enum class ReferenceType { BOARD_DATASHEET, PINOUT, SCHEMATIC, MCU_DATASHEET, USER_GUIDE }

enum class FeatureSimLevel { FULL, BASIC, METADATA, UNSUPPORTED }

data class BoardReference(
    val title:String, val publisher:String, val documentType:ReferenceType,
    val revision:String?=null, val sourceUrl:String?=null, val notes:String?=null,
    val applicableRevision:String?=null,val lastVerified:String?=null
)

data class PowerDomain(val id:String, val voltage:Double, val role:String, val notes:String="")

data class FeatureSupport(val hardware:Boolean, val simulator:FeatureSimLevel, val notes:String="")

data class BoardCapabilityMatrix(
    val gpio:FeatureSupport, val adc:FeatureSupport, val pwm:FeatureSupport,
    val uart:FeatureSupport, val i2c:FeatureSupport, val spi:FeatureSupport,
    val wifi:FeatureSupport, val bluetooth:FeatureSupport, val dac:FeatureSupport
)

data class BoardPin(
    val name:String,
    val capabilities:Set<PinCapability>,
    val notes:String="",
    val minVoltage:Double?=null,
    val maxVoltage:Double?=null,
    val physicalNumber:Int?=null,
    val gpioNumber:Int?=null,
    val aliases:Set<String> = emptySet(),
    val type:PinType = PinType.GPIO,
    val signal:String = name,
    val adcChannel:Int?=null,
    val inputOnly:Boolean=false,
    val connectable:Boolean=true,
    val nx:Float?=null,
    val ny:Float?=null,
    val warnings:List<String> = emptyList(),
    val physicalLabel:String=name,val functions:Set<String> = emptySet(),
    val maxCurrentMa:Double?=null,val pwmResource:String?=null,
    val pullUpSupported:Boolean=true,val pullDownSupported:Boolean=false
)

data class BoardDefinition(
    val kind:Kind,
    val manufacturer:String,
    val product:String,
    val mcu:String,
    val logicVoltage:Double,
    val pins:List<BoardPin>,
    val supplyPins:Set<String>,
    val sourceUrl:String,
    val notes:String,
    val keywords:List<String> = emptyList(),
    val family:BoardFamily = BoardFamily.ARDUINO_AVR,
    val revision:String = "",
    val module:String?=null,
    val architecture:String = "",
    val adcBits:Int = 10,
    val adcReferenceVoltage:Double = logicVoltage,
    val ledBuiltin:String?=null,
    val supportLevel:BoardSupportLevel = BoardSupportLevel.FUNCTIONAL,
    val powerDomains:List<PowerDomain> = emptyList(),
    val references:List<BoardReference> = emptyList(),
    val warnings:List<String> = emptyList(),
    val capabilities:BoardCapabilityMatrix = BoardCapabilityMatrix(
        FeatureSupport(true,FeatureSimLevel.FULL),FeatureSupport(true,FeatureSimLevel.FULL),
        FeatureSupport(true,FeatureSimLevel.FULL),FeatureSupport(true,FeatureSimLevel.BASIC),
        FeatureSupport(true,FeatureSimLevel.BASIC),FeatureSupport(true,FeatureSimLevel.BASIC),
        FeatureSupport(false,FeatureSimLevel.UNSUPPORTED),FeatureSupport(false,FeatureSimLevel.UNSUPPORTED),
        FeatureSupport(false,FeatureSimLevel.UNSUPPORTED)),
    val usbConnector:String = "USB",
    val boardWidth:Float = 202f,
    val boardHeight:Float = 240f,
    val pcbColor:Long = 0xFF08677E,
    val hardware:BoardHardwareProfile = BoardHardwareProfiles.forKind(kind)
) {
    fun pin(name:String):BoardPin?=pins.firstOrNull { it.name==name }
    fun index(name:String):Int=pins.indexOfFirst { it.name==name }
    fun displayName(name:String):String {
        val pin=pin(name) ?: return name
        val gpio=pin.gpioNumber ?: return name
        return if(name.contains("GPIO",ignoreCase=true) || name.startsWith("IO") || name.startsWith("GP")) name
            else "$name / GPIO$gpio"
    }
    fun beginnerHint(pin:BoardPin):String = when {
        PinType.GROUND==pin.type -> "Common return. Every circuit needs a shared ground with this board."
        pin.type==PinType.POWER_OUTPUT -> "Regulated board rail. Use it to power  sensors at this voltage only."
        pin.type==PinType.POWER_INPUT -> "Board supply input. This is not a GPIO."
        PinCapability.ANALOG_INPUT in pin.capabilities && PinCapability.DIGITAL_OUTPUT !in pin.capabilities ->
            "Measures an analog voltage and converts it to a numeric ADC value."
        PinCapability.ANALOG_INPUT in pin.capabilities ->
            "General-purpose digital pin that can also measure analog voltage (ADC)."
        PinCapability.PWM in pin.capabilities ->
            "General-purpose digital input/output pin. This header also supports PWM."
        PinCapability.DIGITAL_INPUT in pin.capabilities ->
            "General-purpose digital input/output pin."
        else -> pin.notes.ifBlank { "Special-function header. Not a general GPIO." }
    }
    fun resolve(raw:Any?,analog:Boolean=false):String? {
        if(raw==null) return null
        if(raw is Number) {
            val n=raw.toDouble().toInt()
            if(analog) {
                if(family==BoardFamily.ESP8266 && n==0) return pin("A0")?.name
                if(family!=BoardFamily.ARDUINO_AVR && family!=BoardFamily.ARDUINO_SAMD)
                    return pins.firstOrNull { it.gpioNumber==n && PinCapability.ANALOG_INPUT in it.capabilities }?.name
                pin("A$n")?.let { return it.name }
                pins.firstOrNull { it.adcChannel==n }?.let { return it.name }
                pins.firstOrNull { it.gpioNumber==n && PinCapability.ANALOG_INPUT in it.capabilities }?.let { return it.name }
            }
            if(ledBuiltin!=null && n.toString() in setOf(ledBuiltin, pin(ledBuiltin)?.gpioNumber?.toString()))
                return ledBuiltin
            pins.firstOrNull { it.gpioNumber==n }?.let { return it.name }
            pin("D$n")?.let { return it.name }
            pin("GP$n")?.let { return it.name }
            pin("IO$n")?.let { return it.name }
            pin("A$n")?.let { return it.name }
            return null
        }
        val text=raw.toString().trim()
        if(text.uppercase() in setOf("LED_BUILTIN","LED") && ledBuiltin!=null) return ledBuiltin
        val upper=text.uppercase()
        pins.firstOrNull { it.name.equals(text,ignoreCase=true) }?.let { return it.name }
        pins.firstOrNull { pin -> pin.aliases.any { it.equals(text,ignoreCase=true) } }?.let { return it.name }
        if(upper.startsWith("GPIO")) {
            val n=upper.removePrefix("GPIO").toIntOrNull() ?: return null
            return pins.firstOrNull { it.gpioNumber==n }?.name
        }
        return null
    }
    fun findPins(query:String):List<Int> {
        val q=query.trim()
        if(q.isEmpty()) return emptyList()
        return pins.mapIndexedNotNull { i,pin ->
            val hay=listOf(pin.name,displayName(pin.name),pin.gpioNumber?.toString() ?: "",
                pin.signal,pin.adcChannel?.let { "A$it" } ?: "").plus(pin.aliases)
            if(hay.any { it.contains(q,ignoreCase=true) }) i else null
        }
    }
    fun supportLabel():String=when(supportLevel) {
        BoardSupportLevel.FULL -> "Full"
        BoardSupportLevel.FUNCTIONAL -> "Functional"
        BoardSupportLevel.SIMPLIFIED -> "Simplified"
        BoardSupportLevel.PREVIEW -> "Preview"
    }
}

fun featureLabel(level:FeatureSimLevel)=when(level) {
    FeatureSimLevel.FULL -> "Full"
    FeatureSimLevel.BASIC -> "Basic"
    FeatureSimLevel.METADATA -> "Hardware only"
    FeatureSimLevel.UNSUPPORTED -> "Unsupported"
}
