package com.indianservers.circuitssimulator.ui

import androidx.compose.runtime.Composable
import com.indianservers.circuitssimulator.domain.PlacedComponent

/** One properties/pin editor for every component, including development boards. */
@Composable
fun ComponentDetails(p:PlacedComponent,state:SimulatorState,model:SimulatorViewModel) {
    ComponentPropertiesPanel(p,state,model)
}
