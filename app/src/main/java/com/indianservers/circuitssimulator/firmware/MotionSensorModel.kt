package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*
import kotlin.math.roundToInt

/** Nominal polling subset, not a FIFO/interrupt/filter or mechanical physics model. */
class MotionSensorModel(val kind:Kind) {
    private val profile=MotionSensorParts.profiles.getValue(kind)
    private val registers=IntArray(256)
    private val consumed=mutableMapOf<Int,Int>()
    var pointer=0; private set
    private var increment=false
    var stimuli:Map<String,Double> = emptyMap()
    var lastError:String?=null;private set
    init { reset() }
    fun reset() {
        registers.fill(0);consumed.clear();pointer=0;increment=false;lastError=null
        if(kind==Kind.ADXL345_ACCEL) { registers[0]=profile.identity;registers[0x2C]=0x0A }
        else { registers[0x0F]=profile.identity
            if(kind==Kind.LSM6DSOX_IMU) registers[0x12]=4 else registers[0x20]=7 }
    }
    private fun autoIncrement()=when(kind) {
        Kind.LSM6DSOX_IMU -> registers[0x12] and 4!=0
        Kind.ADXL345_ACCEL -> true
        else -> increment
    }
    private val writable get()=when(kind) {
        Kind.LSM6DSOX_IMU -> mapOf(0x10 to 0xFC,0x11 to 0xFE,0x12 to 0xC5)
        Kind.ADXL345_ACCEL -> mapOf(0x2C to 0x1F,0x2D to 0x0F,0x31 to 0x0F)
        Kind.LIS3DH_ACCEL -> mapOf(0x20 to 0xFF,0x23 to 0xF8)
        else -> mapOf(0x20 to 0xFF,0x23 to 0xF0)
    }
    private fun readable(address:Int)=address in writable || when(kind) {
        Kind.ADXL345_ACCEL -> address==0 || address==0x30 || address in 0x32..0x37
        Kind.LSM6DSOX_IMU -> address==0x0F || address==0x1E || address in 0x22..0x2D
        else -> address==0x0F || address==0x27 || address in 0x28..0x2D
    }
    fun write(bytes:List<Int>):Boolean {
        lastError=null
        if(bytes.isEmpty()) return true
        val sub=bytes.first() and 255
        val legacy=kind in setOf(Kind.L3GD20_GYRO,Kind.LIS3DH_ACCEL)
        val start=if(legacy) sub and 0x7F else sub
        val step=if(legacy) sub and 0x80!=0 else autoIncrement()
        var at=start
        // Validate the whole operation before changing any registers.
        val writes=bytes.drop(1).map { byte -> val address=at;if(step)at=(at+1) and 255;address to (byte and 255) }
        for((address,value) in writes) {
            val mask=writable[address]
            if(mask==null || value and mask.inv()!=0 ||
                (kind==Kind.LIS3DH_ACCEL && address==0x20 && value shr 4>9) ||
                (kind==Kind.LSM6DSOX_IMU && address in setOf(0x10,0x11) && value shr 4>10)) {
                lastError="Unsupported register/field write at 0x${address.toString(16)}"
                return false
            }
        }
        pointer=start;increment=step
        writes.forEach { (address,value) ->
            if(kind==Kind.LSM6DSOX_IMU && address==0x12 && value and 0x81!=0) reset()
            else registers[address]=value
        }
        // Changing configuration starts a new nominal snapshot (no startup timing modeled).
        if(writes.isNotEmpty()) consumed.clear()
        if(writes.isNotEmpty()) pointer=at
        return true
    }
    private fun stimulus(key:String,default:Double=0.0)=stimuli[key]?.takeIf { it.isFinite() } ?: default
    private fun bdu()=if(kind==Kind.LSM6DSOX_IMU) registers[0x12] and 0x40!=0
        else kind!=Kind.ADXL345_ACCEL && registers[0x23] and 0x80!=0
    private fun word(address:Int,value:Int,bigEndian:Boolean=false) {
        if(bdu() && consumed[address]?.let { it!=3 }==true) return
        registers[address]=if(bigEndian) (value shr 8) and 255 else value and 255
        registers[address+1]=if(bigEndian) value and 255 else (value shr 8) and 255
        consumed[address]=0
    }
    private fun signed(value:Double,sensitivity:Double,bits:Int=16,shift:Int=0):Int {
        val bound=1 shl (bits-1)
        return (value/sensitivity).roundToInt().coerceIn(-bound,bound-1) shl shift
    }
    private fun snapshot() {
        val axes=listOf("X","Y","Z")
        when(kind) {
            Kind.L3GD20_GYRO -> {
                val control=registers[0x20];val active=control and 8!=0
                val range=(registers[0x23] shr 4) and 3
                val scale=listOf(.00875,.0175,.07,.07)[range]
                val full=listOf(250.0,500.0,2000.0,2000.0)[range]
                val enabled=if(active)control and 7 else 0
                axes.forEachIndexed { i,axis -> word(0x28+i*2,if(enabled and (1 shl i)!=0)
                    signed(stimulus("gyro${axis}Dps").coerceIn(-full,full),scale) else 0,registers[0x23] and 0x40!=0) }
                registers[0x27]=enabled or if(enabled!=0)8 else 0
            }
            Kind.LIS3DH_ACCEL -> {
                val c=registers[0x20];val format=registers[0x23]
                val low=c and 8!=0;val high=!low && format and 8!=0
                val bits=if(low)8 else if(high)12 else 10
                val range=(format shr 4) and 3
                val base=listOf(.001,.002,.004,.012)[range]
                val scale=base*(if(low)16 else if(high)1 else 4)
                val full=listOf(2.0,4.0,8.0,16.0)[range]
                val enabled=if(c shr 4!=0)c and 7 else 0
                axes.forEachIndexed { i,axis -> word(0x28+i*2,if(enabled and (1 shl i)!=0)
                    signed(stimulus("accel${axis}G",if(axis=="Z")1.0 else 0.0).coerceIn(-full,full),scale,bits,16-bits) else 0,
                    high && format and 0x40!=0) }
                registers[0x27]=enabled or if(enabled!=0)8 else 0
            }
            Kind.ADXL345_ACCEL -> {
                val format=registers[0x31];val range=format and 3
                val fullResolution=format and 8!=0
                val bits=if(fullResolution)10+range else 10
                val scale=if(fullResolution).0039 else .0039*(1 shl range)
                val enabled=registers[0x2D] and 8!=0
                axes.forEachIndexed { i,axis -> word(0x32+i*2,if(enabled)
                    signed(stimulus("accel${axis}G",if(axis=="Z")1.0 else 0.0),scale,bits,if(format and 4!=0)16-bits else 0) else 0) }
                registers[0x30]=if(enabled)0x80 else 0
            }
            Kind.LSM6DSOX_IMU -> {
                val g=registers[0x11];val a=registers[0x10]
                val gyro=g shr 4!=0;val accel=a shr 4!=0
                val gs=if(g and 2!=0).004375 else listOf(.00875,.0175,.035,.07)[(g shr 2) and 3]
                val gf=if(g and 2!=0)125.0 else listOf(250.0,500.0,1000.0,2000.0)[(g shr 2) and 3]
                val acs=listOf(.000061,.000488,.000122,.000244)[(a shr 2) and 3]
                val af=listOf(2.0,16.0,4.0,8.0)[(a shr 2) and 3]
                axes.forEachIndexed { i,axis ->
                    word(0x22+i*2,if(gyro)signed(stimulus("gyro${axis}Dps").coerceIn(-gf,gf),gs) else 0)
                    word(0x28+i*2,if(accel)signed(stimulus("accel${axis}G",if(axis=="Z")1.0 else 0.0).coerceIn(-af,af),acs) else 0)
                }
                registers[0x1E]=(if(accel)1 else 0) or (if(gyro)2 else 0)
            }
            else -> error("Unknown motion sensor")
        }
    }
    fun read(count:Int):List<Int> {
        lastError=null
        if(count !in 0..4096) { lastError="Invalid read length";return emptyList() }
        var at=pointer;val step=autoIncrement()
        val addresses=List(count) { val current=at;if(step)at=(at+1) and 255;current }
        if(addresses.any { !readable(it) }) { lastError="Unsupported register read";return emptyList() }
        val sampleRegisters=if(kind==Kind.ADXL345_ACCEL)0x32..0x37 else if(kind==Kind.LSM6DSOX_IMU)0x22..0x2D else 0x28..0x2D
        if(addresses.any {it in sampleRegisters} && stimuli.any {(key,value)->(key.startsWith("accel") || key.startsWith("gyro")) && !value.isFinite()}) {
            lastError="Simulator fault injection: nonfinite motion input";return emptyList()
        }
        snapshot()
        val data=addresses.map { registers[it] }
        addresses.forEach { address ->
            val base=consumed.keys.firstOrNull { address==it || address==it+1 }
            if(base!=null) consumed[base]=(consumed[base] ?: 0) or if(address==base)1 else 2
        }
        pointer=at
        return data
    }
    fun inspect()=mapOf("Motion model" to "Nominal I2C polling subset", "WHO_AM_I / DEVID" to
        "0x${profile.identity.toString(16)}", "Register pointer" to "0x${pointer.toString(16)}")+
        stimuli.filterKeys { it.startsWith("gyro") || it.startsWith("accel") }.mapValues { it.value.toString() }+
        (lastError?.let { mapOf("Unsupported operation" to it) } ?: emptyMap())
}
