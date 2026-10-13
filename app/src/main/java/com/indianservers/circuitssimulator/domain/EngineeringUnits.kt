package com.indianservers.circuitssimulator.domain

import java.util.Locale
import java.math.BigDecimal
import kotlin.math.abs

object EngineeringUnits {
    fun format(value: Double, unit: String): String {
        if (!value.isFinite()) return "—"
        val a = abs(value)
        if (a < 1e-12) return "0 $unit"
        val scale = when {
            a == 0.0 -> 1.0 to ""
            a >= 1e6 -> 1e6 to "M"
            a >= 1e3 -> 1e3 to "k"
            a >= 1.0 -> 1.0 to ""
            a >= 1e-3 -> 1e-3 to "m"
            a >= 1e-6 -> 1e-6 to "µ"
            a >= 1e-9 -> 1e-9 to "n"
            else -> 1e-12 to "p"
        }
        return String.format(Locale.US, "%.3g %s%s", value / scale.first, scale.second, unit)
    }
    fun parse(text: String): Double? {
        val (value,suffix)=numberAndSuffix(text) ?: return null
        if(suffix.isNotEmpty() && !Regex("[A-Za-zΩ%°µμ²³·/ -]+").matches(suffix)) return null
        val scale=prefixes[suffix.take(1)] ?: 1.0
        return (value*scale).takeIf { it.isFinite() }
    }

    private val prefixes=mapOf("p" to 1e-12,"n" to 1e-9,"u" to 1e-6,"µ" to 1e-6,
        "μ" to 1e-6,"m" to 1e-3,"k" to 1e3,"M" to 1e6,"G" to 1e9)
    private fun numberAndSuffix(text:String):Pair<Double,String>? {
        val match=Regex("^\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*(.*?)\\s*$")
            .matchEntire(text.replace('−','-')) ?: return null
        val value=match.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return value to match.groupValues[2].trim()
    }
    /** Property input is checked against its own unit; ms-valued properties stay in milliseconds. */
    fun parse(text:String,expectedUnit:String):Double? {
        val (value,suffix)=numberAndSuffix(text) ?: return null
        if(suffix.isBlank()) return value
        fun canonical(unit:String)=when(unit.trim().lowercase(Locale.ROOT)) {
            "ohm","ohms","ω"->"Ω"
            "volt","volts","v"->"V"
            "amp","amps","a"->"A"
            "f","farad","farads"->"F"
            "h","henry","henries"->"H"
            "w","watt","watts"->"W"
            "hz"->"Hz"
            "s","sec","second","seconds"->"s"
            "c","°c","degc"->"°C"
            "%rh","% rh"->"% RH"
            else->unit.trim()
        }
        val expected=canonical(expectedUnit)
        if(canonical(suffix)==expected || expected=="% RH" && suffix=="%") return value
        if(expected.isEmpty()) return prefixes[suffix]?.let { (value*it).takeIf(Double::isFinite) }
        val (base,unitScale)=when(expected) { "ms"->"s" to .001;"mT"->"T" to .001;else->expected to 1.0 }
        val suffixScale=when {
            canonical(suffix)==base -> 1.0
            suffix.take(1) in prefixes && canonical(suffix.drop(1))==base -> prefixes.getValue(suffix.take(1))
            else -> return null
        }
        return BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(suffixScale))
            .divide(BigDecimal.valueOf(unitScale)).toDouble().takeIf { it.isFinite() }
    }
}
