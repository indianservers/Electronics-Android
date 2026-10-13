package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*

/** Ready-wired, 3.3 V examples with external pull-ups and raw register access. */
object MotionSensorExamples {
    fun source(kind:Kind):String {
        val profile=MotionSensorParts.profiles.getValue(kind)
        val initialization=when(kind) {
            Kind.L3GD20_GYRO -> listOf(0x20 to 0x0F,0x23 to 0)
            Kind.LSM6DSOX_IMU -> listOf(0x10 to 0x40,0x11 to 0x40)
            Kind.LIS3DH_ACCEL -> listOf(0x20 to 0x57,0x23 to 0x08)
            else -> listOf(0x31 to 8,0x2D to 8)
        }.joinToString("\n") { (reg,value) ->
            "    Wire.beginTransmission(${profile.addresses.first()}); Wire.write($reg); Wire.write($value); Wire.endTransmission();" }
        val register=when(kind) { Kind.L3GD20_GYRO,Kind.LIS3DH_ACCEL -> 0xA8
            Kind.LSM6DSOX_IMU -> 0x22;else -> 0x32 }
        val conversion=when(kind) { Kind.LIS3DH_ACCEL -> "raw / 16.0 * 0.001"
            Kind.ADXL345_ACCEL -> "raw * 0.0039";else -> "raw * 0.00875" }
        return """void setup() {
    Serial.begin(9600);
    Wire.begin();
$initialization
}
void loop() {
    Wire.beginTransmission(${profile.addresses.first()});
    Wire.write($register);
    Wire.endTransmission(false);
    Wire.requestFrom(${profile.addresses.first()}, 6);
    if (Wire.available() == 6) {
        int low = Wire.read();
        int high = Wire.read();
        int raw = high * 256 + low;
        if (raw >= 32768) { raw = raw - 65536; }
        double axisX = $conversion;
        Serial.print("${profile.part} X ${if(profile.gyro) "deg/s" else "g"}: ");
        Serial.println(axisX);
    } else { Serial.println("No sensor data: check wiring and power"); }
    delay(100);
}"""
    }
    fun circuit(kind:Kind):Circuit {
        val profile=MotionSensorParts.profiles.getValue(kind)
        val board=PlacedComponent(kind=Kind.ESP32_DEVKIT,reference="ESP1",x=230f,y=350f,
            parameters=mapOf("usbPower" to 1.0))
        val defaults=ComponentRegistry.definitions.getValue(kind).parameters.associate { it.key to it.default }
        val sensor=PlacedComponent(kind=kind,reference=if(profile.gyro)"GYRO1" else "ACC1",x=610f,y=350f,
            parameters=defaults+if(profile.gyro) mapOf("gyroXDps" to 87.5) else mapOf("accelXG" to .5))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=430f,y=620f)
        val sda=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=420f,y=140f,parameters=mapOf("resistance" to 4700.0))
        val scl=PlacedComponent(kind=Kind.RESISTOR,reference="R2",x=610f,y=140f,parameters=mapOf("resistance" to 4700.0))
        val def=BoardRegistry.boards.getValue(board.kind)
        fun b(name:String)=TerminalRef(board.id,def.index(name))
        fun p(part:PlacedComponent,i:Int)=TerminalRef(part.id,i)
        fun w(a:TerminalRef,z:TerminalRef)=Wire(start=a,end=z)
        return Circuit("${profile.part} Motion Lab",listOf(board,sensor,ground,sda,scl),listOf(
            w(b("GND"),p(ground,0)),w(b("3V3"),p(sensor,0)),w(b("GND"),p(sensor,1)),
            w(b("IO21"),p(sensor,2)),w(b("IO22"),p(sensor,3)),
            w(b("3V3"),p(sda,0)),w(p(sda,1),p(sensor,2)),w(b("3V3"),p(scl,0)),w(p(scl,1),p(sensor,3))),
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,source(kind),true)))
    }
}
