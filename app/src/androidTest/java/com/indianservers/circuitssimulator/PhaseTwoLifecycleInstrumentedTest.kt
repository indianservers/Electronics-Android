package com.indianservers.circuitssimulator
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.firmware.PhaseTwoProjects
import com.indianservers.circuitssimulator.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

class PhaseTwoLifecycleInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun tenSensorFirmwareSurvivesRotationRecreationAndBackground(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val raw=instrumentation.context.assets.open("phase2-projects/arduino_uno-16/circuit.json").use {it.readBytes().toString(Charsets.UTF_8)}
        val circuit=CircuitJson.decode(raw);assertEquals(PhaseTwoProjects.circuit(Kind.ARDUINO_UNO,16),circuit)
        fun model()=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        var vm=model()
        rule.onNodeWithText("Circuit Designer").performScrollTo().performClick()
        rule.runOnUiThread {vm.dismissRecovery();vm.loadSample(circuit);vm.showFirmware(true)
            vm.setFirmwareSource(circuit.firmware.single().source);assertTrue(vm.compileFirmware());vm.showFirmware(false);vm.runFirmware()}
        rule.waitUntil(60000){(vm.state.value.firmware.variables["cycles"]?.toDoubleOrNull() ?: 0.0)>=1}
        val expectedReadings=linkedMapOf<String,Double>().apply {
            repeat(7){put("t$it",25.0+it*.5)}
            put("lux0",10.0);put("lux1",10.0);put("ax",0.0)
        }
        val checkpoints=JSONArray()
        fun assertReadings(checkpoint:String){
            rule.waitUntil(30000){rule.activity.hasWindowFocus()}
            rule.onNodeWithText("Connect").assertIsDisplayed()
            assertNull(vm.state.value.firmware.error)
            assertEquals(circuit.components,vm.state.value.circuit.components)
            val variables=vm.state.value.firmware.variables
            assertEquals(0.0,variables.getValue("faults").toDouble(),0.0)
            assertTrue(variables.getValue("cycles").toDouble()>=1.0)
            val observed=JSONObject()
            expectedReadings.forEach {(name,expected)->
                val actual=variables.getValue(name).toDouble()
                assertEquals("$checkpoint $name",expected,actual,.0000001)
                observed.put(name,actual)
            }
            checkpoints.put(JSONObject().put("checkpoint",checkpoint)
                .put("expected",JSONObject(expectedReadings)).put("observed",observed))
        }
        fun capture(name:String){
            rule.waitForIdle()
            // Configuration changes precede the OS screen-rotation animation.
            SystemClock.sleep(750)
            rule.onNodeWithText("Connect").assertIsDisplayed()
            assertTrue("Application window is obscured by another window",rule.activity.hasWindowFocus())
            val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            assertEquals("Screenshot orientation must match the actual activity",rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE,bitmap.width>bitmap.height)
            val output=File(instrumentation.targetContext.getExternalFilesDir(null),"visual-qa").apply {mkdirs()}
            File(output,name).outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
            bitmap.recycle()
        }
        assertReadings("portrait")
        capture("phase2-ten-sensor-portrait.png")
        rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        rule.waitUntil(30000){rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        assertReadings("landscape")
        capture("phase2-ten-sensor-landscape.png")
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle();assertReadings("foreground-restored")
        rule.activityRule.scenario.recreate();vm=model()
        rule.waitUntil(30000){vm.state.value.circuit.components==circuit.components}
        assertReadings("recreated")
        capture("phase2-ten-sensor-recreated.png")
        rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
        rule.waitUntil(30000){rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
        assertReadings("portrait-restored")
        capture("phase2-ten-sensor-portrait-restored.png")
        val result=JSONObject().put("status","PASSED_MODELED_SCOPE").put("sensors",10)
            .put("actualRotation",true).put("activityRecreation",true).put("backgroundForeground",true)
            .put("realAppNavigation",true).put("workspaceVisibleAfterEveryTransition",true).put("foregroundWindowVerified",true)
            .put("allTenSensorReadingsVerifiedAfterEveryTransition",true).put("sensorReadingCheckpoints",checkpoints)
            .put("lowMemoryPressure","NOT_EXECUTED").put("physicalDevice","NOT_EXECUTED")
        File(instrumentation.targetContext.filesDir,"phase2-lifecycle-results.json").writeText(result.toString(2))
        rule.runOnUiThread {vm.stopFirmware()}
    }
}
