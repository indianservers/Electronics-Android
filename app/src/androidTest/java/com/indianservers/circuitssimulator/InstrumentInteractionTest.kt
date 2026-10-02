package com.indianservers.circuitssimulator

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.SampleCircuits
import com.indianservers.circuitssimulator.ui.SimulatorViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InstrumentInteractionTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Before fun startFromIdleSample() {
        val activity=rule.activity
        rule.runOnUiThread {
            val model=ViewModelProvider(activity)[SimulatorViewModel::class.java]
            model.loadSample(SampleCircuits.led())
            if(model.state.value.running) model.toggleRun()
        }
        if(rule.onAllNodesWithText("Simulation").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithText("Circuit Designer").performClick()
    }

    @Test fun scopeOpensFromSimulationControl() {
        rule.onNodeWithText("Simulation").performClick()
        rule.onNodeWithText("Oscilloscope").performClick()
        rule.onNodeWithText("Oscilloscope").assertExists()
        rule.onNodeWithText("CH1",substring=true).assertExists()
    }

    @Test fun multimeterOpensFromSimulationControl() {
        rule.onNodeWithText("Simulation").performClick()
        rule.onNodeWithText("Multimeter").performClick()
        rule.onNodeWithText("DC V").assertExists()
        rule.onNodeWithText("Continuity").assertExists()
    }
}
