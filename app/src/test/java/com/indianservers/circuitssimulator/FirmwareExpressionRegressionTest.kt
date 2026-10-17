package com.indianservers.circuitssimulator
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

class FirmwareExpressionRegressionTest {
    @Test fun prefixOperatorsBindAboveMultiplicationAdditionAndRelations(){
        val c=PhaseTwoProjects.circuit(Kind.ARDUINO_UNO,16);val b=FirmwareBoard(c,"B1")
        val s=FirmwareSession(ArduinoSubset.compile("void setup(){double a=-45+175*26214/65535.0;double b=-2*3+4;int c=!0+1;int d=!2<3;double e=abs(-3+1);double f=-(-2+5);}void loop(){delay(10);}",b),b)
        assertNull(s.advance(1000).error);val v=s.variables()
        mapOf("a" to 25.0,"b" to -2.0,"c" to 2.0,"d" to 1.0,"e" to 2.0,"f" to -3.0).forEach {(key,want)->assertEquals(key,want,v.getValue(key).toDouble(),.0000001)}
    }
    private fun parkingSession(body:String):FirmwareSession {
        val c=PhaseTwoParkingProjects.circuit(Kind.ARDUINO_UNO);val d=BoardRegistry.boards.getValue(Kind.ARDUINO_UNO)
        val trigger=c.wires.first {it.end==TerminalRef("U0",2)}.start.index
        val echo=c.wires.first {it.start==TerminalRef("ECHO_INVERTER",0) && it.end.componentId=="B1"}.end.index
        val t=d.pins[trigger].name;val e=d.pins[echo].name
        val source="void setup(){pinMode($t,OUTPUT);pinMode($e,INPUT);int done=0;double width=0;double scaled=0;double elapsed=0;double start=0;double sum=0;}void loop(){if(done==0){"+
            body.replace("TRIGGER","digitalWrite($t,LOW);delayMicroseconds(2);digitalWrite($t,HIGH);delayMicroseconds(10);digitalWrite($t,LOW);")
                .replace("ECHO",e)+"done=1;}delay(100);}"
        val b=FirmwareBoard(c,"B1");return FirmwareSession(ArduinoSubset.compile(source,b),b)
    }
    private fun finish(s:FirmwareSession){var steps=0;while((s.variables()["done"]?.toDouble() ?: 0.0)<1){assertNull(s.advance(10000).error);assertTrue(++steps<100)}}
    @Test fun pulseInReturnsMeasuredWidthToAssignmentsAndArithmetic(){
        val s=parkingSession("TRIGGER width=pulseIn(ECHO,LOW,30000);delay(70);TRIGGER scaled=pulseIn(ECHO,LOW,30000)/58.0;")
        finish(s);assertEquals(8700.0,s.variables().getValue("width").toDouble(),41.0)
        assertEquals(150.0,s.variables().getValue("scaled").toDouble(),41.0/58)
    }
    @Test fun twoBlockingCallsInOneExpressionEachWaitAndReturn(){
        val s=parkingSession("start=micros();sum=pulseIn(ECHO,LOW,1000)+pulseIn(ECHO,LOW,1000)+5;elapsed=micros()-start;")
        finish(s);assertEquals(5.0,s.variables().getValue("sum").toDouble(),0.0)
        assertTrue(s.variables().getValue("elapsed").toDouble() in 2000.0..2100.0)
    }
    @Test fun pulseAlreadyInProgressIsNotReturnedAsANewPulse(){
        val s=parkingSession("TRIGGER delay(1);start=micros();width=pulseIn(ECHO,LOW,30000);elapsed=micros()-start;")
        finish(s);assertEquals(0.0,s.variables().getValue("width").toDouble(),0.0)
        assertTrue(s.variables().getValue("elapsed").toDouble()>=30000)
    }
}
