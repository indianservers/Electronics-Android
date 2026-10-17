package com.indianservers.circuitssimulator.firmware
import com.indianservers.circuitssimulator.domain.*
object PowerMonitoringProjects {
    fun circuit(kind:Kind):Circuit {
        val d=BoardRegistry.boards.getValue(kind);val bus=requireNotNull(d.hardware.i2cPins)
        val uart=d.hardware.uartPins.first();val port=d.hardware.uartNames.firstOrNull() ?: "Serial"
        val reserved=(listOf(bus.first,bus.second,uart.first,uart.second)+d.hardware.bootLevels.keys).mapNotNull {d.pin(it)?.signal}.toSet()
        val output=d.pins.first {it.connectable && PinCapability.DIGITAL_OUTPUT in it.capabilities && it.signal !in reserved}.name
        val parts=mutableListOf<PlacedComponent>();val wires=mutableListOf<Wire>()
        fun p(k:Kind,id:String,params:Map<String,Double> = emptyMap())=PlacedComponent(id=id,kind=k,reference=id,
            x=100f+parts.size%7*160f,y=100f+parts.size/7*160f,
            parameters=ComponentRegistry.definitions.getValue(k).parameters.associate {it.key to it.default}+params).also {parts+=it}
        fun w(a:PlacedComponent,i:Int,b:PlacedComponent,j:Int){wires+=Wire(id="W${wires.size}",start=TerminalRef(a.id,i),end=TerminalRef(b.id,j))}
        val b=p(kind,"B1",mapOf("usbPower" to 1.0));val g=p(Kind.GROUND,"G1");val logic=p(Kind.SOURCE,"VPOWER",mapOf("voltage" to d.logicVoltage))
        w(b,d.pins.indexOfFirst {it.type==PinType.GROUND},g,0);w(logic,1,g,0)
        repeat(10){i ->
            val s=p(Kind.INA219_POWER,"S$i",mapOf("address" to (64+i).toDouble()))
            val input=p(Kind.SOURCE,"INPUT$i",mapOf("voltage" to if(i<5)18.0 else 12.0))
            val shunt=p(Kind.RESISTOR,"SHUNT$i",mapOf("resistance" to .1));val load=p(Kind.RESISTOR,"LOAD$i",mapOf("resistance" to 100.0,"rating" to 10.0))
            w(logic,0,s,0);w(g,0,s,1);w(b,d.index(bus.first),s,2);w(b,d.index(bus.second),s,3)
            w(input,1,g,0);w(input,0,shunt,0);w(shunt,1,load,0);w(load,1,g,0)
            w(input,0,s,4);w(load,0,s,5)
            val cap=p(Kind.CAPACITOR,"C$i",mapOf("capacitance" to 100e-9));w(logic,0,cap,0);w(g,0,cap,1)
        }
        listOf(bus.first,bus.second).forEachIndexed {i,pin->val r=p(Kind.RESISTOR,"RPU$i",mapOf("resistance" to 4700.0));w(b,d.index(d.supplyPins.first()),r,0);w(r,1,b,d.index(pin))}
        val led=p(Kind.LED,"ALARM");val limit=p(Kind.RESISTOR,"RLIMIT",mapOf("resistance" to 1000.0));w(b,d.index(output),limit,0);w(limit,1,led,0);w(led,1,g,0)
        val terminal=p(Kind.SERIAL_TERMINAL,"TELEMETRY",mapOf("baud" to 115200.0));w(b,d.index(uart.first),terminal,1);w(g,0,terminal,2)
        val source=buildString {
            append("// Five PV load channels and five battery discharge channels, 0.1 ohm external shunts.\n// Charge bookkeeping assumes known initial 100 mAh and 200 mAh capacity; no voltage-derived SOC or battery chemistry.\n")
            append("void setup(){Wire.begin();$port.begin(115200);pinMode($output,OUTPUT);int cycles=0;int faults=0;int alarm=0;int excursions=0;double last=0;")
            repeat(10){i->append("double amps$i=0;double volts$i=0;double watts$i=0;double charge$i=100;double soc$i=50;Wire.beginTransmission(${64+i});Wire.write(5);Wire.write(16);Wire.write(0);Wire.endTransmission();")}
            append("delay(2);last=micros();}\nvoid loop(){faults=0;excursions=0;double now=micros();double dt=(now-last)/1000000.0;last=now;")
            repeat(10){i ->
                listOf(2 to "bus",4 to "current",3 to "power",5 to "cal").forEach {(reg,name)->append("Wire.beginTransmission(${64+i});Wire.write($reg);int ack$name$i=Wire.endTransmission(false);int n$name$i=Wire.requestFrom(${64+i},2);int hi$name$i=Wire.read();int lo$name$i=Wire.read();int $name$i=hi$name$i*256+lo$name$i;")}
                append("if(ackbus$i==0 && ackcurrent$i==0 && ackpower$i==0 && ackcal$i==0 && ncal$i==2 && cal$i==4096 && nbus$i==2 && ncurrent$i==2 && npower$i==2 && (bus$i&1)==0){if(current$i>=32768){current$i=current$i-65536;}amps$i=current$i*.0001;volts$i=((bus$i&65528)/8)*.004;watts$i=power$i*.002;charge$i=charge$i-amps$i*dt/3.6;soc$i=charge$i/2;if(amps$i>.3 || volts$i<10 || volts$i>23){excursions=excursions+1;}}else{faults=faults+1;}")
            }
            append("alarm=0;if(faults>0 || excursions>0){alarm=1;}digitalWrite($output,alarm);$port.println(faults);cycles=cycles+1;delay(100);}")
        }
        return Circuit("Solar / battery shunt monitoring / ${d.product}",parts,wires,firmware=listOf(FirmwareAttachment(b.id,FirmwareLanguage.ARDUINO_SUBSET,source,true)))
    }
}
