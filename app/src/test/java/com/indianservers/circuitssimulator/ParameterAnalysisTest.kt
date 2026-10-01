package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test

class ParameterAnalysisTest {
    @Test fun dividerSweepAndExport() {
        val circuit = SampleCircuits.divider()
        val upper = circuit.components.first { it.reference == "R1" }
        val lower = circuit.components.first { it.reference == "R2" }
        val probe = TerminalRef(lower.id, 0)
        val result = ParameterAnalysis().sweep(circuit, upper.id, "resistance", probe, 1000.0, 9000.0, 9)
        assertNull(result.error)
        assertEquals(9, result.points.size)
        assertEquals(5.0, result.points.first().voltage, 1e-5)
        assertEquals(1.0, result.points.last().voltage, 1e-5)
        assertTrue(result.toCsv().startsWith("parameter_value,probe_voltage_v\n"))
        assertEquals(1000.0, upper.value("resistance"), 0.0)
    }

    @Test fun seededToleranceIsRepeatableAndBounded() {
        val circuit = SampleCircuits.divider()
        val probe = TerminalRef(circuit.components.first { it.reference == "R2" }.id, 0)
        val analysis = ParameterAnalysis()
        val first = analysis.tolerance(circuit, probe, 100, 17L)
        val second = analysis.tolerance(circuit, probe, 100, 17L)
        assertNull(first.second)
        assertEquals(first.first, second.first)
        val summary = first.first!!
        assertEquals(5.0, summary.nominal, 1e-5)
        assertTrue(summary.minimum > 4.7 && summary.minimum < 5.0)
        assertTrue(summary.maximum > 5.0 && summary.maximum < 5.3)
    }

    @Test fun invalidRangeIsRejected() {
        val circuit = SampleCircuits.divider()
        val resistor = circuit.components.first { it.reference == "R1" }
        val probe = TerminalRef(resistor.id, 0)
        assertNotNull(ParameterAnalysis().sweep(circuit, resistor.id, "resistance", probe,
            9000.0, 1000.0).error)
    }
}
