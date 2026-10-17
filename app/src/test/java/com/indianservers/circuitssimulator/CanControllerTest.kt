package com.indianservers.circuitssimulator
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Assert.*
import org.junit.Test
class CanControllerTest {
    @Test fun spiRegistersBitrateOneShotFramesBuffersAndReset() {
        val m=Mcp2515Model()
        fun tx(vararg bytes:Int):List<Int>{m.select(true);val result=bytes.map {m.transfer(it,0)};m.select(false);return result}
        tx(2,0x28,5,0xB1,0);assertEquals(500000,m.bitrate)
        tx(2,0x60,0x60);tx(2,0x0F,8)
        tx(2,0x31,100,32,0,0,3,9,8,7);tx(0x81)
        val frame=m.pending!!;assertEquals(0x321,frame.id);assertEquals(listOf(9,8,7),frame.data);assertTrue(frame.due>0)
        m.receive(frame);m.complete(true)
        assertEquals(1,m.registers[0x2C] and 1);assertEquals(0,m.registers[0x30] and 8)
        assertEquals(listOf(0,0,100,32,0,0,3,9,8,7),tx(3,0x61,0,0,0,0,0,0,0,0))
        tx(5,0x2C,1,0);assertEquals(0,m.registers[0x2C] and 1)
        tx(0xC0);assertEquals(0x80,m.mode);assertNull(m.pending)
    }
}
