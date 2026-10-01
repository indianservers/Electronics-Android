package com.indianservers.circuitssimulator.domain

import java.util.Locale
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
        val match = Regex("^\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*([pnuµmkM]?)\\s*[A-Za-zΩ%°]*\\s*$").matchEntire(text) ?: return null
        val scale = when (match.groupValues[2]) {
            "p" -> 1e-12; "n" -> 1e-9; "u", "µ" -> 1e-6; "m" -> 1e-3
            "k" -> 1e3; "M" -> 1e6; else -> 1.0
        }
        return match.groupValues[1].toDoubleOrNull()?.times(scale)
    }
}
