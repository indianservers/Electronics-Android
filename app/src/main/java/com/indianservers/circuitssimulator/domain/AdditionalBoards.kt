package com.indianservers.circuitssimulator.domain

/** Header layouts verified against manufacturer pinouts, schematics and official core variants. */
object AdditionalBoards {
    val kinds=setOf(Kind.ARDUINO_LEONARDO,Kind.ARDUINO_MICRO,Kind.ARDUINO_DUE,
        Kind.ARDUINO_UNO_R4_MINIMA,Kind.RASPBERRY_PICO_2_W,Kind.ESP32_S3_DEVKIT,
        Kind.ESP32_C6_DEVKIT,Kind.ESP32_S2_SAOLA)
    private const val verified="2026-10-12"
    private fun reference(title:String,publisher:String,type:ReferenceType,url:String,revision:String)=
        BoardReference(title,publisher,type,revision,url,applicableRevision=revision,lastVerified=verified)
    private fun capabilities(wifi:Boolean=false,ble:Boolean=false,dac:Boolean=false)=BoardCapabilityMatrix(
        FeatureSupport(true,FeatureSimLevel.FULL,"Finite-impedance GPIO in the supported firmware subset"),
        FeatureSupport(true,FeatureSimLevel.BASIC,"Nominal linear ADC; calibration, noise and conversion timing omitted"),
        FeatureSupport(true,FeatureSimLevel.BASIC,"Virtual-time PWM; hardware timer registers are not emulated"),
        FeatureSupport(true,FeatureSimLevel.BASIC,"Event UART"),
        FeatureSupport(true,FeatureSimLevel.BASIC,"Event I2C with power, mapping and pull-up checks"),
        FeatureSupport(true,FeatureSimLevel.BASIC,"Event SPI"),
        FeatureSupport(wifi,if(wifi) FeatureSimLevel.METADATA else FeatureSimLevel.UNSUPPORTED,"Radio/network stack not simulated"),
        FeatureSupport(ble,if(ble) FeatureSimLevel.METADATA else FeatureSimLevel.UNSUPPORTED,"Radio stack not simulated"),
        FeatureSupport(dac,if(dac) FeatureSimLevel.METADATA else FeatureSimLevel.UNSUPPORTED,"DAC output not simulated"))

    private fun pad(name:String,logic:Double,number:Int?,connector:String,gpio:Int?=null,
        adc:Int?=null,pwm:Boolean=false,i2c:Boolean=false,spi:Boolean=false,
        aliases:Set<String> = emptySet(),signal:String=name,inputOnly:Boolean=false,
        reserved:Boolean=false,functions:Set<String> = emptySet(),notes:String=""):BoardPin {
        val type=when {
            reserved || name.startsWith("NC") -> PinType.RESERVED
            name.startsWith("GND") || name=="AGND" -> PinType.GROUND
            name in setOf("5V","5V0","3V3") || name.startsWith("ICSP_5V") -> PinType.POWER_OUTPUT
            name=="VIN" -> PinType.POWER_INPUT
            name=="RESET" || name=="RST" || name=="ICSP_RESET" -> PinType.RESET
            name=="AREF" || name=="IOREF" -> PinType.REFERENCE
            else -> PinType.GPIO
        }
        val caps=when(type) {
            PinType.RESERVED -> setOf(PinCapability.RESERVED)
            PinType.GROUND -> setOf(PinCapability.GROUND)
            PinType.POWER_OUTPUT -> setOf(PinCapability.POWER_INPUT,PinCapability.POWER_OUTPUT)
            PinType.POWER_INPUT -> setOf(PinCapability.POWER_INPUT)
            PinType.RESET -> setOf(PinCapability.RESET)
            PinType.REFERENCE -> setOf(PinCapability.RESERVED)
            else -> buildSet {
                add(PinCapability.DIGITAL_INPUT)
                if(!inputOnly) add(PinCapability.DIGITAL_OUTPUT)
                if(pwm && !inputOnly) add(PinCapability.PWM)
                if(adc!=null) add(PinCapability.ANALOG_INPUT)
                if(i2c && !inputOnly) add(PinCapability.I2C)
                if(spi && !inputOnly) add(PinCapability.SPI)
            }
        }
        return BoardPin(name,caps,notes,
            minVoltage=if(type==PinType.GPIO) 0.0 else null,
            maxVoltage=if(type==PinType.GPIO) logic else null,
            physicalNumber=number,gpioNumber=gpio,aliases=aliases,type=type,
            signal=if(type==PinType.GROUND) "GND" else signal,adcChannel=adc,
            inputOnly=inputOnly,connectable=type!=PinType.RESERVED,
            physicalLabel=name,functions=functions,connector=connector)
    }
    private fun row(pins:List<BoardPin>,x:Float,start:Float,pitch:Float=14f)=
        pins.mapIndexed { i,p -> p.copy(nx=x,ny=start+i*pitch) }
    private fun icsp(logic:Double,spiNumbers:List<Int>,signals:List<String> = listOf("MISO","SCK","MOSI"))=
        listOf("MISO","ICSP_5V","SCK","MOSI","ICSP_RESET","GND_ICSP").mapIndexed { i,n ->
            val s=when(i) { 0->0;2->1;3->2;else->null }
            pad(n,logic,i+1,"ICSP",gpio=s?.let { spiNumbers[it] },spi=s!=null,
                aliases=when(n) { "MISO"->setOf("CIPO");"MOSI"->setOf("COPI");else->emptySet() },
                signal=when { s!=null->signals[s];i==1->"5V";i==4->"RESET";else->"GND" })
                .copy(nx=-22f+(i%2)*24f,ny=70f+(i/2)*16f)
        }
    private val avrAdc=mapOf("A0" to 7,"A1" to 6,"A2" to 5,"A3" to 4,"A4" to 1,"A5" to 0,
        "D4" to 8,"D6" to 10,"D8" to 11,"D9" to 12,"D10" to 13,"D12" to 9)
    private val avrShared=mapOf("D4" to 6,"D6" to 7,"D8" to 8,"D9" to 9,"D10" to 10,"D12" to 11)
    private fun avrPin(name:String,number:Int,connector:String):BoardPin {
        val d=name.removePrefix("D").toIntOrNull()
        val a=name.removePrefix("A").toIntOrNull()
        val aliases=buildSet {
            if(d!=null) add(d.toString())
            if(a!=null) { add((18+a).toString());add("D${18+a}") }
            avrShared[name]?.let { add("A$it");add((18+it).toString());add("D${18+it}") }
            if(name=="D2") add("SDA")
            if(name=="D3") add("SCL")
            if(name=="D0") add("RX")
            if(name=="D1") add("TX")
            if(name=="D14") { add("MISO");add("CIPO") }
            if(name=="D15") add("SCK")
            if(name=="D16") { add("MOSI");add("COPI") }
            if(name=="D17") add("SS")
        }
        return pad(name,5.0,number,connector,gpio=d ?: a?.let { 18+it },adc=avrAdc[name],
            pwm=d in setOf(3,5,6,9,10,11,13),i2c=d in setOf(2,3),spi=d in 14..17,
            aliases=aliases,notes=avrShared[name]?.let { "Shared analog input A$it; ADC${avrAdc[name]}." }.orEmpty())
    }
    private fun leonardo():BoardDefinition {
        val left=listOf("NC","IOREF","RESET","3V3","5V","GND","GND","VIN")+(0..5).map { "A$it" }
        val right=listOf("SCL","SDA","AREF","GND")+(13 downTo 0).map { "D$it" }
        val pins=row(left.mapIndexed { i,n -> avrPin(n,i+1,if(i<8) "POWER" else "ANALOG") },-120f,-98f)+
            row(right.mapIndexed { i,n -> when(n) {
                "SDA","SCL" -> pad(n,5.0,i+1,"DIGITAL",gpio=if(n=="SDA") 2 else 3,
                    i2c=true,signal=if(n=="SDA") "D2" else "D3")
                else -> avrPin(n,i+1,"DIGITAL")
            } },120f,-119f)+icsp(5.0,listOf(14,15,16))
        return arduino(Kind.ARDUINO_LEONARDO,"Leonardo","ATmega32U4",BoardFamily.ARDUINO_AVR,"A000057",5.0,
            pins.map { if(it.connector=="ICSP") it else it.copy(physicalNumber=null) },
            "Native USB AVR; 12 analog aliases, seven PWM pins; I2C D2/D3 and SPI on ICSP.",10,
            pinoutSku="A000057",width=248f,height=280f)
    }
    private fun micro():BoardDefinition {
        val left=listOf("D13","3V3","AREF")+(0..5).map { "A$it" }+
            listOf("NC1","NC2","5V","RESET","GND","VIN","D14","D15")
        val right=(12 downTo 2).map { "D$it" }+listOf("GND","RESET","D0","D1","D17","D16")
        val pins=row(left.mapIndexed { i,n -> avrPin(n,i+1,"LEFT") },-78f,-112f)+
            row(right.mapIndexed { i,n -> avrPin(n,i+1,"RIGHT") },78f,-112f)+
            icsp(5.0,listOf(14,15,16),listOf("D14","D15","D16"))
        return arduino(Kind.ARDUINO_MICRO,"Micro","ATmega32U4",BoardFamily.ARDUINO_AVR,"A000053",5.0,
            pins.map { if(it.connector=="ICSP") it else it.copy(physicalNumber=null) },
            "Native USB AVR in the official 34-position Micro footprint. NC pads are not GPIO. A6–A11 share digital pads.",10,
            pinoutSku="A000053",width=168f,height=280f)
    }
    private fun arduino(kind:Kind,product:String,mcu:String,family:BoardFamily,sku:String,logic:Double,
        pins:List<BoardPin>,notes:String,adcBits:Int,pinoutSku:String=sku,width:Float,height:Float,
        usb:String="Micro-USB",dac:Boolean=false):BoardDefinition {
        val url="https://docs.arduino.cc/resources/pinouts/$pinoutSku-full-pinout.pdf"
        val core=when(family) {
            BoardFamily.ARDUINO_AVR -> "https://raw.githubusercontent.com/arduino/ArduinoCore-avr/master/variants/leonardo/pins_arduino.h"
            BoardFamily.ARDUINO_SAM -> "https://raw.githubusercontent.com/arduino/ArduinoCore-sam/master/variants/arduino_due_x/variant.h"
            else -> "https://raw.githubusercontent.com/arduino/ArduinoCore-renesas/main/variants/MINIMA/pins_arduino.h"
        }
        return BoardDefinition(kind,"Arduino",product,mcu,logic,pins,setOf(if(logic==5.0) "5V" else "3V3"),url,notes,
            keywords=listOf("Arduino",product,mcu,family.name,"GPIO","PWM","I2C","SPI","UART"),
            family=family,revision=sku,architecture=when(family) {
                BoardFamily.ARDUINO_AVR -> "AVR 8-bit"
                BoardFamily.ARDUINO_SAM -> "ARM Cortex-M3"
                else -> "ARM Cortex-M4"
            },adcBits=adcBits,adcReferenceVoltage=logic,ledBuiltin="D13",supportLevel=BoardSupportLevel.FUNCTIONAL,
            powerDomains=listOf(PowerDomain("USB",5.0,"input"),PowerDomain("VIN",9.0,"input"),
                PowerDomain("5V",5.0,"output"),PowerDomain("3V3",3.3,"output")),
            references=listOf(reference("$product full pinout","Arduino",ReferenceType.PINOUT,url,sku),
                reference("Official Arduino core pin mapping","Arduino",ReferenceType.USER_GUIDE,core,sku))+
                if(family==BoardFamily.ARDUINO_AVR) listOf(reference("ATmega32U4 datasheet","Microchip",ReferenceType.MCU_DATASHEET,
                    "https://ww1.microchip.com/downloads/en/devicedoc/atmel-7766-8-bit-avr-atmega16u4-32u4_datasheet.pdf",sku)) else emptyList(),
            warnings=listOf("Main GPIO/power headers and SPI/ICSP are exposed. USB connectors and debug/test pads are not wire terminals.",
                "Firmware subset and event buses only; CPU instructions, USB HID, ADC calibration and timer register programming are not emulated."),
            capabilities=capabilities(dac=dac),usbConnector=usb,boardWidth=width,boardHeight=height,
            hardware=AdditionalBoardHardware.forKind(kind))
    }
    private fun due(base:BoardDefinition):BoardDefinition {
        val replacements=mapOf("A12" to "DAC0","A13" to "DAC1","A14" to "CANRX","A15" to "CANTX",
            "SDA" to "SDA1","SCL" to "SCL1")
        val pins=base.pins.map { old ->
            val name=replacements[old.name] ?: old.name
            val d=name.removePrefix("D").toIntOrNull()
            val a=name.removePrefix("A").toIntOrNull()
            val gpio=d ?: a?.let { 54+it } ?: when(name) {
                "DAC0"->66;"DAC1"->67;"CANRX"->68;"CANTX"->69;"SDA1"->70;"SCL1"->71;else->null
            }
            pad(name,3.3,null,if(d!=null && d>=22) "DIGITAL22_53" else "HEADER",gpio=gpio,
                adc=a,pwm=d in 2..13,i2c=d in setOf(20,21) || name in setOf("SDA1","SCL1"),
                aliases=buildSet {
                    gpio?.let { add(it.toString()) }
                    if(a!=null) add("D${54+a}")
                    if(name=="D20") add("SDA")
                    if(name=="D21") add("SCL")
                },functions=when(name) {
                    "DAC0","DAC1"->setOf("12-bit DAC (hardware only)")
                    "CANRX","CANTX"->setOf("CAN0 (hardware only)")
                    "SDA1","SCL1"->setOf("Wire1 (hardware only)")
                    else->emptySet()
                }).let { pin -> pin.copy(nx=old.nx,ny=old.ny,capabilities=pin.capabilities+
                    if(name.startsWith("DAC")) setOf(PinCapability.DAC) else emptySet()) }
        }.let { pads ->
            val mainOrder=listOf("SCL1","SDA1","AREF","GND")+(13 downTo 0).map { "D$it" }+(14..21).map { "D$it" }
            pads.map { pin ->
                val at=mainOrder.indexOf(pin.name)
                if(at>=0 && pin.nx==20f) pin.copy(ny=-168f+at*13.5f) else pin
            }
        }+icsp(3.3,listOf(74,76,75)).map { it.copy(connector="SPI") }
        return arduino(Kind.ARDUINO_DUE,"Due","SAM3X8E",BoardFamily.ARDUINO_SAM,"A000062",3.3,pins,
            "54 digital headers, 12 ADC inputs, two DAC pads and CAN pads. SPI is on the dedicated six-pin header, not D50–D52. SDA1/SCL1 is separate from D20/D21.",
            10,pinoutSku="A000056",width=340f,height=460f,usb="2 × Micro-USB",dac=true).let { board ->
                board.copy(references=board.references+reference("Due datasheet","Arduino",ReferenceType.BOARD_DATASHEET,
                    "https://docs.arduino.cc/resources/datasheets/A000062-datasheet.pdf","A000062"))
            }
    }
    private fun r4(base:BoardDefinition):BoardDefinition {
        val pins=base.pins.map { old ->
            val d=old.name.removePrefix("D").toIntOrNull()
            val a=old.name.removePrefix("A").toIntOrNull()
            old.copy(capabilities=old.capabilities-PinCapability.INTERRUPT+
                if(old.name=="A0") setOf(PinCapability.DAC) else emptySet(),
                functions=if(old.name=="A0") setOf("12-bit DAC (hardware only)") else emptySet(),
                physicalNumber=null,connector="HEADER",
                notes=when(old.name) { "A0"->"DAC output not simulated";"D4","D5"->"CAN alternate function (hardware only)";else->"5 V Renesas GPIO" },
                maxCurrentMa=if(d!=null || a!=null) 8.0 else null)
        }+icsp(5.0,listOf(12,13,11),listOf("D12","D13","D11"))
        return arduino(Kind.ARDUINO_UNO_R4_MINIMA,"UNO R4 Minima","Renesas RA4M1",BoardFamily.ARDUINO_RENESAS,
            "ABX00080",5.0,pins,"5 V RA4M1 UNO footprint. ADC hardware 14-bit; analogRead default 10-bit. A0 DAC, op-amp and CAN are hardware only.",
            10,width=248f,height=280f,usb="USB-C",dac=true).let { board ->
                board.copy(references=board.references+reference("UNO R4 Minima datasheet","Arduino",ReferenceType.BOARD_DATASHEET,
                    "https://docs.arduino.cc/resources/datasheets/ABX00080-datasheet.pdf","ABX00080"))
            }
    }

    private fun esp(kind:Kind,family:BoardFamily,product:String,module:String,revision:String,
        left:List<String>,right:List<String>,url:String,rgb:Int,adcBits:Int=12,
        leftConnector:String="J1",reserved:Set<Int> = emptySet(),inputOnly:Set<Int> = emptySet()):BoardDefinition {
        val hardware=AdditionalBoardHardware.forKind(kind)
        fun pins(names:List<String>,connector:String)=names.mapIndexed { i,n ->
            val gpio=when(n) {
                "TX"->if(family==BoardFamily.ESP32_C6) 16 else 43
                "RX"->if(family==BoardFamily.ESP32_C6) 17 else 44
                else->n.removePrefix("IO").toIntOrNull()
            }
            val channel=if(family==BoardFamily.ESP32_C6) gpio?.takeIf { it in 0..6 }
                else gpio?.takeIf { it in 1..20 }?.let { (it-1)%10 }
            val isReserved=gpio in reserved
            val strapped=gpio in when(family) {
                BoardFamily.ESP32_C6 -> setOf(4,5,8,9,15)
                BoardFamily.ESP32_S3 -> setOf(0,3,45,46)
                else -> setOf(0,45,46)
            }
            pad(n,3.3,i+1,connector,gpio=if(isReserved) null else gpio,adc=channel,
                pwm=gpio!=null,i2c=gpio!=null,spi=gpio!=null,reserved=isReserved,inputOnly=gpio in inputOnly,
                signal=if(n=="5V0") "5V" else n,
                aliases=buildSet {
                    gpio?.let { add(it.toString());add("GPIO$it");add("IO$it") }
                    if(channel!=null) add("A${if(family==BoardFamily.ESP32_C6) gpio else gpio!!-1}")
                    if(n=="IO43") add("TX")
                    if(n=="IO44") add("RX")
                },functions=buildSet {
                    if(channel!=null) add("ADC${if(family==BoardFamily.ESP32_C6 || gpio!!<=10) 1 else 2}_CH$channel")
                    if(family!=BoardFamily.ESP32_C6 && gpio in 1..14) add("TOUCH$gpio (hardware only)")
                    if(family==BoardFamily.ESP32_S2 && gpio in setOf(17,18)) add("DAC (hardware only)")
                    if(gpio==rgb) add("Addressable RGB LED (hardware only)")
                    val usb=if(family==BoardFamily.ESP32_C6) listOf(12,13) else listOf(19,20)
                    if(gpio==usb[0]) add("USB D- (hardware only)")
                    if(gpio==usb[1]) add("USB D+ (hardware only)")
                },notes=when {
                    isReserved->"Reserved for the selected module's PSRAM; cannot use as GPIO."
                    gpio in inputOnly->"Input only; no output driver or PWM."
                    else->"3.3 V GPIO, not 5 V tolerant."
                }).let { p -> p.copy(physicalLabel=if(n.startsWith("IO") && family!=BoardFamily.ESP32_S2) n.drop(2) else if(n=="GND") "G" else n,
                    warnings=if(strapped) listOf("Strapping pin; reset level can change chip configuration.") else emptyList()) }
        }
        val count=maxOf(left.size,right.size)
        val start=-(count-1)*7f
        val pads=row(pins(left,leftConnector),-110f,start)+row(pins(right,"J3"),110f,start)
        val chip="https://www.espressif.com/sites/default/files/documentation/${when(family) {
            BoardFamily.ESP32_S3->"esp32-s3";BoardFamily.ESP32_C6->"esp32-c6";else->"esp32-s2"
        }}_datasheet_en.pdf"
        val variant=when(family) { BoardFamily.ESP32_S3->"esp32s3";BoardFamily.ESP32_C6->"esp32c6";else->"esp32s2" }
        return BoardDefinition(kind,"Espressif",product,module,3.3,pads,setOf("3V3"),url,
            "$revision, $module. ${hardware.notes.joinToString(" ")}",
            keywords=listOf(product,module,variant,"IoT","WiFi","GPIO",
                if(family==BoardFamily.ESP32_C6) "RISC-V" else "Xtensa","I2C","SPI"),
            family=family,revision=revision,module=module,
            architecture=if(family==BoardFamily.ESP32_C6) "RISC-V" else "Xtensa LX7",
            adcBits=adcBits,adcReferenceVoltage=if(family==BoardFamily.ESP32_S2) 2.5 else if(family==BoardFamily.ESP32_S3) 3.1 else 3.3,
            ledBuiltin=null,supportLevel=BoardSupportLevel.FUNCTIONAL,
            powerDomains=listOf(PowerDomain("USB",5.0,"input"),PowerDomain(if(family==BoardFamily.ESP32_S2) "5V0" else "5V",5.0,"input"),
                PowerDomain("3V3",3.3,"output")),
            references=listOf(reference("$product header tables","Espressif",ReferenceType.USER_GUIDE,url,revision),
                reference("MCU datasheet","Espressif",ReferenceType.MCU_DATASHEET,chip,revision),
                reference("Official Arduino core default bus pins","Espressif",ReferenceType.USER_GUIDE,
                    "https://raw.githubusercontent.com/espressif/arduino-esp32/master/variants/$variant/pins_arduino.h",revision)),
            warnings=listOf("Use an external LED for Blink. The addressable RGB LED requires a protocol that is not simulated.",
                "ADC is a linear nominal approximation at fixed attenuation; calibration, noise, ADC2 contention and conversion timing are omitted.",
                "Instruction execution, USB, radio stacks and peripheral registers are not emulated."),
            capabilities=capabilities(wifi=true,ble=family!=BoardFamily.ESP32_S2,dac=family==BoardFamily.ESP32_S2),
            usbConnector=if(family==BoardFamily.ESP32_C6) "2 × USB-C" else if(family==BoardFamily.ESP32_S3) "2 × Micro-USB" else "Micro-USB",
            boardWidth=228f,boardHeight=count*14f+34f,pcbColor=0xFF243E2C,hardware=hardware)
    }
    fun create(existing:Map<Kind,BoardDefinition>):List<BoardDefinition> {
        val pico=existing.getValue(Kind.RASPBERRY_PICO_2)
        val picoW=pico.copy(kind=Kind.RASPBERRY_PICO_2_W,product="Pico 2 W",module="CYW43439",
            revision="Pico 2 W / datasheet Release 2",sourceUrl="https://datasheets.raspberrypi.com/picow/pico-2-w-datasheet.pdf",
            pins=pico.pins.filter { it.name!="GP25" }+BoardPin("WL_LED",
                setOf(PinCapability.DIGITAL_INPUT,PinCapability.DIGITAL_OUTPUT),
                notes="CYW43439 WL_GPIO0; onboard indicator, not a header GPIO",type=PinType.ONBOARD,
                aliases=setOf("LED_BUILTIN","LED"),connectable=false,nx=0f,ny=8f),
            ledBuiltin="WL_LED",hardware=AdditionalBoardHardware.forKind(Kind.RASPBERRY_PICO_2_W),
            notes="Pico 2 W: RP2350, 4 MiB flash, 520 KiB SRAM, 150 MHz. Header follows Pico 2; LED is WL_GPIO0 on the wireless chip.",
            keywords=listOf("Pico 2 W","RP2350","MicroPython","WiFi","Bluetooth","IoT"),
            capabilities=capabilities(wifi=true,ble=true),
            references=listOf(reference("Pico 2 W datasheet","Raspberry Pi Ltd",ReferenceType.BOARD_DATASHEET,
                "https://datasheets.raspberrypi.com/picow/pico-2-w-datasheet.pdf","Release 2"),
                reference("RP2350 datasheet","Raspberry Pi Ltd",ReferenceType.MCU_DATASHEET,
                    "https://pip-assets.raspberrypi.com/categories/1214-rp2350/documents/RP-008373-DS-2-rp2350-datasheet.pdf","Release 2")),
            warnings=listOf("Wireless is hardware metadata. WL_LED is a virtual indicator; CYW43439 bus is not emulated.",
                "RP2350 instructions, PIO, security, analog tolerances and peripheral registers are not simulated."))
        return listOf(leonardo(),micro(),due(existing.getValue(Kind.ARDUINO_MEGA)),r4(existing.getValue(Kind.ARDUINO_UNO)),picoW,
            esp(Kind.ESP32_S3_DEVKIT,BoardFamily.ESP32_S3,"ESP32-S3-DevKitC-1","ESP32-S3-WROOM-1-N8R8","v1.1 N8R8",
                listOf("3V3","3V3","RST","IO4","IO5","IO6","IO7","IO15","IO16","IO17","IO18","IO8","IO3","IO46","IO9","IO10","IO11","IO12","IO13","IO14","5V","GND"),
                listOf("GND","TX","RX","IO1","IO2","IO42","IO41","IO40","IO39","IO38","IO37","IO36","IO35","IO0","IO45","IO48","IO47","IO21","IO20","IO19","GND","GND"),
                "https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32s3/esp32-s3-devkitc-1/user_guide_v1.1.html",38,reserved=setOf(35,36,37)),
            esp(Kind.ESP32_C6_DEVKIT,BoardFamily.ESP32_C6,"ESP32-C6-DevKitC-1","ESP32-C6-WROOM-1","v1.2",
                listOf("3V3","RST","IO4","IO5","IO6","IO7","IO0","IO1","IO8","IO10","IO11","IO2","IO3","5V","GND","NC"),
                listOf("GND","TX","RX","IO15","IO23","IO22","IO21","IO20","IO19","IO18","IO9","GND","IO13","IO12","GND","NC"),
                "https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32c6/esp32-c6-devkitc-1/user_guide.html",8),
            esp(Kind.ESP32_S2_SAOLA,BoardFamily.ESP32_S2,"ESP32-S2-Saola-1","ESP32-S2-WROVER","v1.2 WROVER (chip revision 0)",
                listOf("3V3")+(0..17).map { "IO$it" }+listOf("5V0","GND"),
                listOf("GND","RST","IO46","IO45","IO44","IO43","IO42","IO41","IO40","IO39","IO38","IO37","IO36","IO35","IO34","IO33","IO26","IO21","IO20","IO19","IO18"),
                "https://docs.espressif.com/projects/esp-idf/en/v4.4/esp32s2/hw-reference/esp32s2/user-guide-saola-1-v1.2.html",18,
                adcBits=13,leftConnector="J2",reserved=setOf(26),inputOnly=setOf(46)))
    }
}
