package com.indianservers.circuitssimulator.validation
import com.indianservers.circuitssimulator.validation.CountedChecks.check
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import kotlin.math.abs
object VehicleProjectValidation {
    fun run(kind:Kind):List<String> {
        val original=VehicleProjects.circuit(kind);val c=CircuitJson.decode(CircuitJson.encode(original));check(c==original)
        val rows=mutableListOf<String>()
        fun verify(name:String,circuit:Circuit,alarm:Int,link:Boolean=true,extra:(RuntimeScheduler)->Unit = {}):RuntimeScheduler {
            val r=RuntimeScheduler().compile(circuit)
            repeat(30){val step=r.advance(10_000);check(step.error==null){"$name: ${step.error}"};check(step.frames.all {it.result.error==null})}
            val v=r.sessions.getValue("B1").variables();fun n(key:String)=v.getValue(key).toDouble()
            check(n("cycles")>=1){"$name no dashboard loop"}
            check(n("alarm")==alarm.toDouble()){"$name alarm ${n("alarm")}; $v; ECU ${r.sessions.getValue("ECU").variables()}; CAN ${r.fabric.spiDevices.map {it.ownerId to it.can?.registers?.toList()}}; log ${r.fabric.log.filter {it.kind==BusKind.CAN}}"}
            if(link){check(n("received")>=1){"$name no CAN data: ${r.fabric.log}"};check(r.fabric.log.any {it.kind==BusKind.CAN && it.ack==true})}
            else {check(n("received")==0.0);check(n("faults")>=1)}
            val current=abs(r.sessions.getValue("B1").board.latest!!.readings.getValue("ALARM").current)
            check(if(alarm==0)current<1e-6 else current>1e-4){"$name LED $current"}
            extra(r);rows+="$name,PASSED,300000";return r
        }
        val normal=verify("two-controller-normal",c,0){r->val v=r.sessions.getValue("ECU").variables();repeat(8){check(v.getValue("t$it").toDouble()==30+it*2.0)} }
        val received=normal.sessions.getValue("B1").variables().getValue("received").toDouble()
        normal.resetBoard("ECU");repeat(30){check(normal.advance(10000).error==null)}
        check(normal.sessions.getValue("B1").variables().getValue("received").toDouble()>received)
        rows+="ecu-reset,PASSED,600000"
        repeat(8){i->verify("independent-thermal-$i",ChemistryProjectValidation.changed(c,"T$i","temperatureC",95.0),1)}
        verify("co-excursion",ChemistryProjectValidation.changed(c,"CO","coPpm",100.0),1){r->check(abs(r.sessions.getValue("ECU").variables().getValue("co").toDouble()-100)<2)}
        verify("pressure-excursion",ChemistryProjectValidation.changed(c,"PRESSURE","pressureKpa",8.0),1)
        verify("remote-i2c-fault",c.copy(wires=c.wires.filterNot {it.end==TerminalRef("T0",2)}),1)
        verify("canh-disconnected",c.copy(wires=c.wires.filterNot {it.start==TerminalRef("CAN_DASH",6) && it.end==TerminalRef("CAN_ECU",6)}),1,false)
        verify("termination-loss",c.copy(components=c.components.filterNot {it.id=="TERM0"},wires=c.wires.filterNot {it.start.componentId=="TERM0" || it.end.componentId=="TERM0"}),1,false)
        verify("transceiver-supply-loss",ChemistryProjectValidation.changed(c,"V5","voltage",0.0),1,false)
        val mismatch=c.copy(firmware=c.firmware.map {if(it.boardId=="ECU")it.copy(source=it.source.replace("SPI.transfer(42);SPI.transfer(0);","SPI.transfer(42);SPI.transfer(1);")) else it})
        verify("can-bit-rate-mismatch",mismatch,1,false){r->
            val can=r.fabric.spiDevices.first {it.ownerId=="CAN_ECU"}.can!!
            check(can.registers[0x30] and 0x50==0x50);check(can.registers[0x2C] and 0x80!=0)
        }
        verify("can-pair-short",c.copy(wires=c.wires+Wire(id="CAN_SHORT",start=TerminalRef("CAN_DASH",6),end=TerminalRef("CAN_DASH",7))),1,false)
        verify("save-reload",CircuitJson.decode(CircuitJson.encode(c)),0)
        return rows
    }
}
