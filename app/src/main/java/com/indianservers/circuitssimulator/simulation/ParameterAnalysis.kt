package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*
import java.util.Random

data class ParameterPoint(val parameterValue: Double, val voltage: Double)
data class ParameterSweep(val points: List<ParameterPoint> = emptyList(), val error: String? = null)
data class ToleranceSummary(val nominal: Double, val minimum: Double, val maximum: Double,
                            val mean: Double, val samples: Int, val seed: Long)

/** Repeated DC operating points. Every run uses an immutable copy of the circuit. */
class ParameterAnalysis(private val solver: DcSolver = DcSolver()) {
    fun sweep(circuit: Circuit, componentId: String, key: String, probe: TerminalRef,
              start: Double, stop: Double, count: Int = 41): ParameterSweep {
        val part = circuit.components.firstOrNull { it.id == componentId }
            ?: return ParameterSweep(error = "Choose a component.")
        val definition = ComponentRegistry.definitions.getValue(part.kind).parameters.firstOrNull { it.key == key }
            ?: return ParameterSweep(error = "Choose a numeric parameter.")
        if (probe.index !in 0 until (circuit.components.firstOrNull { it.id == probe.componentId }?.terminalCount ?: 0))
            return ParameterSweep(error = "Choose a valid voltage probe.")
        if (!start.isFinite() || !stop.isFinite() || start >= stop ||
            start < definition.min || stop > definition.max || count !in 2..201)
            return ParameterSweep(error = "Range must increase and stay within the component limits.")
        val points = ArrayList<ParameterPoint>(count)
        repeat(count) { index ->
            val value = start + (stop - start) * index / (count - 1)
            val changed = circuit.copy(components = circuit.components.map {
                if (it.id == componentId) it.copy(parameters = it.parameters + (key to value)) else it
            })
            val result = solver.solve(changed)
            if (result.error != null || !result.converged)
                return ParameterSweep(points, "Solve failed at ${"%.5g".format(value)}: ${result.error ?: "no convergence"}")
            points += ParameterPoint(value, result.nodeVoltages[probe] ?: 0.0)
        }
        return ParameterSweep(points)
    }

    /** Reproducible resistor-tolerance sampling; the saved circuit is never modified. */
    fun tolerance(circuit: Circuit, probe: TerminalRef, count: Int = 100,
                  seed: Long = 1L): Pair<ToleranceSummary?, String?> {
        if (count !in 2..1000) return null to "Sample count must be 2–1000."
        val resistors = circuit.components.filter { it.kind == Kind.RESISTOR && it.value("tolerance") > 0.0 }
        if (resistors.isEmpty()) return null to "Add a resistor with a tolerance value."
        val nominal = solver.solve(circuit)
        if (nominal.error != null || !nominal.converged) return null to (nominal.error ?: "Nominal solve failed.")
        val random = Random(seed)
        val values = ArrayList<Double>(count)
        repeat(count) { index ->
            val perturbed = circuit.copy(components = circuit.components.map { part ->
                if (part !in resistors) part else {
                    val fraction = part.value("tolerance") / 100.0
                    val resistance = part.value("resistance") * (1.0 + (2.0 * random.nextDouble() - 1.0) * fraction)
                    part.copy(parameters = part.parameters + ("resistance" to resistance))
                }
            })
            val result = solver.solve(perturbed)
            if (result.error != null || !result.converged)
                return null to "Tolerance solve failed at sample ${index + 1}: ${result.error ?: "no convergence"}"
            values += result.nodeVoltages[probe] ?: 0.0
        }
        return ToleranceSummary(nominal.nodeVoltages[probe] ?: 0.0, values.minOrNull()!!,
            values.maxOrNull()!!, values.average(), count, seed) to null
    }
}

fun ParameterSweep.toCsv(): String = buildString {
    append("parameter_value,probe_voltage_v\n")
    points.forEach { append(it.parameterValue).append(',').append(it.voltage).append('\n') }
}
