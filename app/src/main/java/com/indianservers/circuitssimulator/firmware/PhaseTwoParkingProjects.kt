package com.indianservers.circuitssimulator.firmware
import com.indianservers.circuitssimulator.domain.*

/** Two independently triggered bays plus eight ambient thermal channels, not ten fictitious bays. */
object PhaseTwoParkingProjects {
    fun circuit(kind:Kind):Circuit {
        val base=DomainMonitoringProjects.circuit(kind,19)
        val def=BoardRegistry.boards.getValue(kind);val bus=def.hardware.i2cPins!!;val uart=def.hardware.uartPins.first()
        val reserved=(listOf(bus.first,bus.second,uart.first,uart.second)+def.hardware.bootLevels.keys).mapNotNull {def.pin(it)?.signal}.toSet()
        val gpio=def.pins.filter {it.connectable && PinCapability.DIGITAL_INPUT in it.capabilities && PinCapability.DIGITAL_OUTPUT in it.capabilities && it.signal !in reserved}.distinctBy {it.signal}.take(4)
        require(gpio.size==4){"Four unclaimed GPIO required for two triggers, shared isolated echo and status output"}
        val removed=setOf("ACC","CACC","IMU","CIMU")
        val parts=base.components.filter {it.id !in removed}.toMutableList()
        val wires=base.wires.filter {it.start.componentId !in removed && it.end.componentId !in removed}.map {w->
            if(w.end==TerminalRef("RLIMIT",0) && w.start.componentId=="B1")w.copy(start=TerminalRef("B1",def.index(gpio[3].name)))else w
        }.toMutableList()
        fun p(k:Kind,id:String,params:Map<String,Double> = emptyMap()):PlacedComponent = PlacedComponent(id=id,kind=k,reference=id,
            x=100f+parts.size%6*180,y=100f+parts.size/6*180,
            parameters=ComponentRegistry.definitions.getValue(k).parameters.associate {it.key to it.default}+params).also {parts+=it}
        fun w(a:String,ai:Int,b:String,bi:Int){wires+=Wire(id="PARK${wires.size}",start=TerminalRef(a,ai),end=TerminalRef(b,bi))}
        val q=p(Kind.NPN_BJT,"ECHO_INVERTER");val rb=p(Kind.RESISTOR,"ECHO_RB",mapOf("resistance" to 10000.0))
        val rc=p(Kind.RESISTOR,"ECHO_RC",mapOf("resistance" to 10000.0));val pd=p(Kind.RESISTOR,"ECHO_PD",mapOf("resistance" to 100000.0))
        w(q.id,2,"G1",0);w(rb.id,1,q.id,1);w(pd.id,0,rb.id,0);w(pd.id,1,"G1",0)
        w("B1",def.index(def.supplyPins.first()),rc.id,0);w(rc.id,1,q.id,0);w(q.id,0,"B1",def.index(gpio[2].name))
        repeat(2){i->
            val s=p(Kind.ULTRASONIC,"U$i",mapOf("useEnvironment" to 0.0,"distanceCm" to 150.0+i*50))
            val diode=p(Kind.RECTIFIER,"ECHO_D$i")
            w("V5",0,s.id,0);w("G1",0,s.id,1);w("B1",def.index(gpio[i].name),s.id,2)
            w(s.id,3,diode.id,0);w(diode.id,1,rb.id,0)
        }
        val port=def.hardware.uartNames.firstOrNull() ?: "Serial"
        val program=buildString {
            append("// Two-bay occupancy and eight thermal channels. Generic 58 us/cm sonar; no acoustic crosstalk model.\nvoid setup(){Wire.begin();$port.begin(115200);")
            append("pinMode(${gpio[0].name},OUTPUT);pinMode(${gpio[1].name},OUTPUT);pinMode(${gpio[2].name},INPUT);pinMode(${gpio[3].name},OUTPUT);")
            append("int cycles=0;int faults=0;int occupied=0;int alarm=0;double distance0=0;double distance1=0;")
            repeat(8){append("double t$it=0;")};append("}\nvoid loop(){faults=0;occupied=0;")
            repeat(8){i->append("Wire.beginTransmission(${72+i});Wire.write(0);int ack$i=Wire.endTransmission(false);int count$i=Wire.requestFrom(${72+i},2);int hi$i=Wire.read();int lo$i=Wire.read();int raw$i=hi$i*256+lo$i;if(raw$i>=32768){raw$i=raw$i-65536;}if(ack$i==0 && count$i==2){t$i=raw$i/256.0;}else{faults=faults+1;}")}
            repeat(2){i->append("digitalWrite(${gpio[i].name},LOW);delayMicroseconds(2);digitalWrite(${gpio[i].name},HIGH);delayMicroseconds(10);digitalWrite(${gpio[i].name},LOW);int duration$i=pulseIn(${gpio[2].name},LOW,30000);if(duration$i>0){distance$i=duration$i/58.0;if(distance$i<100){occupied=occupied+1;}}else{faults=faults+1;}")}
            append("alarm=0;if(occupied==2 || faults>0){alarm=1;}");repeat(8){append("if(t$it>70){alarm=1;}")}
            append("digitalWrite(${gpio[3].name},alarm);$port.print(occupied);$port.print(\":\");$port.println(faults);cycles=cycles+1;delay(200);}")
        }
        return base.copy(name="Two-bay parking occupancy / ${def.product}",components=parts,wires=wires,
            firmware=listOf(FirmwareAttachment("B1",FirmwareLanguage.ARDUINO_SUBSET,program,true)))
    }
}
