package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*
import kotlin.math.*

data class Complex(val re: Double, val im: Double = 0.0) {
    operator fun plus(b: Complex) = Complex(re + b.re, im + b.im)
    operator fun minus(b: Complex) = Complex(re - b.re, im - b.im)
    operator fun times(b: Complex) = Complex(re*b.re-im*b.im, re*b.im+im*b.re)
    operator fun div(b: Complex): Complex {
        val d=b.re*b.re+b.im*b.im
        return Complex((re*b.re+im*b.im)/d,(im*b.re-re*b.im)/d)
    }
    val magnitude get() = hypot(re,im)
    val phaseDegrees get() = atan2(im,re)*180.0/PI
    companion object { val ZERO=Complex(0.0); val ONE=Complex(1.0) }
}

data class AcPoint(val frequencyHz: Double, val nodeVoltages: Map<TerminalRef,Complex>,
                   val branchCurrents: Map<String,Complex>) {
    fun gain(input:TerminalRef,output:TerminalRef):Complex? {
        val vin=nodeVoltages[input] ?: return null
        val vout=nodeVoltages[output] ?: return null
        return if(vin.magnitude<1e-15) null else vout/vin
    }
}
data class AcSweep(val points:List<AcPoint>,val error:String?=null)
enum class SweepScale { LINEAR, LOGARITHMIC }

/** Complex modified nodal analysis. Sources are unit small-signal phasors; DC batteries are AC ground. */
class AcSolver {
    private val unsupported=setOf(Kind.NPN_BJT,Kind.PNP_BJT,Kind.NMOS,Kind.PMOS)
    private fun bias(circuit:Circuit):DcResult? = if(circuit.components.any {
        isOpAmp(it) || it.kind==Kind.LED || it.kind==Kind.DIODE || it.kind==Kind.RECTIFIER
    }) DcSolver().solve(circuit) else null

    fun sweep(circuit:Circuit,startHz:Double,stopHz:Double,count:Int,scale:SweepScale):AcSweep {
        if(!startHz.isFinite() || !stopHz.isFinite() || startHz<=0 || stopHz<startHz || count !in 2..2000)
            return AcSweep(emptyList(),"Invalid frequency sweep settings.")
        if(circuit.components.any { it.kind in unsupported })
            return AcSweep(emptyList(),"AC small-signal model is not available for BJT/MOSFET circuits yet.")
        val points=ArrayList<AcPoint>(count)
        val operating=bias(circuit)
        if(operating?.error!=null) return AcSweep(emptyList(),operating.error)
        for(k in 0 until count) {
            val f=if(scale==SweepScale.LOGARITHMIC)
                exp(ln(startHz)+(ln(stopHz)-ln(startHz))*k/(count-1))
                else startHz+(stopHz-startHz)*k/(count-1)
            val point=solveAt(circuit,f,operating) ?: return AcSweep(points,"AC circuit is floating or singular at ${"%.3g".format(f)} Hz.")
            points.add(point)
        }
        return AcSweep(points)
    }

    fun solve(circuit:Circuit,frequencyHz:Double):AcPoint? = solveAt(circuit,frequencyHz,bias(circuit))

    private fun solveAt(circuit:Circuit,frequencyHz:Double,bias:DcResult?):AcPoint? {
        if(!frequencyHz.isFinite() || frequencyHz<=0) return null
        if(circuit.components.any { it.kind in unsupported }) return null
        if(bias?.error!=null) return null
        val parts=circuit.components
        val ground=parts.firstOrNull { it.kind==Kind.GROUND } ?: return null
        val terminals=parts.flatMap { p -> (0 until p.terminalCount).map { TerminalRef(p.id,it) } }
        val parent=terminals.associateWith { it }.toMutableMap()
        fun root(t:TerminalRef):TerminalRef {
            val p=parent[t] ?: return t
            if(p==t) return t
            val r=root(p);parent[t]=r;return r
        }
        fun union(a:TerminalRef,b:TerminalRef) { if(a in parent && b in parent) parent[root(a)]=root(b) }
        circuit.electricalConnections().forEach { (a,b) -> union(a,b) }
        parts.filter { it.kind==Kind.GROUND }.forEach { union(TerminalRef(ground.id,0),TerminalRef(it.id,0)) }
        val grounded=root(TerminalRef(ground.id,0))
        val roots=terminals.map(::root).distinct().filter { it!=grounded }
        val node=roots.withIndex().associate { it.value to it.index }
        fun index(p:PlacedComponent,pin:Int)=node[root(TerminalRef(p.id,pin))] ?: -1
        val sources=parts.filter { it.kind==Kind.BATTERY || it.kind==Kind.SOURCE || it.kind==Kind.FUNCTION_GENERATOR }
        val opAmps=parts.filter(::isOpAmp)
        val n=roots.size+sources.size+opAmps.size
        if(n==0) return null
        val a=Array(n) { Array(n) { Complex.ZERO } }
        val b=Array(n) { Complex.ZERO }
        fun add(r:Int,c:Int,v:Complex) { if(r>=0 && c>=0) a[r][c]=a[r][c]+v }
        fun admittance(p:PlacedComponent,y:Complex) {
            val x=index(p,0);val z=index(p,1)
            add(x,x,y);add(z,z,y);add(x,z,Complex.ZERO-y);add(z,x,Complex.ZERO-y)
        }
        fun between(p:PlacedComponent,pinA:Int,pinB:Int,y:Complex) {
            val x=index(p,pinA);val z=index(p,pinB)
            add(x,x,y);add(z,z,y);add(x,z,Complex.ZERO-y);add(z,x,Complex.ZERO-y)
        }
        val omega=2.0*PI*frequencyHz
        parts.forEach { p -> when(p.kind) {
            Kind.RESISTOR,Kind.LAMP,Kind.FUSE -> admittance(p,Complex(1.0/p.value("resistance").coerceAtLeast(1e-9)))
            Kind.LDR,Kind.THERMISTOR -> admittance(p,Complex(1.0/sensorResistanceOhms(p)))
            Kind.POTENTIOMETER -> {
                val (upper,lower)=potentiometerSegments(p)
                between(p,0,1,Complex(1.0/upper))
                between(p,1,2,Complex(1.0/lower))
            }
            Kind.CAPACITOR,Kind.ELECTROLYTIC -> admittance(p,Complex(0.0,omega*p.value("capacitance")))
            Kind.INDUCTOR -> admittance(p,Complex(0.0,-1.0/(omega*p.value("inductance").coerceAtLeast(1e-12))))
            Kind.SWITCH -> if(p.closed) admittance(p,Complex(1000.0))
            Kind.AMMETER -> admittance(p,Complex(1000.0))
            Kind.VOLTMETER -> admittance(p,Complex(1e-9))
            Kind.LED,Kind.DIODE,Kind.RECTIFIER -> {
                val vd=bias?.readings?.get(p.id)?.voltage ?: 0.0
                admittance(p,Complex(diodeAt(p,vd).conductance))
            }
            else -> Unit
        } }
        sources.forEachIndexed { k,p ->
            val row=roots.size+k;val x=index(p,0);val z=index(p,1)
            add(x,row,Complex.ONE);add(row,x,Complex.ONE)
            add(z,row,Complex(-1.0));add(row,z,Complex(-1.0))
            add(row,row,Complex(-p.value("internalResistance")))
            b[row]=if(p.kind==Kind.FUNCTION_GENERATOR) {
                val phase=p.value("phase")*PI/180.0
                Complex(p.value("amplitude")*cos(phase),p.value("amplitude")*sin(phase))
            } else if(p.kind==Kind.SOURCE) Complex(p.value("acAmplitude")) else Complex.ZERO
        }
        opAmps.forEachIndexed { k,p ->
            val plus=index(p,0);val minus=index(p,1);val output=index(p,2)
            val row=roots.size+sources.size+k
            val differential=(bias?.nodeVoltages?.get(TerminalRef(p.id,0)) ?: 0.0)-
                (bias?.nodeVoltages?.get(TerminalRef(p.id,1)) ?: 0.0)
            val slope=opAmpAt(p,differential).slope
            val gain=if(p.kind==Kind.IDEAL_OPAMP || slope==0.0) Complex(slope) else {
                val pole=p.value("gbw")/p.value("gain")
                Complex(slope)/Complex(1.0,frequencyHz/pole)
            }
            add(output,row,Complex.ONE);add(row,output,Complex.ONE)
            add(row,plus,Complex.ZERO-gain);add(row,minus,gain)
            add(row,row,Complex(-p.value("outputResistance")))
        }
        // A small conductance anchors disconnected instrument pins without affecting measured signals.
        roots.indices.forEach { add(it,it,Complex(1e-12)) }
        val solved=complexSolve(a,b) ?: return null
        val voltages=terminals.associateWith { t -> node[root(t)]?.let { solved[it] } ?: Complex.ZERO }
        val currents=mutableMapOf<String,Complex>()
        sources.forEachIndexed { k,p -> currents[p.id]=solved[roots.size+k] }
        opAmps.forEachIndexed { k,p -> currents[p.id]=solved[roots.size+sources.size+k] }
        parts.filter { it.kind==Kind.POTENTIOMETER }.forEach { p ->
            currents[p.id]=(voltages.getValue(TerminalRef(p.id,0))-
                voltages.getValue(TerminalRef(p.id,1)))*Complex(1.0/potentiometerSegments(p).first)
        }
        parts.filter { it.terminalCount==2 && it !in sources }.forEach { p ->
            val y=when(p.kind) {
                Kind.RESISTOR,Kind.LAMP,Kind.FUSE -> Complex(1.0/p.value("resistance").coerceAtLeast(1e-9))
                Kind.LDR,Kind.THERMISTOR -> Complex(1.0/sensorResistanceOhms(p))
                Kind.CAPACITOR,Kind.ELECTROLYTIC -> Complex(0.0,omega*p.value("capacitance"))
                Kind.INDUCTOR -> Complex(0.0,-1.0/(omega*p.value("inductance").coerceAtLeast(1e-12)))
                Kind.SWITCH -> Complex(if(p.closed) 1000.0 else 0.0)
                Kind.AMMETER -> Complex(1000.0)
                Kind.VOLTMETER -> Complex(1e-9)
                Kind.LED,Kind.DIODE,Kind.RECTIFIER -> Complex(diodeAt(p,bias?.readings?.get(p.id)?.voltage ?: 0.0).conductance)
                else -> Complex.ZERO
            }
            currents[p.id]=(voltages.getValue(TerminalRef(p.id,0))-
                voltages.getValue(TerminalRef(p.id,1)))*y
        }
        return AcPoint(frequencyHz,voltages,currents)
    }
}

internal fun complexSolve(a:Array<Array<Complex>>,b:Array<Complex>):Array<Complex>? {
    val n=b.size
    for(k in 0 until n) {
        val pivot=(k until n).maxByOrNull { a[it][k].magnitude } ?: return null
        if(a[pivot][k].magnitude<1e-18) return null
        val row=a[k];a[k]=a[pivot];a[pivot]=row
        val rhs=b[k];b[k]=b[pivot];b[pivot]=rhs
        for(i in k+1 until n) {
            val factor=a[i][k]/a[k][k]
            for(j in k+1 until n) a[i][j]=a[i][j]-factor*a[k][j]
            a[i][k]=Complex.ZERO
            b[i]=b[i]-factor*b[k]
        }
    }
    val x=Array(n) { Complex.ZERO }
    for(i in n-1 downTo 0) {
        var sum=b[i]
        for(j in i+1 until n) sum=sum-a[i][j]*x[j]
        x[i]=sum/a[i][i]
    }
    return x.takeIf { values -> values.all { it.re.isFinite() && it.im.isFinite() } }
}
