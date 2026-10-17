package com.indianservers.circuitssimulator
import com.indianservers.circuitssimulator.domain.TerminalRef
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Assert.*
import org.junit.Test
class BusTrafficRegressionTest {
    @Test fun sustainedTrafficRetainsOnlyNeededHistoryWithoutDroppingSlowPeerState() {
        val f=ProtocolFabric();val pin=TerminalRef("S",0)
        f.registerConsumer("A");f.registerConsumer("B")
        f.waveforms.add(0,"test",pin,0.0);f.waveforms.add(50,"test",pin,1.0);f.waveforms.add(100,"test",pin,2.0)
        f.consumerAdvanced("A",100);assertEquals(0.0,f.waveforms.drives(25).single().voltage,0.0)
        f.consumerAdvanced("B",60);assertEquals(1.0,f.waveforms.drives(60).single().voltage,0.0)
        f.consumerAdvanced("B",100)
        repeat(60000){i->val t=101L+i;f.waveforms.add(t,"test",pin,(i%2).toDouble());f.consumerAdvanced("A",t);f.consumerAdvanced("B",t)}
        assertEquals(1.0,f.waveforms.drives(60100).single().voltage,0.0)
    }
}
