package com.indianservers.circuitssimulator
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test
class DomainSensorModelTest {
    @Test fun scd41PeriodicReadinessChecksumAndConsumption() {
        assertEquals(0x92,Scd41Model.crc(listOf(0xBE,0xEF)))
        val m=Scd41Model();m.stimuli=mapOf("co2Ppm" to 1234.0)
        assertFalse(m.write(listOf(0xEC,5),0));assertTrue(m.write(listOf(0x21,0xB1),0))
        m.write(listOf(0xE4,0xB8),4_998_000);assertEquals(Scd41Model.words(listOf(0)),m.read(3,4_999_000))
        m.write(listOf(0xE4,0xB8),5_000_000);assertTrue(m.read(3,5_000_500).isEmpty());assertEquals(Scd41Model.words(listOf(1)),m.read(3,5_001_000))
        m.write(listOf(0xEC,5),5_002_000);val bytes=m.read(9,5_003_000)
        assertEquals(1234,bytes[0]*256+bytes[1]);repeat(3){i->assertEquals(Scd41Model.crc(bytes.subList(i*3,i*3+2)),bytes[i*3+2])}
        m.write(listOf(0xEC,5),5_004_000);assertTrue(m.read(9,5_005_000).isEmpty())
        m.write(listOf(0x3F,0x86),6_000_000);assertFalse(m.write(listOf(0x21,0xB1),6_499_999));assertTrue(m.write(listOf(0x21,0xB1),6_500_000))
        m.reset();assertFalse(m.write(listOf(0xEC,5),20_000_000))
    }
    @Test fun ina219ShuntBusCalibrationSignedCurrentAndTiming() {
        val m=Ina219Model();m.positive=12.01;m.negative=12.0
        fun read(reg:Int,t:Long):Int {assertTrue(m.write(listOf(reg),t));val b=m.read(2,t);return b[0]*256+b[1]}
        assertEquals(0,read(1,1063));assertEquals(1000,read(1,1064))
        assertEquals(0,read(4,1064));assertTrue(m.write(listOf(5,16,0),1064))
        assertEquals(1000,read(4,2128));assertEquals(3000,(read(2,2128) and 0xFFF8) shr 3)
        assertEquals(600,read(3,2128));assertEquals(0,read(2,2128) and 2)
        m.positive=11.99;assertEquals(-1000,read(4,3192).toShort().toInt())
        assertFalse(m.write(listOf(0,0,0),4000)) // Unsupported timing/mode is not silently accepted.
        assertTrue(m.write(listOf(0,0x39,0x98),4000));m.positive=12.02
        assertEquals(-1000,read(4,9000).toShort().toInt())
        m.reset(10000);assertEquals(0,read(5,10000));assertEquals(0x399F,read(0,10000))
    }
    @Test fun conversionLatchesRetainTheSampleAcrossAStimulusChange() {
        val gas=Scd41Model();gas.stimuli=mapOf("co2Ppm" to 800.0);gas.write(listOf(0x21,0xB1),0)
        gas.advance(5_000_000);gas.stimuli=mapOf("co2Ppm" to 2000.0)
        gas.write(listOf(0xEC,5),6_000_000);val a=gas.read(9,6_001_000)
        assertEquals(800,a[0]*256+a[1])
        gas.advance(10_000_000);gas.write(listOf(0xEC,5),10_000_000);val b=gas.read(9,10_001_000)
        assertEquals(2000,b[0]*256+b[1])
        val power=Ina219Model();power.positive=12.01;power.negative=12.0;power.advance(1064)
        power.positive=12.02;power.write(listOf(1),1065);val old=power.read(2,1065)
        assertEquals(1000,old[0]*256+old[1])
        power.advance(2128);power.write(listOf(1),2128);val fresh=power.read(2,2128)
        assertEquals(2000,fresh[0]*256+fresh[1])
    }
    @Test fun manufacturerPressureAndCoTransfersRespectPhysicalUnits() {
        fun p(k:Kind,key:String,v:Double)=PlacedComponent(kind=k,reference="S",x=0f,y=0f,parameters=mapOf("temperatureC" to 25.0,key to v))
        assertEquals(.4,iotSensorOutputVoltage(p(Kind.ZE07_CO,"coPpm",0.0),5.0)!!,1e-9)
        assertEquals(2.0,iotSensorOutputVoltage(p(Kind.ZE07_CO,"coPpm",500.0),12.0)!!,1e-9)
        assertNull(iotSensorOutputVoltage(p(Kind.ZE07_CO,"coPpm",501.0),5.0))
        assertEquals(.2,iotSensorOutputVoltage(p(Kind.MPX5010_PRESSURE,"pressureKpa",0.0),5.0)!!,1e-9)
        assertEquals(4.7,iotSensorOutputVoltage(p(Kind.MPX5010_PRESSURE,"pressureKpa",10.0),5.0)!!,1e-9)
        assertNull(iotSensorOutputVoltage(p(Kind.MPX5010_PRESSURE,"pressureKpa",Double.NaN),5.0))
    }
    @Test fun firmwareMathAndBitwiseOperatorsUseCPrecedence() {
        val c=ChemistryProjects.circuit(Kind.ARDUINO_UNO,8)
        val b=FirmwareBoard(c,"B1");val s=FirmwareSession(ArduinoSubset.compile("void setup(){double a=sqrt(9);double b=abs(-4);double c=atan2(1,0);int d=1|2^3&6;int e=2+3*4;}void loop(){delay(1000);}",b),b)
        assertNull(s.advance(1000).error);val v=s.variables()
        assertEquals(3.0,v.getValue("a").toDouble(),0.0);assertEquals(4.0,v.getValue("b").toDouble(),0.0)
        assertEquals(Math.PI/2,v.getValue("c").toDouble(),1e-9);assertEquals(1.0,v.getValue("d").toDouble(),0.0);assertEquals(14.0,v.getValue("e").toDouble(),0.0)
    }
}
