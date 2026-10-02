package com.indianservers.circuitssimulator

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.ui.SimulatorViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhaseOneExploreUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun findNodeMcuInExploreAndPlaceItOnDesignerCanvas() {
        rule.onNodeWithText("Explore Components").performClick()
        rule.onNodeWithContentDescription("Search").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("NodeMCU")
        rule.onNodeWithText("NodeMCU ESP8266").performClick()
        rule.onNodeWithText("Use in Designer").performClick()
        rule.onNodeWithText("Tap canvas to place NodeMCU ESP8266").assertExists()
        rule.onNodeWithContentDescription("Circuit workspace",substring=true)
            .performTouchInput { click(center) }
        rule.waitUntil(timeoutMillis=3000) {
            val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
            model.state.value.circuit.components.any { it.kind==Kind.NODEMCU_ESP8266 }
        }
        rule.runOnIdle {
            val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
            assertTrue(model.state.value.circuit.components.any { it.kind==Kind.NODEMCU_ESP8266 })
        }
    }
}
