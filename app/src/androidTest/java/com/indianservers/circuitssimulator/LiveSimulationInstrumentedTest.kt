package com.indianservers.circuitssimulator

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.SampleCircuits
import com.indianservers.circuitssimulator.ui.SimulatorViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveSimulationInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun runPauseEditSpeedAndRewindKeepAContinuousElectricalTrace() {
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        rule.runOnIdle { model.loadSample(SampleCircuits.rc()) }
        rule.waitUntil(5000) { model.state.value.elapsedSeconds>.04 }
        rule.runOnIdle { model.toggleRun() }
        Thread.sleep(200)
        val paused=model.state.value.elapsedSeconds
        Thread.sleep(150)
        assertEquals(paused,model.state.value.elapsedSeconds,1e-9)
        val firstTime=model.state.value.transient!!.frames.first().timeSeconds
        val resistor=model.state.value.circuit.components.first { it.kind==Kind.RESISTOR }
        rule.runOnIdle {
            model.setParameter(resistor.id,"resistance",470.0)
            model.setSimulationSpeed(4.0)
        }
        rule.waitUntil(5000) { (model.state.value.transient?.frames?.lastOrNull()?.timeSeconds ?: 0.0) > paused }
        assertEquals(4.0,model.state.value.simulationSpeed,0.0)
        assertEquals(firstTime,model.state.value.transient!!.frames.first().timeSeconds,1e-9)
        rule.runOnIdle { model.toggleRun() }
        rule.waitUntil(5000) { model.state.value.elapsedSeconds>paused+.03 }
        rule.runOnIdle { model.resetTime() }
        rule.waitUntil(5000) { model.state.value.transient?.frames?.isNotEmpty()==true &&
            model.state.value.elapsedSeconds<.02 }
        assertTrue(model.state.value.transient!!.frames.first().timeSeconds<.02)
    }
}
