package com.indianservers.circuitssimulator

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.ui.SimulatorViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GuideUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun guideOpensAndFirstStepRespondsToPlacementHintRestartAndResume() {
        rule.onNodeWithText("Guide",useUnmergedTree=true).performClick()
        rule.onNodeWithText("Light Your First LED").performClick()
        rule.onNodeWithText("Choose Battery below, then tap the canvas to place it.").assertExists()
        rule.onNodeWithText("Hint 0/2").performClick()
        rule.onNodeWithText("Open the component picker or use the Battery card.",substring=true).assertExists()
        rule.onNodeWithText("Battery",useUnmergedTree=true).performClick()
        rule.onNodeWithContentDescription("Circuit workspace",substring=true)
            .performTouchInput { click(center) }
        rule.waitUntil(3000) {
            ViewModelProvider(rule.activity)[SimulatorViewModel::class.java].state.value.guide?.stepIndex==1
        }
        rule.runOnIdle {
            val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
            assertTrue(model.state.value.circuit.components.any { it.kind==Kind.BATTERY })
        }
        rule.onNodeWithText("Restart").performClick()
        rule.onNodeWithText("Choose Battery below, then tap the canvas to place it.").assertExists()
        rule.onNodeWithText("Exit").performClick()
        rule.onNodeWithText("Continue: Light Your First LED",substring=true).assertExists()
        rule.onNodeWithText("Continue: Light Your First LED",substring=true).performClick()
        rule.onNodeWithText("Choose Battery below, then tap the canvas to place it.").assertExists()
    }
}
