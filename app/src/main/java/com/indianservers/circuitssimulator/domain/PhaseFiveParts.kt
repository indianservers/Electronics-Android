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
        Definition(Kind.SERIAL_TERMINAL,"Virtual UART terminal with TX, RX and GND",emptyList(),
            keywords=listOf("serial","uart","console"),applications=listOf("MCU serial debugging"),
            limitations=listOf("Byte transfer is event-based; baud is metadata, not bit timing.")),
        Definition(Kind.I2C_TEMP_SENSOR,"I²C temperature sensor, default address 0x48",listOf(
            p("address","I²C address","",0x48.toDouble(),1.0,127.0)),
            keywords=listOf("I2C","temperature","LM75"),applications=listOf("Digital temperature sensing"),
            limitations=listOf("Register map is a two-byte temperature word; not a specific vendor part.")),
        Definition(Kind.I2C_LCD,"16x2 character LCD reached through a simulated I²C backpack",listOf(
            p("address","I²C address","",0x27.toDouble(),1.0,127.0)),
            keywords=listOf("LCD","HD44780","I2C"),applications=listOf("Text display"),
            limitations=listOf("Printable bytes update the text buffer; full HD44780 timing is not modeled.")),
        Definition(Kind.OLED_SSD1306,"Small I²C OLED text buffer, default address 0x3C",listOf(
            p("address","I²C address","",0x3C.toDouble(),1.0,127.0)),
            keywords=listOf("OLED","SSD1306","I2C"),applications=listOf("Embedded display"),
            limitations=listOf("Text buffer only; graphics libraries are not implemented.")),
        Definition(Kind.I2C_EEPROM,"256-byte I²C EEPROM, default address 0x50",listOf(
            p("address","I²C address","",0x50.toDouble(),1.0,127.0)),
            keywords=listOf("EEPROM","I2C","memory"),applications=listOf("Non-volatile storage labs"),
            limitations=listOf("256 bytes, no wear or page-timing model.")),
        Definition(Kind.ULTRASONIC,"HC-SR04-style trigger/echo ranger",emptyList(),
            keywords=listOf("ultrasonic","distance","HC-SR04"),applications=listOf("Distance measurement"),
            limitations=listOf("Echo width follows environment distance; acoustic physics are omitted.")),
        Definition(Kind.PIR_SENSOR,"Passive infrared motion output",listOf(
            p("delayMs","Hold time","ms",200.0,10.0,5000.0)),
            keywords=listOf("PIR","motion"),applications=listOf("Occupancy sensing"),
            limitations=listOf("Environment motion flag drives the output after a short delay.")),
        Definition(Kind.SPI_MEMORY,"Simple SPI byte memory selected by CS",emptyList(),
            keywords=listOf("SPI","memory"),applications=listOf("SPI transfer labs"),
            limitations=listOf("Byte store/recall only; no flash command set."))
    )
}
