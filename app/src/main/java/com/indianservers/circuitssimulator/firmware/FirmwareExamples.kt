package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*

/** Executable subset examples. Every shown API is implemented by the runtime. */
object FirmwareExamples {
    const val BLINK="""void setup() {
    pinMode(13, OUTPUT);
    Serial.begin(9600);
}
void loop() {
    digitalWrite(13, HIGH);
    Serial.println("LED on");
    delay(500);
    digitalWrite(13, LOW);
    Serial.println("LED off");
    delay(500);
}"""
    const val BUTTON="""void setup() {
    pinMode(2, INPUT_PULLUP);
    pinMode(13, OUTPUT);
    Serial.begin(9600);
}
void loop() {
    if (digitalRead(2) == LOW) {
        digitalWrite(13, HIGH);
        Serial.println("Button: PRESSED");
    } else {
        digitalWrite(13, LOW);
        Serial.println("Button: OPEN");
    }
    delay(50);
}"""
    const val PWM_FADE="""void setup() {
    pinMode(9, OUTPUT);
    Serial.begin(9600);
}
void loop() {
    int duty = 0;
    while (duty <= 255) {
        analogWrite(9, duty);
        duty = duty + 32;
        delay(40);
    }
    duty = 255;
    while (duty >= 0) {
        analogWrite(9, duty);
        duty = duty - 32;
        delay(40);
    }
}"""
    const val ANALOG_READ="""void setup() {
    Serial.begin(9600);
}
void loop() {
    int sample = analogRead(A0);
    Serial.println(sample);
    delay(100);
}"""
    const val SERIAL_HELLO="""void setup() {
    Serial.begin(9600);
    Serial.println("hello");
}
void loop() {
    delay(1000);
}"""
    const val TRAFFIC="""void setup() {
    pinMode(8, OUTPUT);
    pinMode(9, OUTPUT);
    pinMode(10, OUTPUT);
    Serial.begin(9600);
}
void loop() {
    digitalWrite(8, HIGH);
    digitalWrite(9, LOW);
    digitalWrite(10, LOW);
    Serial.println("RED");
    delay(400);
    digitalWrite(8, LOW);
    digitalWrite(9, HIGH);
    Serial.println("AMBER");
    delay(200);
    digitalWrite(9, LOW);
    digitalWrite(10, HIGH);
    Serial.println("GREEN");
    delay(400);
}"""
    const val SERVO_SWEEP="""Servo servo;
void setup() {
    servo.attach(9);
    Serial.begin(9600);
}
void loop() {
    int angle = analogRead(A0);
    int target = map(angle, 0, 1023, 0, 180);
    servo.write(target);
    Serial.println(target);
    delay(30);
}"""
    const val TEMPERATURE="""void setup() {
    Serial.begin(9600);
}
void loop() {
    int sample = analogRead(A0);
    float volts = sample * 5.0 / 1023.0;
    float temp = volts / 0.01;
    Serial.print("Temperature: ");
    Serial.println(temp);
    delay(200);
}"""
    const val I2C_SCAN="""void setup() {
    Serial.begin(9600);
    Wire.begin();
    int addr = 1;
    while (addr < 127) {
        Wire.beginTransmission(addr);
        int err = Wire.endTransmission();
        if (err == 0) {
            Serial.print("Found device at 0x");
            Serial.println(hex(addr));
        }
        addr = addr + 1;
    }
}
void loop() {
    delay(2000);
}"""
    const val I2C_TEMP="""void setup() {
    Serial.begin(9600);
    Wire.begin();
}
void loop() {
    Wire.beginTransmission(72);
    Wire.write(0);
    Wire.endTransmission();
    Wire.requestFrom(72, 2);
    int hi = Wire.read();
    int lo = Wire.read();
    int milli = hi * 256 + lo;
    Serial.print("Temperature: ");
    Serial.println(milli / 100);
    delay(200);
}"""
    const val UART_TX="""void setup() {
    Serial.begin(9600);
}
void loop() {
    Serial.println("ON");
    delay(300);
    Serial.println("OFF");
    delay(300);
}"""
    const val UART_RX="""void setup() {
    Serial.begin(9600);
    pinMode(13, OUTPUT);
}
void loop() {
    if (Serial.available() > 0) {
        int ch = Serial.read();
        if (ch == 79) {
            digitalWrite(13, HIGH);
        }
        if (ch == 70) {
            digitalWrite(13, LOW);
        }
    }
    delay(10);
}"""
    const val PICO_BLINK="""from machine import Pin
import time
led = Pin(15, Pin.OUT)
while True:
    led.value(1)
    time.sleep(0.5)
    led.value(0)
    time.sleep(0.5)
"""

    data class Example(val id:String,val title:String,val language:FirmwareLanguage,val source:String)
    fun examplesFor(kind:Kind):List<Example> {
        val arduino=listOf(
            Example("blink","Blink",FirmwareLanguage.ARDUINO_SUBSET,BLINK),
            Example("button","Button",FirmwareLanguage.ARDUINO_SUBSET,BUTTON),
            Example("pwm","PWM Fade",FirmwareLanguage.ARDUINO_SUBSET,PWM_FADE),
            Example("analog","Analog Read",FirmwareLanguage.ARDUINO_SUBSET,ANALOG_READ),
            Example("serial","Serial Hello",FirmwareLanguage.ARDUINO_SUBSET,SERIAL_HELLO),
            Example("servo","Servo Sweep",FirmwareLanguage.ARDUINO_SUBSET,SERVO_SWEEP),
            Example("i2c","I2C Scanner",FirmwareLanguage.ARDUINO_SUBSET,I2C_SCAN)
        )
        return if(BoardSupport.languages(kind).contains(FirmwareLanguage.MICROPYTHON_SUBSET))
            arduino+Example("mpy-blink","MicroPython Blink",FirmwareLanguage.MICROPYTHON_SUBSET,PICO_BLINK)
        else arduino
    }

    fun starter(kind:Kind,circuitName:String,language:FirmwareLanguage=FirmwareLanguage.ARDUINO_SUBSET):String {
        if(language==FirmwareLanguage.MICROPYTHON_SUBSET) return PICO_BLINK
        return when(circuitName) {
            "Blinking LED" -> BLINK
            "Push Button LED" -> BUTTON
            "Traffic Light" -> TRAFFIC
            "Servo Control" -> SERVO_SWEEP
            "Temperature Monitor" -> TEMPERATURE
            "PWM Fade" -> PWM_FADE
            "Analog Read" -> ANALOG_READ
            "I2C Scanner" -> I2C_SCAN
            "I2C Temperature" -> I2C_TEMP
            "UART Transmitter" -> UART_TX
            "UART Receiver" -> UART_RX
            else -> BLINK
        }
    }

    private fun part(kind:Kind,ref:String,x:Float,y:Float,values:Map<String,Double> = emptyMap())=
        PlacedComponent(kind=kind,reference=ref,x=x,y=y,
            parameters=ComponentRegistry.definitions.getValue(kind).parameters.associate { it.key to it.default }+values)
    private fun wire(a:PlacedComponent,ap:Int,b:PlacedComponent,bp:Int)=
        Wire(start=TerminalRef(a.id,ap),end=TerminalRef(b.id,bp))

    fun blinkCircuit():Circuit {
        val board=part(Kind.ARDUINO_UNO,"UNO1",220f,390f)
        val supply=part(Kind.SOURCE,"V1",140f,650f,mapOf("voltage" to 5.0))
        val ground=part(Kind.GROUND,"GND1",430f,760f)
        val resistor=part(Kind.RESISTOR,"R1",600f,300f,mapOf("resistance" to 330.0,"rating" to .25))
        val led=part(Kind.LED,"D1",810f,450f)
        val pins=BoardRegistry.boards.getValue(board.kind)
        return Circuit("Blinking LED",listOf(board,supply,ground,resistor,led),listOf(
            wire(supply,0,board,pins.index("5V")),wire(supply,1,board,pins.index("GND")),
            wire(supply,1,ground,0),wire(board,pins.index("D13"),resistor,0),
            wire(resistor,1,led,0),wire(led,1,ground,0)),
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,BLINK,true)))
    }

    fun buttonCircuit():Circuit {
        val base=blinkCircuit()
        val board=base.components.first { it.kind==Kind.ARDUINO_UNO }
        val ground=base.components.first { it.kind==Kind.GROUND }
        val button=part(Kind.SWITCH,"SW1",360f,220f).copy(closed=false)
        val pins=BoardRegistry.boards.getValue(board.kind)
        return base.copy(name="Push Button LED",
            components=base.components+button,
            wires=base.wires+listOf(wire(board,pins.index("D2"),button,0),wire(button,1,ground,0)),
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,BUTTON,true)))
    }

    fun trafficCircuit():Circuit {
        val board=part(Kind.ARDUINO_UNO,"UNO1",200f,400f)
        val supply=part(Kind.SOURCE,"V1",120f,680f,mapOf("voltage" to 5.0))
        val ground=part(Kind.GROUND,"GND1",420f,780f)
        val rR=part(Kind.RESISTOR,"R1",520f,220f,mapOf("resistance" to 330.0,"rating" to .25))
        val rA=part(Kind.RESISTOR,"R2",520f,360f,mapOf("resistance" to 330.0,"rating" to .25))
        val rG=part(Kind.RESISTOR,"R3",520f,500f,mapOf("resistance" to 330.0,"rating" to .25))
        val red=part(Kind.RED_LED,"D1",760f,220f)
        val amber=part(Kind.LED,"D2",760f,360f)
        val green=part(Kind.GREEN_LED,"D3",760f,500f)
        val pins=BoardRegistry.boards.getValue(board.kind)
        return Circuit("Traffic Light",listOf(board,supply,ground,rR,rA,rG,red,amber,green),listOf(
            wire(supply,0,board,pins.index("5V")),wire(supply,1,board,pins.index("GND")),
            wire(supply,1,ground,0),
            wire(board,pins.index("D8"),rR,0),wire(rR,1,red,0),wire(red,1,ground,0),
            wire(board,pins.index("D9"),rA,0),wire(rA,1,amber,0),wire(amber,1,ground,0),
            wire(board,pins.index("D10"),rG,0),wire(rG,1,green,0),wire(green,1,ground,0)),
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,TRAFFIC,true)))
    }

    fun analogCircuit():Circuit {
        val base=blinkCircuit()
        val board=base.components.first { it.kind==Kind.ARDUINO_UNO }
        val supply=base.components.first { it.kind==Kind.SOURCE }
        val ground=base.components.first { it.kind==Kind.GROUND }
        val pot=part(Kind.POTENTIOMETER,"RV1",560f,620f,mapOf("position" to .5))
        val pins=BoardRegistry.boards.getValue(board.kind)
        return base.copy(name="Analog Read",
            components=base.components+pot,
            wires=base.wires+listOf(wire(supply,0,pot,0),wire(pot,2,ground,0),wire(pot,1,board,pins.index("A0"))),
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,ANALOG_READ,true)))
    }

    fun pwmCircuit():Circuit {
        val base=blinkCircuit()
        val board=base.components.first { it.kind==Kind.ARDUINO_UNO }
        val resistor=base.components.first { it.kind==Kind.RESISTOR }
        val pins=BoardRegistry.boards.getValue(board.kind)
        val wires=base.wires.map { w ->
            if(w.start==TerminalRef(board.id,pins.index("D13")) || w.end==TerminalRef(board.id,pins.index("D13")))
                w.copy(start=TerminalRef(board.id,pins.index("D9")),end=TerminalRef(resistor.id,0))
            else w
        }
        return base.copy(name="PWM Fade",wires=wires,
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,PWM_FADE,true)))
    }

    fun servoCircuit():Circuit {
        val base=analogCircuit()
        val board=base.components.first { it.kind==Kind.ARDUINO_UNO }
        val supply=base.components.first { it.kind==Kind.SOURCE }
        val ground=base.components.first { it.kind==Kind.GROUND }
        val servo=part(Kind.SERVO_MOTOR,"SERVO1",820f,240f)
        val pins=BoardRegistry.boards.getValue(board.kind)
        return base.copy(name="Servo Control",components=base.components+servo,
            wires=base.wires+listOf(wire(supply,0,servo,0),wire(servo,1,ground,0),
                wire(board,pins.index("D9"),servo,2)),
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,SERVO_SWEEP,true)))
    }

    fun temperatureCircuit():Circuit {
        val base=blinkCircuit()
        val board=base.components.first { it.kind==Kind.ARDUINO_UNO }
        val supply=base.components.first { it.kind==Kind.SOURCE }
        val ground=base.components.first { it.kind==Kind.GROUND }
        val sensor=part(Kind.LM35_SENSOR,"TMP1",620f,620f)
        val pins=BoardRegistry.boards.getValue(board.kind)
        return base.copy(name="Temperature Monitor",components=base.components+sensor,
            wires=base.wires+listOf(wire(supply,0,sensor,0),wire(sensor,2,ground,0),
                wire(sensor,1,board,pins.index("A0"))),
            environment=EnvironmentState(temperatureC=27.3),
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,TEMPERATURE,true)))
    }

    fun i2cCircuit():Circuit {
        val base=blinkCircuit()
        val board=base.components.first { it.kind==Kind.ARDUINO_UNO }
        val supply=base.components.first { it.kind==Kind.SOURCE }
        val ground=base.components.first { it.kind==Kind.GROUND }
        val sensor=part(Kind.I2C_TEMP_SENSOR,"TMP1",680f,220f)
        val lcd=part(Kind.OLED_SSD1306,"OLED1",680f,420f)
        val pu1=part(Kind.RESISTOR,"R2",480f,180f,mapOf("resistance" to 4700.0,"rating" to .25))
        val pu2=part(Kind.RESISTOR,"R3",480f,260f,mapOf("resistance" to 4700.0,"rating" to .25))
        val pins=BoardRegistry.boards.getValue(board.kind)
        return base.copy(name="I2C Scanner",components=base.components+listOf(sensor,lcd,pu1,pu2),
            wires=base.wires+listOf(
                wire(supply,0,sensor,0),wire(sensor,1,ground,0),
                wire(supply,0,lcd,0),wire(lcd,1,ground,0),
                wire(board,pins.index("A4"),sensor,2),wire(board,pins.index("A5"),sensor,3),
                wire(board,pins.index("A4"),lcd,2),wire(board,pins.index("A5"),lcd,3),
                wire(supply,0,pu1,0),wire(pu1,1,board,pins.index("A4")),
                wire(supply,0,pu2,0),wire(pu2,1,board,pins.index("A5"))),
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,I2C_SCAN,true)))
    }

    fun uartPairCircuit():Circuit {
        val a=part(Kind.ARDUINO_UNO,"UNO1",180f,360f)
        val b=part(Kind.ARDUINO_UNO,"UNO2",620f,360f)
        val supply=part(Kind.SOURCE,"V1",140f,700f,mapOf("voltage" to 5.0))
        val ground=part(Kind.GROUND,"GND1",500f,780f)
        val resistor=part(Kind.RESISTOR,"R1",820f,240f,mapOf("resistance" to 330.0,"rating" to .25))
        val led=part(Kind.LED,"D1",960f,360f)
        val pa=BoardRegistry.boards.getValue(a.kind)
        val pb=BoardRegistry.boards.getValue(b.kind)
        return Circuit("UART Link",listOf(a,b,supply,ground,resistor,led),listOf(
            wire(supply,0,a,pa.index("5V")),wire(supply,1,a,pa.index("GND")),
            wire(supply,0,b,pb.index("5V")),wire(supply,1,b,pb.index("GND")),
            wire(supply,1,ground,0),
            wire(a,pa.index("D1"),b,pb.index("D0")),
            wire(b,pb.index("D1"),a,pa.index("D0")),
            wire(b,pb.index("D13"),resistor,0),wire(resistor,1,led,0),wire(led,1,ground,0)),
            firmware=listOf(
                FirmwareAttachment(a.id,FirmwareLanguage.ARDUINO_SUBSET,UART_TX,true),
                FirmwareAttachment(b.id,FirmwareLanguage.ARDUINO_SUBSET,UART_RX,true)))
    }

    fun esp32OledCircuit():Circuit {
        val board=part(Kind.ESP32_DEVKIT,"ESP1",220f,390f)
        val supply=part(Kind.SOURCE,"V1",140f,650f,mapOf("voltage" to 3.3))
        val ground=part(Kind.GROUND,"GND1",430f,760f)
        val lcd=part(Kind.OLED_SSD1306,"OLED1",680f,360f)
        val pu1=part(Kind.RESISTOR,"R2",480f,180f,mapOf("resistance" to 4700.0,"rating" to .25))
        val pu2=part(Kind.RESISTOR,"R3",480f,260f,mapOf("resistance" to 4700.0,"rating" to .25))
        val pins=BoardRegistry.boards.getValue(board.kind)
        return Circuit("ESP32 OLED",listOf(board,supply,ground,lcd,pu1,pu2),listOf(
            wire(supply,0,board,pins.index("3V3")),wire(supply,1,board,pins.index("GND")),
            wire(supply,1,ground,0),wire(supply,0,lcd,0),wire(lcd,1,ground,0),
            wire(board,pins.index("IO21"),lcd,2),wire(board,pins.index("IO22"),lcd,3),
            wire(supply,0,pu1,0),wire(pu1,1,board,pins.index("IO21")),
            wire(supply,0,pu2,0),wire(pu2,1,board,pins.index("IO22"))),
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,I2C_SCAN,true)))
    }

    fun picoAdcCircuit():Circuit {
        val board=part(Kind.RASPBERRY_PICO,"PICO1",220f,390f)
        val supply=part(Kind.SOURCE,"V1",140f,650f,mapOf("voltage" to 3.3))
        val ground=part(Kind.GROUND,"GND1",430f,760f)
        val pot=part(Kind.POTENTIOMETER,"RV1",560f,620f,mapOf("position" to .5))
        val resistor=part(Kind.RESISTOR,"R1",600f,300f,mapOf("resistance" to 330.0,"rating" to .25))
        val led=part(Kind.LED,"D1",810f,450f)
        val pins=BoardRegistry.boards.getValue(board.kind)
        return Circuit("Pico ADC PWM",listOf(board,supply,ground,pot,resistor,led),listOf(
            wire(supply,0,board,pins.index("3V3_OUT")),wire(supply,1,board,pins.index("GND")),
            wire(supply,1,ground,0),wire(supply,0,pot,0),wire(pot,2,ground,0),
            wire(pot,1,board,pins.index("GP26")),
            wire(board,pins.index("GP15"),resistor,0),wire(resistor,1,led,0),wire(led,1,ground,0)),
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,ANALOG_READ,true)))
    }

    fun nodeMcuLdrCircuit():Circuit {
        val board=part(Kind.NODEMCU_ESP8266,"ESP1",220f,390f)
        val supply=part(Kind.SOURCE,"V1",140f,650f,mapOf("voltage" to 3.3))
        val ground=part(Kind.GROUND,"GND1",430f,760f)
        val ldr=part(Kind.LDR,"LDR1",560f,620f)
        val drop=part(Kind.RESISTOR,"R2",560f,480f,mapOf("resistance" to 10000.0,"rating" to .25))
        val resistor=part(Kind.RESISTOR,"R1",600f,300f,mapOf("resistance" to 330.0,"rating" to .25))
        val led=part(Kind.LED,"D1",810f,450f)
        val pins=BoardRegistry.boards.getValue(board.kind)
        return Circuit("NodeMCU LDR",listOf(board,supply,ground,ldr,drop,resistor,led),listOf(
            wire(supply,0,board,pins.index("3V3")),wire(supply,1,board,pins.index("GND")),
            wire(supply,1,ground,0),wire(supply,0,ldr,0),wire(ldr,1,board,pins.index("A0")),
            wire(board,pins.index("A0"),drop,0),wire(drop,1,ground,0),
            wire(board,pins.index("D4"),resistor,0),wire(resistor,1,led,0),wire(led,1,ground,0)),
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,ANALOG_READ,true)))
    }
}
