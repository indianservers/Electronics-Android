package com.indianservers.circuitssimulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The host runner must also retrieve this file AFTER Gradle has completed cleanup. */
@RunWith(AndroidJUnit4::class)
class AndroidEvidenceExportInstrumentedTest {
    @Test fun writePostRunExportProbe() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir,"validation-export-probe.json").writeText("{\"exportProbe\":\"PASSED\"}")
    }
}
