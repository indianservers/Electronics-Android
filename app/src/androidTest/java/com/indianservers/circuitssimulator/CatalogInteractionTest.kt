package com.indianservers.circuitssimulator

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class CatalogInteractionTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun partNumberSearchNarrowsCardsAndTapStartsPlacement() {
        rule.onNodeWithText("View all",substring=true).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("1n4148")
        rule.onNodeWithText("Diode").assertExists().performClick()
        rule.onNodeWithText("Tap canvas to place Diode").assertExists()
    }
}
