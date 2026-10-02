package com.indianservers.circuitssimulator

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.TerminalRef
import com.indianservers.circuitssimulator.simulation.MeterMode
import com.indianservers.circuitssimulator.ui.SimulatorViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GuideRuntimeInstrumentedTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun firstLedCompletesThroughRealViewModelEvents() {
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        rule.runOnUiThread {
            model.startLesson("first-led")
            model.add(Kind.BATTERY,150f,450f)
            model.add(Kind.RESISTOR,470f,300f)
            model.add(Kind.LED,750f,450f)
            val c=model.state.value.circuit
            fun pin(kind:Kind,index:Int)=TerminalRef(c.components.first { it.kind==kind }.id,index)
            model.connect(pin(Kind.BATTERY,0),pin(Kind.RESISTOR,0))
            model.connect(pin(Kind.RESISTOR,1),pin(Kind.LED,0))
            model.connect(pin(Kind.LED,1),pin(Kind.BATTERY,1))
            model.connect(pin(Kind.BATTERY,1),pin(Kind.GROUND,0))
            assertEquals(8,model.state.value.guide?.stepIndex)
            model.toggleRun()
        }
        rule.waitUntil(5000) { model.state.value.guide?.complete==true }
        rule.runOnIdle { assertTrue(model.state.value.guide!!.complete) }
    }

    @Test fun voltageLessonUsesAttachedProbesAndRealMeter() {
        val model=ViewModelProvider(rule.activity)[SimulatorViewModel::class.java]
        rule.runOnUiThread { model.startLesson("measure-voltage");model.toggleRun() }
        rule.waitUntil(5000) { model.state.value.guide?.stepIndex==1 }
        rule.runOnUiThread {
            val c=model.state.value.circuit
            model.setMeterMode(MeterMode.DC_VOLTAGE)
            model.attachMeter("plus","guide-battery-resistor")
            model.attachMeter("common","guide-ground-wire")
        }
        rule.waitUntil(5000) { (model.state.value.guide?.stepIndex ?: 0)>=2 }
        rule.runOnIdle { assertTrue((model.state.value.guide?.stepIndex ?: 0)>=2) }
    }
}
