package com.indianservers.circuitssimulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indianservers.circuitssimulator.data.ProjectStore
import com.indianservers.circuitssimulator.domain.SampleCircuits
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProjectStoreInstrumentedTest {
    @Test fun damagedProjectsRemainVisibleAndBackupCanBeRecovered() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=ProjectStore(context)
        val broken=store.save(null,SampleCircuits.led())
        val recoverable=store.save(null,SampleCircuits.divider())
        try {
            File(context.filesDir,"projects/$broken.json").writeText("damaged JSON")
            assertTrue(store.list().any { it.id==broken && it.unreadable })
            val base=File(context.filesDir,"projects/$recoverable.json")
            assertTrue(base.renameTo(File(context.filesDir,"projects/$recoverable.json.bak")))
            assertEquals("Voltage Divider",store.list().first { it.id==recoverable }.name)
            assertEquals("Voltage Divider",store.open(recoverable).name)
        } finally {
            store.delete(broken);store.delete(recoverable)
        }
    }

    @Test fun namedProjectCopyAndAutosaveStaySeparate() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=ProjectStore(context)
        val autosave=File(context.filesDir,"working_session.json")
        val prior=autosave.takeIf { it.exists() }?.readText()
        var first:String?=null;var copy:String?=null
        try {
            val circuit=SampleCircuits.rcLowPass()
            val firstId=store.save(null,circuit)
            first=firstId
            assertEquals(circuit,store.open(firstId))
            val copyId=store.duplicate(firstId)
            copy=copyId
            assertEquals("RC Low-pass Copy",store.open(copyId).name)
            store.autosave(SampleCircuits.fuseFault())
            assertEquals("Fuse Fault",store.recovery()?.name)
            store.discardRecovery()
            assertNull(store.recovery())
            assertEquals(circuit,store.open(firstId))
            assertTrue(store.list().any { it.id==copyId })
        } finally {
            first?.let(store::delete);copy?.let(store::delete)
            if(prior==null) autosave.delete() else autosave.writeText(prior)
        }
    }
}
