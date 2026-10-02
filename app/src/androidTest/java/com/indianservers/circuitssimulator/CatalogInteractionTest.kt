package com.indianservers.circuitssimulator

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.SampleCircuits
import com.indianservers.circuitssimulator.ui.SimulatorViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CatalogInteractionTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Before fun openEmptyDesigner() {
        val activity=rule.activity
        rule.runOnIdle { ViewModelProvider(activity)[SimulatorViewModel::class.java]
            .loadSample(SampleCircuits.led()) }
        if(rule.onAllNodesWithText("Simulation").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithText("Circuit Designer").performClick()
    }

    @Test fun partNumberSearchNarrowsCardsAndTapStartsPlacement() {
        rule.onNodeWithText("View all",substring=true).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("1n4148")
        rule.onNodeWithText("Diode").assertExists().performClick()
        rule.onNodeWithText("Tap canvas to place Diode").assertExists()
    }

    @Test fun digitalGateIsSelectableForCanvasPlacement() {
        rule.onNodeWithText("View all",substring=true).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("NOT gate")
        rule.onNodeWithContentDescription("Add NOT gate",substring=true).assertExists().performClick()
        rule.onNodeWithText("Tap canvas to place NOT gate").assertExists()
    }
}
