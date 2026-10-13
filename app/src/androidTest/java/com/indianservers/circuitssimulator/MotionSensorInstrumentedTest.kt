package com.indianservers.circuitssimulator

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.firmware.MotionSensorExamples
import com.indianservers.circuitssimulator.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MotionSensorInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun gyroPropertiesEditRealMotionAndPreserveWiringWithUndo() {
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        val circuit=MotionSensorExamples.circuit(Kind.LSM6DSOX_IMU)
        val sensor=circuit.components.first { it.kind==Kind.LSM6DSOX_IMU }
        rule.runOnUiThread {
            model.dismissRecovery();model.loadSample(circuit);model.select(sensor.id)
            rule.activity.setContent {
                val state by model.state.collectAsState()
                MaterialTheme(colorScheme=darkColorScheme()) {
                    Column(Modifier.fillMaxSize()) {
                        CircuitCanvas(state,model,Modifier.fillMaxWidth().weight(1f))
                        state.circuit.components.first { it.id==sensor.id }.let { ComponentDetails(it,state,model) }
                    }
                }
            }
        }
        rule.onNodeWithTag("property-gyroXDps").performScrollTo().performTextReplacement("-90 °/s")
        rule.onNodeWithTag("apply-gyroXDps").performScrollTo().performClick()
        rule.waitUntil(5000) { model.state.value.circuit.components.first { it.id==sensor.id }.value("gyroXDps")==-90.0 }
        assertEquals(circuit.wires,model.state.value.circuit.wires)
        rule.runOnUiThread { model.undo() }
        assertEquals(87.5,model.state.value.circuit.components.first { it.id==sensor.id }.value("gyroXDps"),0.0)
        rule.onNodeWithText("Pins",substring=false).performClick()
        listOf("VCC","GND","SDA","SCL").forEach { rule.onNodeWithText(it,substring=false).assertExists() }
        val image=rule.onRoot().captureToImage()
        java.io.File(rule.activity.getExternalFilesDir(null),"motion-sensor-pins.png").outputStream().use {
            image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        rule.onNodeWithText("Specifications",substring=false).performClick()
        rule.onNodeWithText("STMicroelectronics",substring=true).assertExists()
    }
    @Test fun sensorSampleCanBeFoundAndOpenedFromTheSampleBrowser() {
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        rule.runOnUiThread { model.dismissRecovery();rule.activity.setContent { MaterialTheme { SampleBrowser(model) } } }
        rule.onNodeWithText("Search samples").performTextInput("L3GD20")
        rule.onNodeWithText("L3GD20 Motion Lab",substring=false).performClick()
        assertTrue(model.state.value.circuit.components.any { it.kind==Kind.L3GD20_GYRO })
        assertTrue(model.state.value.circuit.firmware.single().source.contains("Wire.requestFrom(106, 6)"))
    }
    @Test fun allMotionLabsSaveAndLoadExactPinsPropertiesAndPrograms() {
        MotionSensorParts.profiles.keys.forEach { kind ->
            val circuit=MotionSensorExamples.circuit(kind)
            assertEquals(circuit,CircuitJson.decode(CircuitJson.encode(circuit)))
        }
    }
}
