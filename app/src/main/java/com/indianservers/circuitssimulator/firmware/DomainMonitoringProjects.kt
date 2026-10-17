package com.indianservers.circuitssimulator.firmware
import com.indianservers.circuitssimulator.domain.*

/** Actual polling circuits and bounded firmware algorithms, with illustrative alarm policies. */
object DomainMonitoringProjects {
    val numbers=listOf(7,9,12,19,20)
    fun circuit(kind:Kind,number:Int):Circuit {
        require(number in numbers)
        val d=BoardRegistry.boards.getValue(kind);val bus=requireNotNull(d.hardware.i2cPins)
        val uart=d.hardware.uartPins.first();val port=d.hardware.uartNames.firstOrNull() ?: "Serial"
        val reserved=(listOf(bus.first,bus.second,uart.first,uart.second)+d.hardware.bootLevels.keys).mapNotNull {d.pin(it)?.signal}.toSet()
        val adc=d.pins.first { it.connectable && PinCapability.ANALOG_INPUT in it.capabilities && it.signal !in reserved }.name
        val adcToken=if(adc=="VP" || adc=="VN")requireNotNull(d.pin(adc)?.gpioNumber).toString() else adc
        val output=d.pins.first {it.connectable && PinCapability.DIGITAL_OUTPUT in it.capabilities && it.signal !in reserved && it.signal!=d.pin(adc)?.signal}.name
        val parts=mutableListOf<PlacedComponent>();val wires=mutableListOf<Wire>()
        fun p(k:Kind,id:String,params:Map<String,Double> = emptyMap())=PlacedComponent(id=id,kind=k,reference=id,
            x=100f+parts.size%6*180f,y=100f+parts.size/6*180f,
            parameters=ComponentRegistry.definitions.getValue(k).parameters.associate {it.key to it.default}+params).also {parts+=it}
        fun w(a:PlacedComponent,i:Int,b:PlacedComponent,j:Int){wires+=Wire(id="W${wires.size}",start=TerminalRef(a.id,i),end=TerminalRef(b.id,j))}
        val board=p(kind,"B1",mapOf("usbPower" to 1.0));val g=p(Kind.GROUND,"G1")
        val voltage=if(d.logicVoltage>4.0)3.5 else 3.3
        val rail=p(Kind.SOURCE,"VDOMAIN",mapOf("voltage" to voltage));val five=p(Kind.SOURCE,"V5",mapOf("voltage" to 5.1))
        w(board,d.pins.indexOfFirst {it.type==PinType.GROUND},g,0);w(rail,1,g,0);w(five,1,g,0)
        val pullRail=p(Kind.SOURCE,"VI2C",mapOf("voltage" to voltage));w(pullRail,1,g,0)
        fun sensor(k:Kind,id:String,params:Map<String,Double> = emptyMap()):PlacedComponent {
            val s=p(k,id,params);w(rail,0,s,0);w(g,0,s,1);w(board,d.index(bus.first),s,2);w(board,d.index(bus.second),s,3)
            val cap=p(Kind.CAPACITOR,"C$id",mapOf("capacitance" to 100e-9));w(rail,0,cap,0);w(g,0,cap,1);return s
        }
        repeat(8){i->sensor(Kind.I2C_TEMP_SENSOR,"T$i",mapOf("address" to (72+i).toDouble(),"useEnvironment" to 0.0,"pullupsEnabled" to 0.0,"temperatureC" to (25.0+i*.5)))}
        sensor(Kind.ADXL345_ACCEL,"ACC",mapOf("accelXG" to 0.0,"accelYG" to 0.0,"accelZG" to 1.0,"vibrationAmplitudeG" to if(number==19).03 else 0.0))
        val gas=number==7 || number==9
        if(gas)sensor(Kind.SCD41_CO2,"GAS") else sensor(Kind.LSM6DSOX_IMU,"IMU")
        listOf(bus.first,bus.second).forEachIndexed {i,pin->val pull=p(Kind.RESISTOR,"RPU$i",mapOf("resistance" to 1200.0));w(pullRail,0,pull,0);w(pull,1,board,d.index(pin))}
        if(gas || number==20) {
            val s=p(if(gas)Kind.ZE07_CO else Kind.MPX5010_PRESSURE,"ANALOG")
            // Both simulated analog carriers are V+, VOUT, GND; 1:1 divider protects every board ADC.
            w(five,0,s,0);w(g,0,s,2)
            val top=p(Kind.RESISTOR,"RDIV0",mapOf("resistance" to 10000.0));val bottom=p(Kind.RESISTOR,"RDIV1",mapOf("resistance" to 10000.0))
            w(s,1,top,0);w(top,1,bottom,0);w(bottom,1,g,0);w(top,1,board,d.index(adc))
        }
        val led=p(Kind.LED,"ALARM");val limit=p(Kind.RESISTOR,"RLIMIT",mapOf("resistance" to 1000.0))
        w(board,d.index(output),limit,0);w(limit,1,led,0);w(led,1,g,0)
        val terminal=p(Kind.SERIAL_TERMINAL,"TELEMETRY",mapOf("baud" to 115200.0));w(board,d.index(uart.first),terminal,1);w(g,0,terminal,2)
        val title=when(number){7->"Air quality CO and CO2 network";9->"Multi-zone heat and CO detection";
            12->"Robotics sensor fusion and 1D kinematics";19->"Predictive maintenance vibration monitoring";
            20->"Seismic and hydrostatic flood alert";else->error("Unsupported domain project")}
        val source=buildString {
            append("// $title\n// Raw sensor protocols. Illustrative application policy; no native CPU or vendor library execution.\n")
            append("void setup(){Wire.begin();$port.begin(115200);pinMode($output,OUTPUT);int cycles=0;int faults=0;int alarm=0;int excursions=0;double roll=0;double velocity=0;double position=0;double energy=0;double rms=0;double previousRms=0;double trend=0;double timeToThreshold=0;double last=0;double co=0;double depth=0;int co2=0;")
            repeat(8){append("double t$it=0;")}
            append("Wire.beginTransmission(83);Wire.write(49);Wire.write(8);Wire.endTransmission();Wire.beginTransmission(83);Wire.write(45);Wire.write(8);Wire.endTransmission();")
            if(gas)append("Wire.beginTransmission(98);Wire.write(33);Wire.write(177);Wire.endTransmission();delay(5000);")
            else append("Wire.beginTransmission(106);Wire.write(16);Wire.write(64);Wire.write(64);Wire.endTransmission();")
            append("last=micros();}\nvoid loop(){faults=0;excursions=0;")
            repeat(8){i->append("Wire.beginTransmission(${72+i});Wire.write(0);int ack$i=Wire.endTransmission(false);int n$i=Wire.requestFrom(${72+i},2);int hi$i=Wire.read();int lo$i=Wire.read();if(ack$i==0 && n$i==2){int raw$i=hi$i*256+lo$i;if(raw$i>=32768){raw$i=raw$i-65536;}t$i=raw$i/256.0;if(t$i<0 || t$i>${if(number==9)55 else 70}){excursions=excursions+1;}}else{faults=faults+1;}")}
            fun axes(address:Int,register:Int,prefix:String,scale:Double) {
                append("Wire.beginTransmission($address);Wire.write($register);int ack$prefix=Wire.endTransmission(false);int n$prefix=Wire.requestFrom($address,6);")
                listOf("x","y","z").forEach {a->append("int lo$prefix$a=Wire.read();int hi$prefix$a=Wire.read();int raw$prefix$a=hi$prefix$a*256+lo$prefix$a;if(raw$prefix$a>=32768){raw$prefix$a=raw$prefix$a-65536;}double $prefix$a=raw$prefix$a*$scale;")}
                if(prefix=="a")append("if(ack$prefix!=0 || n$prefix!=6){faults=faults+1;}")
            }
            axes(83,50,"a",.0039)
            if(gas) {
                append("Wire.beginTransmission(98);Wire.write(236);Wire.write(5);int ackGas=Wire.endTransmission();delay(1);int nGas=Wire.requestFrom(98,9);")
                repeat(9){append("int g$it=Wire.read();")}
                // Check all three independent CRC words in real firmware.
                repeat(3){i->append("int crc$i=255;crc$i=crc$i^g${i*3};for(int b$i=0;b$i<8;b$i=b$i+1){if((crc$i&128)!=0){crc$i=(crc$i*2)^49;}else{crc$i=crc$i*2;}crc$i=crc$i&255;}crc$i=crc$i^g${i*3+1};for(int c$i=0;c$i<8;c$i=c$i+1){if((crc$i&128)!=0){crc$i=(crc$i*2)^49;}else{crc$i=crc$i*2;}crc$i=crc$i&255;}")}
                append("if(ackGas==0 && nGas==9 && crc0==g2 && crc1==g5 && crc2==g8){co2=g0*256+g1;}else{faults=faults+1;}")
                append("co=((analogRead($adcToken)+.5)*${d.adcReferenceVoltage}*2.0/${1 shl d.adcBits}-.4)/.0032;")
                append("if(co>${if(number==9)30 else 15} || co2>${if(number==9)3000 else 1500} || co< -5){excursions=excursions+1;}")
            } else {
                axes(106,34,"g",.00875);axes(106,40,"i",.000061)
                append("if(ackg!=0 || ng!=6 || acki!=0 || ni!=6){faults=faults+1;}")
                append("double now=micros();double dt=(now-last)/1000000.0;last=now;")
                append("if(acka==0 && na==6 && ackg==0 && ng==6 && acki==0 && ni==6){")
                when(number) {
                    12 -> append("roll=.98*(roll+gx*dt)+.02*atan2(ay,az)*57.2957795;velocity=velocity+ax*9.80665*dt;position=position+velocity*dt;if(abs(roll)>10 || abs(position)>1){excursions=excursions+1;}")
                    19 -> append("energy=.8*energy+.2*(ax*ax+ix*ix);rms=sqrt(energy);trend=(rms-previousRms)/dt;previousRms=rms;timeToThreshold=0;if(trend>0){timeToThreshold=(.2-rms)/trend;}if(rms>.2 || (cycles>10 && rms>.1 && timeToThreshold>0 && timeToThreshold<2)){excursions=excursions+1;}")
                    20 -> append("depth=((analogRead($adcToken)+.5)*${d.adcReferenceVoltage}*2.0/${1 shl d.adcBits}/5.1-.04)/.09/9.80665;if(abs(ax)>.3 || abs(ix)>.3 || depth>.5 || depth< -.02){excursions=excursions+1;}")
                }
                append("}")
            }
            append("alarm=0;if(faults>0 || excursions>0){alarm=1;}digitalWrite($output,alarm);$port.println(faults);cycles=cycles+1;delay(${if(gas)5000 else 100});}")
        }
        return Circuit("$title / ${d.product}",parts,wires,firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,source,true)))
    }
}
