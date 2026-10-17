package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*

/** Three different process-monitoring circuits; thresholds are illustrative user policies, not safety limits. */
object ChemistryProjects {
    data class Probe(val kind:Kind,val low:Double,val high:Double) {
        val profile get()=ChemistrySensorParts.profiles.getValue(kind)
        val normal get()=(low+high)/2
    }
    val numbers=listOf(8,16,18)
    fun probes(number:Int):List<Probe> = when(number) {
        8 -> List(4){Probe(Kind.EZO_PH,6.5,8.5)}+List(4){Probe(Kind.EZO_EC,500.0,1500.0)}+List(2){Probe(Kind.EZO_DO,5.0,10.0)}
        16 -> List(5){Probe(Kind.EZO_PH,4.0,6.0)}+List(3){Probe(Kind.EZO_EC,2000.0,4000.0)}+List(2){Probe(Kind.EZO_DO,2.0,6.0)}
        18 -> List(2){Probe(Kind.EZO_PH,7.0,8.5)}+List(2){Probe(Kind.EZO_EC,500.0,2000.0)}+List(6){Probe(Kind.EZO_DO,5.0,12.0)}
        else -> error("No chemistry project $number")
    }
    fun circuit(kind:Kind,number:Int):Circuit {
        val d=BoardRegistry.boards.getValue(kind)
        val bus=requireNotNull(d.hardware.i2cPins);val uart=d.hardware.uartPins.first()
        val port=d.hardware.uartNames.firstOrNull() ?: "Serial"
        val reserved=(listOf(bus.first,bus.second,uart.first,uart.second)+d.hardware.bootLevels.keys).mapNotNull { d.pin(it)?.signal }.toSet()
        val output=d.pins.first { it.connectable && PinCapability.DIGITAL_OUTPUT in it.capabilities && it.signal !in reserved }.name
        val parts=mutableListOf<PlacedComponent>();val wires=mutableListOf<Wire>()
        fun p(k:Kind,id:String,parameters:Map<String,Double> = emptyMap()):PlacedComponent = PlacedComponent(
            id=id,kind=k,reference=id,x=100f+parts.size%5*180f,y=100f+parts.size/5*180f,
            parameters=ComponentRegistry.definitions.getValue(k).parameters.associate { it.key to it.default }+parameters).also { parts+=it }
        fun w(a:PlacedComponent,i:Int,b:PlacedComponent,j:Int) { wires+=Wire(id="W${wires.size}",start=TerminalRef(a.id,i),end=TerminalRef(b.id,j)) }
        val board=p(kind,"B1",mapOf("usbPower" to 1.0));val ground=p(Kind.GROUND,"G1")
        // Separate sensor supply avoids loading the board regulator; logic pull-ups stay on the board rail.
        val supply=p(Kind.SOURCE,"VCHEM",mapOf("voltage" to 5.0))
        w(board,d.pins.indexOfFirst { it.type==PinType.GROUND },ground,0);w(supply,1,ground,0)
        probes(number).forEachIndexed { i,probe ->
            val sensor=p(probe.kind,"S$i",mapOf("address" to (32+i).toDouble(),probe.profile.key to probe.normal))
            w(supply,0,sensor,0);w(ground,0,sensor,1);w(board,d.index(bus.first),sensor,2);w(board,d.index(bus.second),sensor,3)
            val cap=p(Kind.CAPACITOR,"C$i",mapOf("capacitance" to 100e-9));w(supply,0,cap,0);w(ground,0,cap,1)
        }
        listOf(bus.first,bus.second).forEachIndexed { i,pin ->
            val pull=p(Kind.RESISTOR,"RPU$i",mapOf("resistance" to 4700.0));w(board,d.index(d.supplyPins.first()),pull,0);w(pull,1,board,d.index(pin))
        }
        val led=p(Kind.LED,"ALARM");val limit=p(Kind.RESISTOR,"RLIMIT",mapOf("resistance" to 1000.0))
        w(board,d.index(output),limit,0);w(limit,1,led,0);w(led,1,ground,0)
        val terminal=p(Kind.SERIAL_TERMINAL,"TELEMETRY",mapOf("baud" to 115200.0));w(board,d.index(uart.first),terminal,1);w(ground,0,terminal,2)
        val source=buildString {
            append("// EZO process monitoring; ASCII protocol, ten independent calibrated probes.\n")
            append("void setup(){Wire.begin();$port.begin(115200);pinMode($output,OUTPUT);int cycles=0;int faults=0;int excursions=0;int alarm=0;")
            repeat(10){append("double reading$it=0;")};append("}\nvoid loop(){faults=0;excursions=0;")
            repeat(10){i -> append("Wire.beginTransmission(${32+i});Wire.write(82);int ack$i=Wire.endTransmission();")}
            append("delay(900);") // All probes convert concurrently; pH needs the longest documented delay.
            probes(number).forEachIndexed { i,probe ->
                append("int count$i=Wire.requestFrom(${32+i},16);int status$i=Wire.read();double value$i=0;double scale$i=1;int dot$i=0;int digits$i=0;int bad$i=0;int end$i=0;")
                append("for(int j$i=0;j$i<15;j$i=j$i+1){int ch$i=Wire.read();if(end$i==0){if(ch$i==0){end$i=1;}else{if(ch$i==46 && dot$i==0){dot$i=1;}else{if(ch$i>=48 && ch$i<=57){value$i=value$i*10+ch$i-48;digits$i=digits$i+1;if(dot$i==1){scale$i=scale$i*10;}}else{bad$i=1;}}}}}")
                append("if(ack$i==0 && count$i==16 && status$i==1 && digits$i>0 && bad$i==0 && end$i==1){reading$i=value$i/scale$i;if(reading$i<${probe.low} || reading$i>${probe.high}){excursions=excursions+1;}}else{faults=faults+1;}")
            }
            append("alarm=0;if(faults>0 || excursions>0){alarm=1;}digitalWrite($output,alarm);$port.println(faults);cycles=cycles+1;delay(100);")
            append("}")
        }
        return Circuit("${when(number){8->"Water treatment";16->"Chemical process";else->"Aquaculture"}} / ${d.product}",parts,wires,
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,source,true)))
    }
}
