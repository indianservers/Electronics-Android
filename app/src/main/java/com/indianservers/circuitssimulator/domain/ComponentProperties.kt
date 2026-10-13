package com.indianservers.circuitssimulator.domain

import java.math.BigDecimal
import kotlin.math.abs

data class PropertyValidation(val value:Double?=null,val error:String?=null)

/** One definition-driven validation policy for the editor and model mutations. */
object ComponentProperties {
    private val fractions=setOf("duty","position")
    private val integerKeys=setOf("address","waveform","parity","dataBits","stopBits","state","cellCount","baud",
        "writeProtect","retrigger","usbPower","field")
    private val modelKeys=setOf("is","isat","ideality","bf","br","vaf","vth","kp","lambda","gamma",
        "gain","gbw","outputResistance","inputOffset","thresholdOffset","darkResistance",
        "currentAt100Lux","referenceResistance","beta","resistance25C","motorConstant","inertia",
        "dropout","referenceVoltage","triggerVoltage","holdingCurrent","gateResistance","onResistance")
    fun advanced(parameter:Parameter)=parameter.key in modelKeys
    fun primary(definition:Definition)=definition.parameters.filterNot(::advanced).ifEmpty {
        definition.parameters.filter { it.key=="bf" }.ifEmpty { definition.parameters.take(1) }
    }
    fun sharedStimulus(kind:Kind,key:String)=
        key=="temperatureC" && kind in setOf(Kind.LM35_SENSOR,Kind.THERMISTOR,Kind.PTC_THERMISTOR) ||
            key=="illuminanceLux" && kind==Kind.LDR
    fun environmentAfter(kind:Kind,key:String,value:Double,environment:EnvironmentState)=when {
        sharedStimulus(kind,key) && key=="temperatureC" -> environment.copy(temperatureC=value)
        sharedStimulus(kind,key) -> environment.copy(illuminanceLux=value)
        else -> environment
    }
    fun isPercent(parameter:Parameter)=parameter.key in fractions && parameter.max<=1.0
    fun isInteger(parameter:Parameter)=parameter.key in integerKeys || parameter.key.startsWith("gpio_") ||
        parameter.key.matches(Regex("switch[1-4]"))
    fun choices(parameter:Parameter):List<Pair<String,Double>> = when {
        parameter.key=="waveform" -> listOf("Sine","Square","Triangle","Saw","Pulse").mapIndexed { i,s -> s to i.toDouble() }
        parameter.key=="parity" -> listOf("None" to 0.0,"Even" to 1.0,"Odd" to 2.0)
        parameter.key.startsWith("gpio_") -> listOf("Input" to 0.0,"LOW" to 1.0,"HIGH" to 2.0)
        isInteger(parameter) && parameter.min==0.0 && parameter.max==1.0 ->
            if(parameter.key=="state") listOf("LOW" to 0.0,"HIGH" to 1.0) else listOf("Off" to 0.0,"On" to 1.0)
        else -> emptyList()
    }
    fun label(parameter:Parameter)=when {
        isPercent(parameter) && parameter.key=="duty" -> "Duty cycle"
        isPercent(parameter) -> "Position"
        parameter.key.startsWith("gpio_") -> parameter.key.removePrefix("gpio_")+" drive"
        else -> parameter.label.substringBefore(" (0")
    }
    fun input(parameter:Parameter,value:Double):String {
        if(parameter.key=="address") return "0x"+value.toInt().toString(16).uppercase()
        if(isPercent(parameter)) return BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()+"%"
        val scaledUnits=setOf("V","A","Ω","F","H","Hz","s","W")
        val scale=if(parameter.unit !in scaledUnits || value==0.0) 1.0 to "" else when {
            abs(value)>=1e6->1e6 to "M";abs(value)>=1e3->1e3 to "k"
            abs(value)>=1.0->1.0 to "";abs(value)>=1e-3->1e-3 to "m"
            abs(value)>=1e-6->1e-6 to "µ";abs(value)>=1e-9->1e-9 to "n";else->1e-12 to "p"
        }
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(scale.first)).stripTrailingZeros().toPlainString()+
            if(parameter.unit.isBlank()) "" else " ${scale.second}${parameter.unit}"
    }
    fun parse(part:PlacedComponent,parameter:Parameter,text:String):PropertyValidation {
        val value=when {
            parameter.key=="address" && text.trim().startsWith("0x",true) -> text.trim().drop(2).toIntOrNull(16)?.toDouble()
            isPercent(parameter) -> EngineeringUnits.parse(text,"%")?.div(100)
            else -> EngineeringUnits.parse(text,parameter.unit)
        } ?: return PropertyValidation(error="Enter a number${if(isPercent(parameter)) " in percent" else if(parameter.unit.isNotBlank()) " in ${parameter.unit}" else ""}, such as ${input(parameter,parameter.default)}.")
        return validate(part,parameter,value)
    }
    fun validate(part:PlacedComponent,parameter:Parameter,value:Double):PropertyValidation {
        if(!value.isFinite()) return PropertyValidation(error="Enter a finite value.")
        if(value !in parameter.min..parameter.max) return PropertyValidation(error=
            "Use ${input(parameter,parameter.min)} to ${input(parameter,parameter.max)}.")
        if(isInteger(parameter) && value!=kotlin.math.round(value)) return PropertyValidation(error="Use a whole number.")
        if(parameter.key=="address" && MotionSensorParts.profiles[part.kind]?.addresses?.let { value.toInt() !in it }==true)
            return PropertyValidation(error="Choose a documented address: "+MotionSensorParts.profiles.getValue(part.kind).addresses.joinToString { "0x${it.toString(16).uppercase()}" })
        if(parameter.key=="lowerRail" && value>=part.value("upperRail") ||
            parameter.key=="upperRail" && value<=part.value("lowerRail")) return PropertyValidation(error="The positive rail must be above the negative rail.")
        return PropertyValidation(value=value)
    }
    fun audit():List<String> = ComponentRegistry.catalog.flatMap { definition ->
        val part=PlacedComponent(kind=definition.kind,reference="AUDIT",x=0f,y=0f)
        definition.parameters.mapNotNull { parameter ->
            val parsed=parse(part,parameter,input(parameter,parameter.default))
            if(parsed.error!=null || abs((parsed.value ?: Double.NaN)-parameter.default)>maxOf(1e-15,abs(parameter.default)*1e-12))
                "${definition.kind}.${parameter.key}: default does not round-trip" else null
        }
    }
}
