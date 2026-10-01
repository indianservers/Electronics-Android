package com.indianservers.circuitssimulator

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.circuitssimulator.data.CircuitReport
import com.indianservers.circuitssimulator.domain.SampleCircuits
import com.indianservers.circuitssimulator.simulation.DcSolver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class CircuitReportInstrumentedTest {
    @Test fun imageAndPdfExportsContainCircuitSnapshot() {
        val circuit=SampleCircuits.divider()
        val result=DcSolver().solve(circuit)
        val png=ByteArrayOutputStream()
        CircuitReport.writePng(png,circuit,result)
        val bitmap=BitmapFactory.decodeByteArray(png.toByteArray(),0,png.size())
        assertNotNull(bitmap)
        assertEquals(1600,bitmap.width)
        assertEquals(1000,bitmap.height)
        bitmap.recycle()
        val pdf=ByteArrayOutputStream()
        CircuitReport.writePdf(pdf,circuit,result)
        assertTrue(pdf.toByteArray().copyOfRange(0,5).toString(Charsets.US_ASCII)=="%PDF-")
        assertTrue(pdf.size()>1000)
    }
}
