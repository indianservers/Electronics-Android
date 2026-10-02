package com.indianservers.circuitssimulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indianservers.circuitssimulator.data.GuideStore
import com.indianservers.circuitssimulator.guide.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuideStoreInstrumentedTest {
    @Test fun guideCircuitAndStepResumeWithoutChangingNormalProject() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=GuideStore(context)
        val circuit=LessonTemplates.led("Stored lesson")
        val session=GuideSession("measure-voltage",stepIndex=2,hintLevels=mapOf("resistor" to 1),
            eventLog=listOf(GuideEvent(GuideEventType.PROBE_ATTACHED,"plus")))
        store.save(session,circuit)
        val restored=store.load()
        assertNotNull(restored)
        assertEquals(session.lessonId,restored!!.first.lessonId)
        assertEquals(2,restored.first.stepIndex)
        assertEquals(1,restored.first.hintLevels["resistor"])
        assertEquals(circuit,restored.second)
        store.clear()
    }
}
