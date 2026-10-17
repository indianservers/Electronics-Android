package com.indianservers.circuitssimulator.firmware
import com.indianservers.circuitssimulator.domain.*
/** A real second MCU reads ten sensors and sends their diagnostics through two wired CAN controllers. */
object VehicleProjects {
    fun circuit(kind:Kind):Circuit {
        val d=BoardRegistry.boards.getValue(kind);val ecu=BoardRegistry.boards.getValue(Kind.ARDUINO_UNO)
        val spiSignals=d.hardware.spiPins.take(3).mapNotNull {d.pin(it)?.signal}.toSet()
        val uartIndex=d.hardware.uartPins.indexOfFirst {pair->listOf(pair.first,pair.second).filter {it.isNotBlank()}.all {d.pin(it)?.signal !in spiSignals}}
        require(uartIndex>=0){"${d.product}: no UART route independent of SPI"}
        val uart=d.hardware.uartPins[uartIndex]
        val port=d.hardware.uartNames.getOrNull(uartIndex) ?: if(uartIndex==0)"Serial" else "Serial$uartIndex"
        val reserved=(d.hardware.spiPins+listOf(uart.first,uart.second)+d.hardware.bootLevels.keys).mapNotNull {d.pin(it)?.signal}.toSet()
        val pins=d.pins.filter {it.connectable && PinCapability.DIGITAL_OUTPUT in it.capabilities && it.signal !in reserved}.distinctBy {it.signal}.take(2)
        val cs=pins[0].name;val output=pins[1].name
        val parts=mutableListOf<PlacedComponent>();val wires=mutableListOf<Wire>()
        fun p(k:Kind,id:String,params:Map<String,Double> = emptyMap())=PlacedComponent(id=id,kind=k,reference=id,
            x=100f+parts.size%7*180f,y=100f+parts.size/7*180f,
            parameters=ComponentRegistry.definitions.getValue(k).parameters.associate {it.key to it.default}+params).also {parts+=it}
        fun w(a:PlacedComponent,i:Int,b:PlacedComponent,j:Int){wires+=Wire(id="W${wires.size}",start=TerminalRef(a.id,i),end=TerminalRef(b.id,j))}
        val dashboard=p(kind,"B1",mapOf("usbPower" to 1.0));val engine=p(Kind.ARDUINO_UNO,"ECU",mapOf("usbPower" to 1.0))
        val g=p(Kind.GROUND,"G1");val five=p(Kind.SOURCE,"V5",mapOf("voltage" to 5.1))
        w(dashboard,d.pins.indexOfFirst {it.type==PinType.GROUND},g,0);w(engine,ecu.pins.indexOfFirst {it.type==PinType.GROUND},g,0);w(five,1,g,0)
        fun controller(board:PlacedComponent,def:BoardDefinition,id:String,select:String):PlacedComponent {
            val c=p(Kind.MCP2515_CAN,id)
            w(board,def.index(def.supplyPins.first()),c,0);w(g,0,c,1);w(five,0,c,8)
            def.hardware.spiPins.take(3).forEachIndexed {i,pin->w(board,def.index(pin),c,2+i)}
            w(board,def.index(select),c,5)
            return c
        }
        val a=controller(dashboard,d,"CAN_DASH",cs);val b=controller(engine,ecu,"CAN_ECU","D10")
        w(a,6,b,6);w(a,7,b,7)
        repeat(2){i->val term=p(Kind.RESISTOR,"TERM$i",mapOf("resistance" to 120.0));w(if(i==0)a else b,6,term,0);w(if(i==0)a else b,7,term,1)}
        val bus=ecu.hardware.i2cPins!!
        repeat(8){i->
            val s=p(Kind.I2C_TEMP_SENSOR,"T$i",mapOf("address" to (72+i).toDouble(),"useEnvironment" to 0.0,"pullupsEnabled" to 0.0,"temperatureC" to (30+i*2.0)))
            w(five,0,s,0);w(g,0,s,1);w(engine,ecu.index(bus.first),s,2);w(engine,ecu.index(bus.second),s,3)
        }
        listOf(bus.first,bus.second).forEachIndexed {i,pin->val pull=p(Kind.RESISTOR,"RPU$i",mapOf("resistance" to 4700.0));w(engine,ecu.index(ecu.supplyPins.first()),pull,0);w(pull,1,engine,ecu.index(pin))}
        listOf(Kind.ZE07_CO to "A0",Kind.MPX5010_PRESSURE to "A1").forEachIndexed {i,(k,pin)->
            val s=p(k,if(i==0)"CO" else "PRESSURE");w(five,0,s,0);w(g,0,s,2)
            val top=p(Kind.RESISTOR,"DIV${i}A",mapOf("resistance" to 10000.0));val bottom=p(Kind.RESISTOR,"DIV${i}B",mapOf("resistance" to 10000.0))
            w(s,1,top,0);w(top,1,bottom,0);w(bottom,1,g,0);w(top,1,engine,ecu.index(pin))
        }
        val led=p(Kind.LED,"ALARM");val limit=p(Kind.RESISTOR,"RLIMIT",mapOf("resistance" to 1000.0));w(dashboard,d.index(output),limit,0);w(limit,1,led,0);w(led,1,g,0)
        val terminal=p(Kind.SERIAL_TERMINAL,"TELEMETRY",mapOf("baud" to 115200.0));w(dashboard,d.index(uart.first),terminal,1);w(g,0,terminal,2)
        fun write(reg:Int,value:Int,select:String)="digitalWrite($select,LOW);SPI.transfer(2);SPI.transfer($reg);SPI.transfer($value);digitalWrite($select,HIGH);"
        fun init(select:String)=buildString {
            append("pinMode($select,OUTPUT);digitalWrite($select,HIGH);SPI.begin();SPI.setClockDivider(32);digitalWrite($select,LOW);SPI.transfer(192);digitalWrite($select,HIGH);")
            append(write(0x28,5,select));append(write(0x29,0xB1,select));append(write(0x2A,0,select));append(write(0x60,0x60,select));append(write(0x70,0x60,select));append(write(0x0F,8,select))
        }
        val engineSource=buildString {
            append("// ECU reads eight independent thermal probes, CO and pressure. Classical CAN ID 0x321, four diagnostic bytes.\nvoid setup(){Wire.begin();")
            append(init("D10"));append("int cycles=0;int faults=0;int excursions=0;double co=0;double pressure=0;");repeat(8){append("double t$it=0;")};append("}void loop(){faults=0;excursions=0;")
            repeat(8){i->append("Wire.beginTransmission(${72+i});Wire.write(0);int ack$i=Wire.endTransmission(false);int n$i=Wire.requestFrom(${72+i},2);int hi$i=Wire.read();int lo$i=Wire.read();if(ack$i==0 && n$i==2){int raw$i=hi$i*256+lo$i;if(raw$i>=32768){raw$i=raw$i-65536;}t$i=raw$i/256.0;if(t$i<0 || t$i>85){excursions=excursions+1;}}else{faults=faults+1;}")}
            append("co=((analogRead(A0)+.5)*5.0*2/1024-.4)/.0032;pressure=((analogRead(A1)+.5)*5.0*2/1024/5.1-.04)/.09;if(co>30 || co< -5 || pressure>7 || pressure< -.1){excursions=excursions+1;}")
            append("digitalWrite(D10,LOW);SPI.transfer(2);SPI.transfer(49);SPI.transfer(100);SPI.transfer(32);SPI.transfer(0);SPI.transfer(0);SPI.transfer(4);SPI.transfer(faults);SPI.transfer(excursions);SPI.transfer(co/2);SPI.transfer(pressure*10);digitalWrite(D10,HIGH);")
            append("digitalWrite(D10,LOW);SPI.transfer(129);digitalWrite(D10,HIGH);cycles=cycles+1;delay(100);}")
        }
        val dashSource=buildString {
            append("// Dashboard reads the remote ECU frame, and fails safe after 200 ms without a frame.\nvoid setup(){")
            append(init(cs));append("$port.begin(115200);pinMode($output,OUTPUT);int cycles=0;int received=0;int faults=0;int alarm=0;int remoteFaults=0;int remoteExcursions=0;double lastSeen=0;}void loop(){")
            append("digitalWrite($cs,LOW);SPI.transfer(3);SPI.transfer(44);int flags=SPI.transfer(0);digitalWrite($cs,HIGH);")
            append("if((flags&1)!=0){digitalWrite($cs,LOW);SPI.transfer(3);SPI.transfer(97);int idHi=SPI.transfer(0);int idLo=SPI.transfer(0);SPI.transfer(0);SPI.transfer(0);int dlc=SPI.transfer(0);int f=SPI.transfer(0);int e=SPI.transfer(0);int co=SPI.transfer(0);int pressure=SPI.transfer(0);digitalWrite($cs,HIGH);")
            append("if(idHi*8+idLo/32==801 && dlc==4){remoteFaults=f;remoteExcursions=e;lastSeen=millis();received=received+1;}digitalWrite($cs,LOW);SPI.transfer(5);SPI.transfer(44);SPI.transfer(1);SPI.transfer(0);digitalWrite($cs,HIGH);}")
            append("faults=remoteFaults;if(millis()-lastSeen>200){faults=faults+1;}alarm=0;if(faults>0 || remoteExcursions>0){alarm=1;}digitalWrite($output,alarm);$port.println(faults);cycles=cycles+1;delay(10);}")
        }
        return Circuit("Vehicle CAN diagnostic monitoring / ${d.product}",parts,wires,firmware=listOf(
            FirmwareAttachment(dashboard.id,FirmwareLanguage.ARDUINO_SUBSET,dashSource,true),FirmwareAttachment(engine.id,FirmwareLanguage.ARDUINO_SUBSET,engineSource,true)))
    }
}
