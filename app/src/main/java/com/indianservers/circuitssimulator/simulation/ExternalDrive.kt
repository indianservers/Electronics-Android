package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.TerminalRef

/** A finite-impedance voltage driver attached to an existing circuit terminal. */
data class ExternalDrive(val terminal: TerminalRef, val voltage: Double, val resistanceOhms: Double) {
    init {
        require(voltage.isFinite())
        require(resistanceOhms.isFinite() && resistanceOhms > 0.0)
    }
}
