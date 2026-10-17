package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*

/** Phase 2 numbering. Only explicitly implemented monitored functions are claimed. */
object PhaseTwoProjects {
    val titles=listOf("Industrial environmental monitoring","Smart agriculture and irrigation","Smart greenhouse automation",
        "Advanced weather station","Industrial motor condition monitoring","Home security and intrusion detection",
        "Smart building automation","Medical environmental monitoring","Water quality monitoring","Smart electrical energy monitoring",
        "Battery monitoring and protection","Industrial gas safety monitoring","Smart parking management","Factory production monitoring",
        "Fire detection and alarm system","Smart streetlight monitoring","Vehicle diagnostics and monitoring","Cold-chain logistics monitoring",
        "Flood and disaster early warning","Advanced robotics sensor integration")
    val newNumbers=setOf(1,3,8,16,18)
    val occupancyNumbers=mapOf(6 to 17,7 to 11)
    val priorNumbers=mapOf(5 to 19,9 to 8,10 to 10,12 to 7,15 to 9,17 to 13,19 to 20,20 to 12)
    fun supported(number:Int)=number in newNumbers || number in priorNumbers || number in occupancyNumbers || number==13
    fun circuit(kind:Kind,number:Int):Circuit {
        require(supported(number)){"Application has unresolved core sensing/control requirements"}
        if(number==13)return PhaseTwoParkingProjects.circuit(kind)
        if(number in occupancyNumbers)return com.indianservers.circuitssimulator.validation.AdvancedProjects
            .create(kind,occupancyNumbers.getValue(number)).circuit.copy(name="${titles[number-1]} / thermal and generic PIR subsystem")
        if(number !in newNumbers){
            val old=priorNumbers.getValue(number)
            return when(old){8->ChemistryProjects.circuit(kind,old);13->VehicleProjects.circuit(kind);10->PowerMonitoringProjects.circuit(kind);else->DomainMonitoringProjects.circuit(kind,old)}
        }
        val base=DomainMonitoringProjects.circuit(kind,19)
        val climate=number in setOf(3,8)
        val removed=setOf("T7","CT7","IMU","CIMU")+if(climate)setOf("ACC","CACC")else emptySet()
        val parts=base.components.filter {it.id !in removed}.map {p->
            if(p.kind==Kind.I2C_TEMP_SENSOR)p.copy(parameters=p.parameters+("temperatureC" to if(number==18)4.0+p.id.drop(1).toInt()*.5 else 25.0+p.id.drop(1).toInt()*.5))
            else if(p.id=="ACC")p.copy(parameters=p.parameters+("vibrationAmplitudeG" to 0.0)) else p
        }.toMutableList()
        val wires=base.wires.filter {it.start.componentId !in removed && it.end.componentId !in removed}.toMutableList()
        val def=BoardRegistry.boards.getValue(kind);val bus=def.hardware.i2cPins!!
        fun w(a:String,ai:Int,b:String,bi:Int){wires+=Wire(id="P2W${wires.size}",start=TerminalRef(a,ai),end=TerminalRef(b,bi))}
        repeat(2){i->
            val id="L$i"
            parts+=PlacedComponent(id=id,kind=Kind.BH1750_LIGHT,reference=id,x=120f+i*200,y=1000f,
                parameters=mapOf("address" to if(i==0)35.0 else 92.0,"illuminanceLux" to if(number==18)5.0 else if(number==16)10.0 else 300.0+i*20))
            parts+=PlacedComponent(id="CL$i",kind=Kind.CAPACITOR,reference="CL$i",x=120f+i*200,y=1140f,parameters=mapOf("capacitance" to 100e-9))
            w("VDOMAIN",0,id,0);w("G1",0,id,1);w("B1",def.index(bus.first),id,2);w("B1",def.index(bus.second),id,3)
            w("VDOMAIN",0,"CL$i",0);w("G1",0,"CL$i",1)
        }
        if(climate){
            parts+=PlacedComponent(id="GAS",kind=Kind.SCD41_CO2,reference="GAS",x=580f,y=1000f,
                parameters=mapOf("address" to 98.0,"co2Ppm" to 800.0,"temperatureC" to 25.0,"humidityPercent" to 50.0))
            w("VDOMAIN",0,"GAS",0);w("G1",0,"GAS",1);w("B1",def.index(bus.first),"GAS",2);w("B1",def.index(bus.second),"GAS",3)
            parts+=PlacedComponent(id="CGAS",kind=Kind.CAPACITOR,reference="CGAS",x=580f,y=1140f,parameters=mapOf("capacitance" to 100e-9))
            w("VDOMAIN",0,"CGAS",0);w("G1",0,"CGAS",1)
        }
        val output=wires.first {it.start.componentId=="B1" && it.end==TerminalRef("RLIMIT",0)}.start.index
        val outputToken=def.pins[output].name
        val port=def.hardware.uartNames.firstOrNull() ?: "Serial"
        val source=buildString {
            append("// ${titles[number-1]}: seven LM75B channels, two BH1750FVI and one ${if(climate)"SCD41" else "ADXL345"}.\n")
            append("// Nominal monitored subsystem; see spec.json for absent application functions.\nvoid setup(){Wire.begin();$port.begin(115200);pinMode($outputToken,OUTPUT);int cycles=0;int faults=0;int alarm=0;double ax=0;")
            repeat(7){append("double t$it=0;")};repeat(2){append("double lux$it=0;int lightInit$it=0;")}
            if(climate)append("double co2=0;double airTemperature=0;double humidity=0;Wire.beginTransmission(98);Wire.write(33);Wire.write(177);Wire.endTransmission();")
            else append("Wire.beginTransmission(83);Wire.write(49);Wire.write(8);Wire.endTransmission();Wire.beginTransmission(83);Wire.write(45);Wire.write(8);Wire.endTransmission();")
            listOf(35,92).forEachIndexed {i,a->append("Wire.beginTransmission($a);Wire.write(16);lightInit$i=Wire.endTransmission();")}
            if(climate)append("delay(5000);")
            append("delay(180);}\nvoid loop(){faults=0;")
            repeat(7){i->append("Wire.beginTransmission(${72+i});Wire.write(0);int a$i=Wire.endTransmission(false);int n$i=Wire.requestFrom(${72+i},2);int h$i=Wire.read();int l$i=Wire.read();int r$i=h$i*256+l$i;if(r$i>=32768){r$i=r$i-65536;}if(a$i==0 && n$i==2){t$i=r$i/256.0;}else{faults=faults+1;}")}
            listOf(35,92).forEachIndexed {i,a->append("int nl$i=Wire.requestFrom($a,2);int hl$i=Wire.read();int ll$i=Wire.read();if(nl$i==2 && lightInit$i==0){lux$i=(hl$i*256+ll$i)/1.2;}else{faults=faults+1;}")}
            if(climate){
                append("Wire.beginTransmission(98);Wire.write(236);Wire.write(5);int ag=Wire.endTransmission();delay(1);int ng=Wire.requestFrom(98,9);")
                repeat(9){append("int g$it=Wire.read();")}
                repeat(3){i->append("int crc$i=255;crc$i=crc$i^g${i*3};for(int b$i=0;b$i<8;b$i=b$i+1){if((crc$i&128)!=0){crc$i=(crc$i*2)^49;}else{crc$i=crc$i*2;}crc$i=crc$i&255;}crc$i=crc$i^g${i*3+1};for(int c$i=0;c$i<8;c$i=c$i+1){if((crc$i&128)!=0){crc$i=(crc$i*2)^49;}else{crc$i=crc$i*2;}crc$i=crc$i&255;}")}
                append("if(ag==0 && ng==9 && crc0==g2 && crc1==g5 && crc2==g8){co2=g0*256+g1;airTemperature=-45+175.0*(g3*256+g4)/65535;humidity=100.0*(g6*256+g7)/65535;}else{faults=faults+1;}")
            } else append("Wire.beginTransmission(83);Wire.write(50);int aa=Wire.endTransmission(false);int na=Wire.requestFrom(83,2);int la=Wire.read();int ha=Wire.read();int ra=ha*256+la;if(ra>=32768){ra=ra-65536;}if(aa==0 && na==2){ax=ra*.0039;}else{faults=faults+1;}")
            append("alarm=0;")
            when(number){
                1->{repeat(7){append("if(t$it<0 || t$it>60){alarm=1;}")};append("if(abs(ax)>.5 || lux0>20000 || lux1>20000){alarm=1;}")}
                3->{repeat(7){append("if(t$it<15 || t$it>35){alarm=1;}")};append("if(humidity<35 || humidity>80 || co2>2500 || lux0>50000 || lux1>50000){alarm=1;}")}
                8->{repeat(7){append("if(t$it<20 || t$it>30){alarm=1;}")};append("if(humidity<30 || humidity>65 || co2>1000 || lux0<100 || lux1<100){alarm=1;}")}
                16->{append("if(lux0<50 && lux1<50){alarm=1;}");repeat(7){append("if(t$it>70){alarm=0;}")};append("if(abs(ax)>1){alarm=0;}")}
                18->{repeat(7){append("if(t$it<2 || t$it>8){alarm=1;}")};append("if(lux0>50 || lux1>50 || abs(ax)>.5){alarm=1;}")}
            }
            append("if(faults>0){alarm=1;}digitalWrite($outputToken,alarm);$port.print(cycles);$port.print(\":\");$port.println(faults);cycles=cycles+1;delay(${if(climate)5000 else 200});}")
        }
        return base.copy(name="${titles[number-1]} / ${def.product}",components=parts,wires=wires,
            firmware=listOf(FirmwareAttachment("B1",FirmwareLanguage.ARDUINO_SUBSET,source,true)))
    }
}
