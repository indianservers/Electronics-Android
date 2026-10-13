package com.indianservers.circuitssimulator.domain

/** Explicit simulator carriers: header order is not the manufacturer's bare IC pin order. */
object MotionSensorParts {
    data class Profile(val kind:Kind,val part:String,val manufacturer:String,val minimumSupply:Double,
        val addresses:List<Int>,val identity:Int,val gyro:Boolean,val accel:Boolean,val revision:String,
        val url:String,val chipConnections:String)
    val profiles=listOf(
        Profile(Kind.L3GD20_GYRO,"L3GD20","STMicroelectronics",2.4,listOf(0x6A,0x6B),0xD4,true,false,
            "DocID022116 Rev 2","https://www.st.com/resource/en/datasheet/l3gd20.pdf",
            "VCC → IC Vdd (16) and Vdd_IO (1); GND → IC 13; SDA → IC 3; SCL → IC 2. CS (5) held HIGH; SA0 (4) selects address."),
        Profile(Kind.LSM6DSOX_IMU,"LSM6DSOX","STMicroelectronics",1.71,listOf(0x6A,0x6B),0x6C,true,true,
            "DS12814 Rev 4","https://www.st.com/resource/en/datasheet/lsm6dsox.pdf",
            "VCC → IC VDD (8) and VDDIO (5); GND → IC 6/7; SDA → IC 14; SCL → IC 13. CS (12) held HIGH; SA0 (1) selects address. Mode 1 carrier."),
        Profile(Kind.LIS3DH_ACCEL,"LIS3DH","STMicroelectronics",1.71,listOf(0x18,0x19),0x33,false,true,
            "DocID17530 Rev 2","https://www.st.com/resource/en/datasheet/lis3dh.pdf",
            "VCC → IC Vdd (14) and Vdd_IO (1); GND → IC 5/12; SDA → IC 6; SCL → IC 4. CS (8) held HIGH; SA0 (7) selects address."),
        Profile(Kind.ADXL345_ACCEL,"ADXL345","Analog Devices",2.0,listOf(0x53,0x1D),0xE5,false,true,
            "Rev G","https://www.analog.com/media/en/technical-documentation/data-sheets/adxl345.pdf",
            "VCC → IC VS (6) and VDD I/O (1); GND → IC 2/4/5; SDA → IC 13; SCL → IC 14. CS (7) held HIGH; ALT ADDRESS (12) selects 0x53 LOW / 0x1D HIGH.")
    ).associateBy { it.kind }
    val pins=listOf("VCC","GND","SDA","SCL")
    val pinNames=profiles.mapValues { pins }
    val limitations=listOf(
        "Four-pin simulator I2C carrier, not a commercial breakout pinout. VDD and VDDIO are tied to VCC; no regulator or level shifter. Maximum operating supply is 3.6 V. External SDA/SCL pull-ups are required.",
        "Nominal register-level polling model: identity, power/axis enable, range, byte order, and coherent burst data. Motion values are per-device properties in chip-axis coordinates; acceleration includes gravity (stationary default Z = +1 g).",
        "Samples refresh on reads, not at physical ODR times. Noise, offset drift, filters, startup delay, FIFO, interrupts, self-test, SPI, I3C and embedded sensor fusion/AI are not simulated. Unsupported register writes are rejected; unsupported reads return no data.",
        "Supply load is an educational 8 kOhm resistance, not a datasheet current-consumption model. Manufacturer ratings describe the chip; damage is not predicted."
    )
    fun metadata(profile:Profile)=DatasheetMetadata(manufacturer=profile.manufacturer,partNumber=profile.part,
        packageName="Four-pin simulated I2C carrier",referenceUrl=profile.url,revision=profile.revision,
        physicalPinOrder=pins,lastVerifiedUtc="2026-10-12",sourceNotes=profile.chipConnections+
            " Header order: VCC, GND, SDA, SCL. Manufacturer IC pins above are separate from header electrical indices. Operating supply ${profile.minimumSupply}–3.6 V.")
    val definitions=profiles.values.map { profile ->
        val address=Parameter("address","I2C address strap","",profile.addresses.first().toDouble(),
            profile.addresses.min().toDouble(),profile.addresses.max().toDouble())
        val gyro=if(profile.gyro) listOf("X","Y","Z").map { axis ->
            Parameter("gyro${axis}Dps","Angular velocity $axis","°/s",0.0,-2000.0,2000.0) } else emptyList()
        val accel=if(profile.accel) listOf("X","Y","Z").map { axis ->
            Parameter("accel${axis}G","Acceleration $axis","g",if(axis=="Z")1.0 else 0.0,-16.0,16.0) } else emptyList()
        Definition(profile.kind,"${profile.part} ${if(profile.gyro && profile.accel) "6-axis gyroscope / accelerometer" else if(profile.gyro) "3-axis gyroscope" else "3-axis accelerometer"}; I2C polling carrier",
            listOf(address)+gyro+accel,datasheet=metadata(profile),keywords=listOf("motion","MEMS","I2C",profile.part)+
                (if(profile.gyro) listOf("gyro","gyroscope","rotation","angular velocity") else emptyList())+
                (if(profile.accel) listOf("accelerometer","acceleration","tilt") else emptyList()),
            applications=listOf("Read signed axis samples through board I2C firmware"),limitations=limitations)
    }
}
