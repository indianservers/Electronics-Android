package com.indianservers.circuitssimulator
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Test
import org.junit.Assert.*

class FirmwarePlaybackTest {
    @Test fun validTenSensorPlaybackSurvivesALongWallClockTick(){
        val runtime=RuntimeScheduler().compile(PhaseTwoProjects.circuit(Kind.ARDUINO_UNO,16))
        val result=FirmwarePlayback.advance(runtime,2_000_000)
        assertNull(result.error)
        assertEquals(2_000_000L,result.timeMicros)
        val values=runtime.sessions.getValue("B1").variables()
        assertEquals(0.0,values.getValue("faults").toDouble(),0.0)
        assertEquals(10.0,values.getValue("lux0").toDouble(),.0000001)
        assertEquals(10.0,values.getValue("lux1").toDouble(),.0000001)
        assertTrue(values.getValue("cycles").toDouble()>=8)
        assertTrue(result.frames.size<=4000)
        assertEquals(2_000_000L,result.frames.last().timeMicros)
    }
    @Test fun invalidBusyLoopStillHitsTheInstructionGuard(){
        val c=PhaseTwoProjects.circuit(Kind.ARDUINO_UNO,16)
        val runtime=RuntimeScheduler().compile(c.copy(firmware=c.firmware.map {it.copy(source="void setup(){int i=0;}void loop(){i=i+1;}")}))
        val result=FirmwarePlayback.advance(runtime,2_000_000)
        assertTrue(result.error?.message?.contains("instruction budget")==true)
        assertEquals(0L,result.timeMicros)
    }
    @Test fun bothControllersKeepTheSharedClockAndCanExchange(){
        val runtime=RuntimeScheduler().compile(VehicleProjects.circuit(Kind.ARDUINO_UNO))
        val result=FirmwarePlayback.advance(runtime,300_000)
        assertNull(result.error)
        assertEquals(300_000L,result.timeMicros)
        assertTrue(runtime.sessions.values.all {it.timeMicros==300_000L})
        val dashboard=runtime.sessions.getValue("B1").variables()
        assertTrue(dashboard.getValue("received").toDouble()>=1)
        assertEquals(0.0,dashboard.getValue("faults").toDouble(),0.0)
        assertEquals(setOf("B1","ECU"),result.variables.keys)
    }
}
