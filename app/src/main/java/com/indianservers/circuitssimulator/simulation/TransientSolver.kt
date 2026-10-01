package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*
import kotlin.math.abs
import kotlin.math.max

data class SignalSample(val timeSeconds: Double, val value: Double)
data class SignalTrace(val channelId: String, val quantity: String, val unit: String, val samples: List<SignalSample>)
data class TransientFrame(val timeSeconds: Double, val nodeVoltages: Map<TerminalRef, Double>,
                          val readings: Map<String, Reading>) {
    fun asDcResult() = DcResult(nodeVoltages,readings)
}
data class TransientResult(val frames: List<TransientFrame> = emptyList(),
                           val traces: Map<String, SignalTrace> = emptyMap(), val error: String? = null) {
    fun frameAt(seconds: Double): TransientFrame? {
        if(frames.isEmpty()) return null
        val index=frames.binarySearch { it.timeSeconds.compareTo(seconds) }
        if(index>=0) return frames[index]
        val insertion=-index-1
        val near=frames.getOrNull(insertion)
        if(near!=null && kotlin.math.abs(near.timeSeconds-seconds)<1e-9) return near
        return frames[(insertion-1).coerceAtLeast(0)]
    }
}

data class CompanionModel(val conductance:Double,val historyCurrent:Double) {
    fun current(voltage:Double)=conductance*voltage+historyCurrent
}
interface IntegrationMethod {
    fun capacitor(capacitance:Double,stepSeconds:Double,previousVoltage:Double,
                  previousCurrent:Double=0.0):CompanionModel
    fun inductor(inductance:Double,stepSeconds:Double,previousCurrent:Double,
                 previousVoltage:Double=0.0):CompanionModel
}
object BackwardEuler:IntegrationMethod {
    override fun capacitor(capacitance:Double,stepSeconds:Double,previousVoltage:Double,
                           previousCurrent:Double):CompanionModel {
        val conductance=max(capacitance,1e-15)/stepSeconds
        return CompanionModel(conductance,-conductance*previousVoltage)
    }
    override fun inductor(inductance:Double,stepSeconds:Double,previousCurrent:Double,
                          previousVoltage:Double):CompanionModel =
        CompanionModel(stepSeconds/max(inductance,1e-12),previousCurrent)
}
object TrapezoidalIntegration:IntegrationMethod {
    override fun capacitor(capacitance:Double,stepSeconds:Double,previousVoltage:Double,
                           previousCurrent:Double):CompanionModel {
        val g=2.0*max(capacitance,1e-15)/stepSeconds
        return CompanionModel(g,-g*previousVoltage-previousCurrent)
    }
    override fun inductor(inductance:Double,stepSeconds:Double,previousCurrent:Double,
                          previousVoltage:Double):CompanionModel {
        val g=stepSeconds/(2.0*max(inductance,1e-12))
        return CompanionModel(g,previousCurrent+g*previousVoltage)
    }
}

/** Backward-Euler transient MNA. Capacitors and inductors use companion conductance/history sources. */
class TransientSolver(private val integration:IntegrationMethod=BackwardEuler) {
    fun simulate(circuit: Circuit, durationSeconds: Double = 1.0, stepSeconds: Double = .001,
                 initialCapacitorVoltages: Map<String,Double> = emptyMap(),
                 initialInductorCurrents: Map<String,Double> = emptyMap()): TransientResult {
        require(durationSeconds > 0 && stepSeconds > 0)
        val parts=circuit.components
        if(parts.any { isOpAmp(it) && it.value("upperRail")<=it.value("lowerRail") })
            return TransientResult(error="Op-amp supply rails are invalid.")
        val ground=parts.firstOrNull { it.kind==Kind.GROUND }
            ?: return TransientResult(error="Circuit has no reference ground.")
        val terminals=parts.flatMap { p -> (0 until p.terminalCount).map { TerminalRef(p.id,it) } }
        val parent=terminals.associateWith { it }.toMutableMap()
        fun root(t: TerminalRef): TerminalRef {
            val p=parent[t] ?: return t
            if(p==t) return t
            val r=root(p); parent[t]=r; return r
        }
        fun union(a: TerminalRef,b: TerminalRef) { if(a in parent && b in parent) parent[root(a)]=root(b) }
        circuit.electricalConnections().forEach { (a,b) -> union(a,b) }
        parts.filter { it.kind==Kind.GROUND }.forEach { union(TerminalRef(ground.id,0),TerminalRef(it.id,0)) }
        val groundRoot=root(TerminalRef(ground.id,0))
        val roots=terminals.map(::root).distinct().filter { it!=groundRoot }
        val nodes=roots.withIndex().associate { it.value to it.index }
        fun index(p: PlacedComponent,pin: Int): Int = nodes[root(TerminalRef(p.id,pin))] ?: -1
        val sources=parts.filter { it.kind==Kind.BATTERY || it.kind==Kind.SOURCE || it.kind==Kind.FUNCTION_GENERATOR }
        val opAmps=parts.filter(::isOpAmp)
        val sourceWaves=sources.map(::sourceWaveform)
        if(sources.any { index(it,0)==index(it,1) && it.value("internalResistance")<=0.0 }) return TransientResult(error="This voltage source is short-circuited.")
        val size=roots.size+sources.size+opAmps.size
        if(size==0) return TransientResult(error="Add a connected circuit to simulate.")
        val capHistory=parts.filter { it.kind==Kind.CAPACITOR || it.kind==Kind.ELECTROLYTIC }
            .associate { it.id to (initialCapacitorVoltages[it.id] ?: it.value("initialVoltage")) }.toMutableMap()
        val inductorHistory=parts.filter { it.kind==Kind.INDUCTOR }
            .associate { it.id to (initialInductorCurrents[it.id] ?: it.value("initialCurrent")) }.toMutableMap()
        val capCurrentHistory=capHistory.keys.associateWith { 0.0 }.toMutableMap()
        val inductorVoltageHistory=inductorHistory.keys.associateWith { 0.0 }.toMutableMap()
        val resistorStress=parts.filter { it.kind==Kind.RESISTOR }.associate { it.id to 0.0 }.toMutableMap()
        val fuseStress=parts.filter { it.kind==Kind.FUSE }.associate { it.id to 0.0 }.toMutableMap()
        val temperature=parts.mapNotNull { p ->
            val data=ComponentRegistry.definitions.getValue(p.kind).thermal
            if(data.junctionToAmbientCPerW!=null && data.thermalCapacitanceJPerC!=null)
                p.id to data.ambientC else null
        }.toMap().toMutableMap()
        val failedOpen=mutableSetOf<String>()
        val frames=ArrayList<TransientFrame>()
        val traceData=(capHistory.keys + sources.filter { it.kind==Kind.FUNCTION_GENERATOR }.map { it.id } + temperature.keys)
            .associateWith { id -> mutableListOf(SignalSample(0.0,
                capHistory[id] ?: temperature[id] ?: sourceWaveform(sources.first { it.id==id }).valueAt(0.0))) }
        var guess=DoubleArray(size)
        val controller=TimeStepController(stepSeconds)
        var time=0.0
        while(time<durationSeconds-1e-12 && frames.size<10000) {
            val dt=controller.next(time,durationSeconds,sourceWaves)
            val nextTime=time+dt
            var solved: DoubleArray?=null
            var converged=false
            for(iteration in 0 until circuit.settings.maxIterations) {
                val matrix=Array(size) { DoubleArray(size) }
                val rhs=DoubleArray(size)
                fun stamp(n1: Int,n2: Int,g: Double,ieq: Double=0.0) {
                    if(n1>=0) { matrix[n1][n1]+=g; rhs[n1]-=ieq }
                    if(n2>=0) { matrix[n2][n2]+=g; rhs[n2]+=ieq }
                    if(n1>=0 && n2>=0) { matrix[n1][n2]-=g;matrix[n2][n1]-=g }
                }
                parts.forEach { p ->
                    if(p.terminalCount<2) return@forEach
                    if(p.kind==Kind.POTENTIOMETER) {
                        val (upper,lower)=potentiometerSegments(p)
                        stamp(index(p,0),index(p,1),1.0/upper)
                        stamp(index(p,1),index(p,2),1.0/lower)
                        return@forEach
                    }
                    if(p.terminalCount==3 && !isOpAmp(p)) {
                        stampThreeTerminal(p,IntArray(3) { index(p,it) },guess,matrix,rhs)
                        return@forEach
                    }
                    val n1=index(p,0);val n2=index(p,1)
                    when(p.kind) {
                        Kind.RESISTOR,Kind.LAMP,Kind.FUSE -> if(p.id !in failedOpen)
                            stamp(n1,n2,1.0/max(p.value("resistance"),1e-6))
                        Kind.LDR,Kind.THERMISTOR -> stamp(n1,n2,1.0/sensorResistanceOhms(p))
                        Kind.SWITCH -> if(p.closed) stamp(n1,n2,1000.0)
                        Kind.AMMETER -> stamp(n1,n2,1000.0)
                        Kind.VOLTMETER -> stamp(n1,n2,1e-9)
                        Kind.CAPACITOR,Kind.ELECTROLYTIC -> {
                            val model=integration.capacitor(p.value("capacitance"),dt,
                                capHistory.getValue(p.id),capCurrentHistory.getValue(p.id))
                            stamp(n1,n2,model.conductance,model.historyCurrent)
                        }
                        Kind.INDUCTOR -> {
                            val model=integration.inductor(p.value("inductance"),dt,
                                inductorHistory.getValue(p.id),inductorVoltageHistory.getValue(p.id))
                            stamp(n1,n2,model.conductance,model.historyCurrent)
                        }
                        Kind.LED,Kind.DIODE,Kind.RECTIFIER -> {
                            val raw=(if(n1>=0) guess[n1] else 0.0)-(if(n2>=0) guess[n2] else 0.0)
                            val model=diodeAt(p,raw)
                            stamp(n1,n2,model.conductance,model.equivalentCurrent(raw))
                        }
                        else -> Unit
                    }
                }
                sources.forEachIndexed { k,p ->
                    val n1=index(p,0);val n2=index(p,1);val row=roots.size+k
                    if(n1>=0) { matrix[n1][row]+=1.0;matrix[row][n1]+=1.0 }
                    if(n2>=0) { matrix[n2][row]-=1.0;matrix[row][n2]-=1.0 }
                    matrix[row][row]-=p.value("internalResistance")
                    rhs[row]=sourceWaves[k].valueAt(nextTime)
                }
                opAmps.forEachIndexed { k,p ->
                    val plus=index(p,0);val minus=index(p,1);val output=index(p,2)
                    val row=roots.size+sources.size+k
                    val differential=(if(plus>=0) guess[plus] else 0.0)-(if(minus>=0) guess[minus] else 0.0)
                    val model=opAmpAt(p,differential)
                    if(output>=0) { matrix[output][row]+=1.0;matrix[row][output]+=1.0 }
                    if(plus>=0) matrix[row][plus]-=model.slope
                    if(minus>=0) matrix[row][minus]+=model.slope
                    matrix[row][row]-=p.value("outputResistance")
                    rhs[row]=model.output-model.slope*differential
                }
                for(n in roots.indices) matrix[n][n]+=1e-12
                val next=DenseLinearSolver.solve(matrix,rhs) ?: return TransientResult(error="Circuit is floating or singular at $time s.")
                if(next.any { !it.isFinite() }) return TransientResult(error="Numerical error in transient simulation.")
                val delta=next.indices.maxOf { abs(next[it]-guess[it]) }
                guess=DoubleArray(size) { .65*next[it]+.35*guess[it] }
                if(delta<circuit.settings.tolerance) { solved=next;converged=true;break }
            }
            if(!converged) return TransientResult(error="Transient simulation could not converge at $time s.")
            val values=solved!!
            guess=values
            val volts=terminals.associateWith { t -> nodes[root(t)]?.let { values[it] } ?: 0.0 }
            val readings=parts.associate { p ->
                val v=if(isOpAmp(p)) volts[TerminalRef(p.id,2)] ?: 0.0 else
                    volts[TerminalRef(p.id,0)]!!-(volts[TerminalRef(p.id,if(p.terminalCount==3) 2 else 1)] ?: 0.0)
                val i=when(p.kind) {
                    Kind.BATTERY,Kind.SOURCE,Kind.FUNCTION_GENERATOR -> values[roots.size+sources.indexOf(p)]
                    Kind.IDEAL_OPAMP,Kind.OPAMP -> values[roots.size+sources.size+opAmps.indexOf(p)]
                    Kind.NPN_BJT,Kind.PNP_BJT,Kind.NMOS,Kind.PMOS -> threeTerminalCurrents(p,
                        DoubleArray(3) { volts[TerminalRef(p.id,it)] ?: 0.0 })[0]
                    Kind.POTENTIOMETER -> (volts.getValue(TerminalRef(p.id,0))-
                        volts.getValue(TerminalRef(p.id,1)))/potentiometerSegments(p).first
                    Kind.RESISTOR,Kind.LAMP,Kind.FUSE -> if(p.id in failedOpen) 0.0 else v/max(p.value("resistance"),1e-6)
                    Kind.LDR,Kind.THERMISTOR -> v/sensorResistanceOhms(p)
                    Kind.SWITCH -> if(p.closed) v*1000.0 else 0.0
                    Kind.AMMETER -> v*1000.0
                    Kind.VOLTMETER -> v*1e-9
                    Kind.CAPACITOR,Kind.ELECTROLYTIC -> integration.capacitor(p.value("capacitance"),dt,
                        capHistory.getValue(p.id),capCurrentHistory.getValue(p.id)).current(v)
                    Kind.INDUCTOR -> integration.inductor(p.value("inductance"),dt,
                        inductorHistory.getValue(p.id),inductorVoltageHistory.getValue(p.id)).current(v)
                    Kind.LED,Kind.DIODE,Kind.RECTIFIER -> diodeAt(p,v).current
                    else -> 0.0
                }
                val power=if(p.kind==Kind.POTENTIOMETER) {
                    val (upper,lower)=potentiometerSegments(p)
                    val v0=volts.getValue(TerminalRef(p.id,0))
                    val v1=volts.getValue(TerminalRef(p.id,1))
                    val v2=volts.getValue(TerminalRef(p.id,2))
                    (v0-v1)*(v0-v1)/upper+(v1-v2)*(v1-v2)/lower
                } else if(p.terminalCount==3 && !isOpAmp(p)) {
                    val pins=DoubleArray(3) { volts[TerminalRef(p.id,it)] ?: 0.0 }
                    val amps=threeTerminalCurrents(p,pins)
                    abs((0..2).sumOf { pins[it]*amps[it] })
                } else abs(v*i)
                val health=when(p.kind) {
                    Kind.RESISTOR,Kind.LAMP -> if(p.id in failedOpen) Health.FAILED_OPEN
                        else if(power>p.value("rating")) Health.OVERLOAD else Health.NORMAL
                    Kind.FUSE -> if(p.id in failedOpen) Health.FAILED_OPEN
                        else if(abs(i)>p.value("currentRating")) Health.WARNING else Health.NORMAL
                    Kind.LED -> if(abs(i)>p.value("maxCurrent")*1.25) Health.WARNING else Health.NORMAL
                    Kind.DIODE -> if(i>.2 || v< -100.0) Health.WARNING else Health.NORMAL
                    Kind.RECTIFIER -> if(i>1.0 || v< -1000.0) Health.WARNING else Health.NORMAL
                    Kind.NPN_BJT,Kind.PNP_BJT -> if(abs(i)>.2 || abs(v)>40.0 || power>.625) Health.OVERLOAD else Health.NORMAL
                    Kind.NMOS -> if(abs(i)>.2 || abs(v)>60.0 || power>.4) Health.OVERLOAD else Health.NORMAL
                    Kind.CAPACITOR,Kind.ELECTROLYTIC -> if(abs(v)>p.value("maxVoltage") ||
                        (p.kind==Kind.ELECTROLYTIC && v<-.001)) Health.WARNING else Health.NORMAL
                    else -> Health.NORMAL
                }
                val thermal=ComponentRegistry.definitions.getValue(p.kind).thermal
                val prior=temperature[p.id]
                val actualTemperature=if(prior!=null) {
                    val resistance=thermal.junctionToAmbientCPerW!!
                    val capacitance=thermal.thermalCapacitanceJPerC!!
                    (prior+dt*(power+thermal.ambientC/resistance)/capacitance)/
                        (1.0+dt/(resistance*capacitance))
                } else thermal.junctionToAmbientCPerW?.let { thermal.ambientC+power*it }
                if(prior!=null && actualTemperature!=null) temperature[p.id]=actualTemperature
                val finalHealth=if(health==Health.NORMAL && actualTemperature!=null && actualTemperature>=125.0)
                    Health.THERMAL_WARNING else health
                p.id to Reading(v,i,power,finalHealth,actualTemperature)
            }
            capHistory.keys.forEach { id ->
                capCurrentHistory[id]=readings.getValue(id).current
                capHistory[id]=readings.getValue(id).voltage
                traceData.getValue(id).add(SignalSample(nextTime,capHistory.getValue(id)))
            }
            sources.filter { it.kind==Kind.FUNCTION_GENERATOR }.forEach { p ->
                traceData.getValue(p.id).add(SignalSample(nextTime,readings.getValue(p.id).voltage))
            }
            temperature.forEach { (id,celsius) -> traceData.getValue(id).add(SignalSample(nextTime,celsius)) }
            inductorHistory.keys.forEach { id ->
                inductorHistory[id]=readings.getValue(id).current
                inductorVoltageHistory[id]=readings.getValue(id).voltage
            }
            parts.filter { it.kind==Kind.RESISTOR && it.id !in failedOpen }.forEach { p ->
                val ratio=readings.getValue(p.id).power/p.value("rating").coerceAtLeast(1e-12)
                if(ratio>1.0) {
                    val damage=resistorStress.getValue(p.id)+(ratio-1.0)*(ratio-1.0)*dt
                    resistorStress[p.id]=damage
                    if(damage>=1.0) failedOpen+=p.id
                }
            }
            parts.filter { it.kind==Kind.FUSE && it.id !in failedOpen }.forEach { p ->
                val amps=readings.getValue(p.id).current
                if(abs(amps)>p.value("currentRating")) {
                    fuseStress[p.id]=fuseStress.getValue(p.id)+amps*amps*dt
                    if(fuseStress.getValue(p.id)>=p.value("i2t")) failedOpen+=p.id
                }
            }
            frames+=TransientFrame(nextTime,volts,readings)
            time=nextTime
        }
        if(time<durationSeconds-1e-12) return TransientResult(error="Transient step limit reached; use a shorter interval.")
        val traces=traceData.mapValues { (id,samples) -> SignalTrace(parts.first { it.id==id }.reference,
            if(id in temperature) "Temperature" else "Voltage",if(id in temperature) "°C" else "V",samples) }
        return TransientResult(frames,traces)
    }

}
