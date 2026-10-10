package com.indianservers.circuitssimulator.domain

/** Protocol and environment peripherals introduced for the embedded runtime. */
object PhaseFiveParts {
    val pinNames=mapOf(
        Kind.SERIAL_TERMINAL to listOf("TX","RX","GND"),
        Kind.I2C_TEMP_SENSOR to listOf("VCC","GND","SDA","SCL"),
        Kind.I2C_LCD to listOf("VCC","GND","SDA","SCL"),
        Kind.OLED_SSD1306 to listOf("VCC","GND","SDA","SCL"),
        Kind.I2C_EEPROM to listOf("VCC","GND","SDA","SCL"),
        Kind.ULTRASONIC to listOf("VCC","GND","TRIG","ECHO"),
        Kind.PIR_SENSOR to listOf("VCC","OUT","GND"),
        Kind.SPI_MEMORY to listOf("VCC","GND","MOSI","MISO","SCK","CS")
    )
    private fun p(key:String,label:String,unit:String,default:Double,min:Double,max:Double)=
        Parameter(key,label,unit,default,min,max)
    val definitions=listOf(
        Definition(Kind.SERIAL_TERMINAL,"Virtual UART terminal with TX, RX and GND",listOf(p("baud","Baud","baud",9600.0,300.0,1000000.0),
            p("dataBits","Data bits","",8.0,5.0,8.0),p("parity","Parity (0 none, 1 even, 2 odd)","",0.0,0.0,2.0),
            p("stopBits","Stop bits","",1.0,1.0,2.0)),
            keywords=listOf("serial","uart","console"),applications=listOf("MCU serial debugging"),
            limitations=listOf("Event-scheduled bytes; physical UART framing metadata is checked.")),
        Definition(Kind.I2C_TEMP_SENSOR,"I²C temperature sensor, default address 0x48",listOf(
            p("address","I²C address","",0x48.toDouble(),72.0,79.0)),
            keywords=listOf("I2C","temperature","LM75"),applications=listOf("Digital temperature sensing"),
            limitations=listOf("LM75B registers with signed 0.5 °C samples, config/shutdown and thresholds; OS pin not exposed.")),
        Definition(Kind.I2C_LCD,"16x2 character LCD reached through a simulated I²C backpack",listOf(
            p("address","I²C address","",0x27.toDouble(),32.0,39.0)),
            keywords=listOf("LCD","HD44780","I2C"),applications=listOf("Text display"),
            limitations=listOf("PCF8574 port bytes drive four-bit HD44780 latch edges; readback/custom glyph rendering omitted.")),
        Definition(Kind.OLED_SSD1306,"SSD1306 128×64 I²C framebuffer module, 3.3 V",listOf(
            p("address","I²C address","",0x3C.toDouble(),60.0,61.0)),
            keywords=listOf("OLED","SSD1306","I2C"),applications=listOf("Embedded display"),
            limitations=listOf("GDDRAM and addressing/display commands; scrolling and fade omitted.")),
        Definition(Kind.I2C_EEPROM,"256-byte I²C EEPROM, default address 0x50",listOf(
            p("address","I²C address","",0x50.toDouble(),80.0,87.0),
            p("writeProtect","Write protect (0 off, 1 on)","",0.0,0.0,1.0)),
            keywords=listOf("EEPROM","I2C","memory"),applications=listOf("Non-volatile storage labs"),
            limitations=listOf("AT24C02C: 256 bytes, 8-byte page wrap, 5 ms write busy, configurable WP; wear omitted.")),
        Definition(Kind.ULTRASONIC,"HC-SR04-style trigger/echo ranger",emptyList(),
            keywords=listOf("ultrasonic","distance","HC-SR04"),applications=listOf("Distance measurement"),
            limitations=listOf("Echo width follows environment distance; acoustic physics are omitted.")),
        Definition(Kind.PIR_SENSOR,"Passive infrared motion output",listOf(
            p("delayMs","Hold time","ms",3000.0,10.0,300000.0),
            p("warmupMs","Warm-up","ms",30000.0,0.0,60000.0),
            p("retrigger","Retrigger (0 off, 1 on)","",1.0,0.0,1.0)),
            keywords=listOf("PIR","motion"),applications=listOf("Occupancy sensing"),
            limitations=listOf("3.3 V output after configurable warm-up; held/retriggered motion detection.")),
        Definition(Kind.SPI_MEMORY,"W25Q32JV 4 MiB SPI NOR flash, 3.3 V",emptyList(),
            keywords=listOf("SPI","memory"),applications=listOf("SPI transfer labs"),
            limitations=listOf("JEDEC ID, WEL/BUSY, program, erase, protection and persistence; quad mode omitted."))
    ).map { def -> com.indianservers.circuitssimulator.firmware.EmbeddedProfiles.profiles[def.kind]?.let { profile ->
        def.copy(datasheet=profile.datasheet.copy(physicalPinOrder=pinNames.getValue(def.kind)),limitations=def.limitations+profile.limitations)
    } ?: def }
}
