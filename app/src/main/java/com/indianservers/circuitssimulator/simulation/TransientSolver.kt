package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import com.indianservers.circuitssimulator.simulation.digital.DigitalEvent
import kotlin.math.abs
import kotlin.math.max

data class SignalSample(val timeSeconds: Double, val value: Double)
data class SignalTrace(val channelId: String, val quantity: String, val unit: String, val samples: List<SignalSample>)
data class TransientFrame(val timeSeconds: Double, val nodeVoltages: Map<TerminalRef, Double>,
                          val readings: Map<String, Reading>,
                          val digitalStates:Map<TerminalRef,LogicState> = emptyMap()) {
    fun asDcResult() = DcResult(nodeVoltages,readings,digitalStates=digitalStates)
}
data class TransientResult(val frames: List<TransientFrame> = emptyList(),
                           val traces: Map<String, SignalTrace> = emptyMap(), val error: String? = null,
                           val checkpoint:TransientCheckpoint? = null) {
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

/** Electrical memory retained between live simulation slices. */
data class TransientCheckpoint(
    val timeSeconds:Double,
    val capacitorVoltages:Map<String,Double>,
    val capacitorCurrents:Map<String,Double>,
    val inductorCurrents:Map<String,Double>,
    val inductorVoltages:Map<String,Double>,
    val resistorStress:Map<String,Double>,
    val fuseStress:Map<String,Double>,
    val temperatures:Map<String,Double>,
    val failedOpen:Set<String>,
    val guess:List<Double>,
    val motorCurrents:Map<String,Double> = emptyMap(),
    val motorSpeeds:Map<String,Double> = emptyMap(),
    val transformerCurrents:Map<String,Pair<Double,Double>> = emptyMap(),
    val digitalNodes:Map<String,LogicState> = emptyMap(),
    val digitalBits:Map<String,LogicState> = emptyMap(),
    val digitalCounters:Map<String,Int> = emptyMap(),
    val digitalClockInputs:Map<String,LogicState> = emptyMap(),
    val digitalTimerReset:Map<String,LogicState> = emptyMap(),
    val digitalGrounded:Set<String> = emptySet(),
    val digitalEvents:List<DigitalEvent> = emptyList(),
    val coilCurrents:Map<String,Double> = emptyMap(),
    val thyristorLatched:Set<String> = emptySet(),
    val digitalShiftRegisters:Map<String,Int> = emptyMap(),
    val digitalLatchRegisters:Map<String,Int> = emptyMap()
)

fun appendTransient(previous:TransientResult?,next:TransientResult,maxFrames:Int=2000):TransientResult {
    if(previous==null) return next
    val frames=(previous.frames+next.frames).takeLast(maxFrames)
    val traces=(previous.traces.keys+next.traces.keys).associateWith { id ->
        val old=previous.traces[id]
        val fresh=next.traces[id]
        val start=old?.samples?.lastOrNull()?.timeSeconds ?: Double.NEGATIVE_INFINITY
        val samples=(old?.samples.orEmpty()+fresh?.samples.orEmpty().filter { it.timeSeconds>start+1e-12 })
            .takeLast(maxFrames)
        (fresh ?: old)!!.copy(samples=samples)
    }
    return TransientResult(frames,traces,next.error,next.checkpoint)
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
                 initialInductorCurrents: Map<String,Double> = emptyMap(),
                 externalDrivesAt: (Double)->List<ExternalDrive> = { emptyList() },
                 nextEventAfter: (Double)->Double? = { null },
                 digitalAt: (Double,Map<TerminalRef,Double>)->Map<TerminalRef,LogicState> = { _,_ -> emptyMap() },
                 checkpoint:TransientCheckpoint? = null): TransientResult {
        require(durationSeconds > 0 && stepSeconds > 0)
        val parts=circuit.components.map { it.electricalPrimitive() }
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
        val vcvs=parts.filter { it.kind==Kind.VCVS }
        val currentControlled=parts.filter { it.kind==Kind.CCCS || it.kind==Kind.CCVS }
        val ccvs=currentControlled.filter { it.kind==Kind.CCVS }
        val sourceWaves=sources.map(::sourceWaveform)
        if(sources.any { index(it,0)==index(it,1) && it.value("internalResistance")<=0.0 }) return TransientResult(error="This voltage source is short-circuited.")
        val size=roots.size+sources.size+opAmps.size+vcvs.size+
            currentControlled.size+ccvs.size
        if(size==0) return TransientResult(error="Add a connected circuit to simulate.")
        val capHistory=parts.filter { it.kind==Kind.CAPACITOR || it.kind==Kind.ELECTROLYTIC }
            .associate { it.id to (checkpoint?.capacitorVoltages?.get(it.id) ?:
                initialCapacitorVoltages[it.id] ?: it.value("initialVoltage")) }.toMutableMap()
        val inductorHistory=parts.filter { it.kind==Kind.INDUCTOR }
            .associate { it.id to (checkpoint?.inductorCurrents?.get(it.id) ?:
                initialInductorCurrents[it.id] ?: it.value("initialCurrent")) }.toMutableMap()
        val capCurrentHistory=capHistory.keys.associateWith { checkpoint?.capacitorCurrents?.get(it) ?: 0.0 }.toMutableMap()
        val inductorVoltageHistory=inductorHistory.keys.associateWith { checkpoint?.inductorVoltages?.get(it) ?: 0.0 }.toMutableMap()
        val motorCurrent=parts.filter { it.kind==Kind.DC_MOTOR }.associate {
            it.id to (checkpoint?.motorCurrents?.get(it.id) ?: 0.0) }.toMutableMap()
        val motorSpeed=motorCurrent.keys.associateWith { checkpoint?.motorSpeeds?.get(it) ?: 0.0 }.toMutableMap()
        val coilCurrent=parts.filter { it.kind in setOf(Kind.SPEAKER,Kind.SOLENOID) }
            .associate { it.id to (checkpoint?.coilCurrents?.get(it.id) ?: 0.0) }.toMutableMap()
        val thyristorLatched=checkpoint?.thyristorLatched?.toMutableSet() ?: mutableSetOf()
        val transformerCurrent=parts.filter { it.kind==Kind.TRANSFORMER }.associate {
            it.id to (checkpoint?.transformerCurrents?.get(it.id) ?: (0.0 to 0.0)) }.toMutableMap()
        val resistorStress=parts.filter { it.kind==Kind.RESISTOR }
            .associate { it.id to (checkpoint?.resistorStress?.get(it.id) ?: 0.0) }.toMutableMap()
        val fuseStress=parts.filter { it.kind==Kind.FUSE }
            .associate { it.id to (checkpoint?.fuseStress?.get(it.id) ?: 0.0) }.toMutableMap()
        val temperature=parts.mapNotNull { p ->
            val data=ComponentRegistry.definitions.getValue(p.kind).thermal
            if(data.junctionToAmbientCPerW!=null && data.thermalCapacitanceJPerC!=null)
                p.id to (checkpoint?.temperatures?.get(p.id) ?: data.ambientC) else null
        }.toMap().toMutableMap()
        val failedOpen=checkpoint?.failedOpen?.toMutableSet() ?: mutableSetOf()
        val frames=ArrayList<TransientFrame>()
        val startTime=checkpoint?.timeSeconds ?: 0.0
        val stopTime=startTime+durationSeconds
        val traceData=(capHistory.keys + sources.filter { it.kind==Kind.FUNCTION_GENERATOR }.map { it.id } + temperature.keys)
            .associateWith { id -> mutableListOf(SignalSample(startTime,
                capHistory[id] ?: temperature[id] ?: sourceWaveform(sources.first { it.id==id }).valueAt(startTime))) }
        var guess=checkpoint?.guess?.takeIf { it.size==size }?.toDoubleArray() ?: DoubleArray(size)
        val controller=TimeStepController(stepSeconds)
        var time=startTime
        while(time<stopTime-1e-12 && frames.size<10000) {
            val candidate=controller.next(time,stopTime,sourceWaves)
            val nextDigital=nextEventAfter(time)
            val dt=if(nextDigital!=null && nextDigital>time+1e-12)
                minOf(candidate,nextDigital-time) else candidate
            val nextTime=time+dt
            val externalDrives=externalDrivesAt(nextTime)
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
                    if(p.kind.isDigital) {
                        (0 until p.terminalCount).forEach { pin ->
                            val node=index(p,pin)
                            if(node>=0) matrix[node][node]+=1e-9
                        }
                        if(p.kind.isBoard) boardDrives(p,circuit) { pin ->
                            index(p,pin).let { if(it>=0) guess[it] else 0.0 }
                        }.forEach { (pin,voltage,resistance) ->
                            val node=index(p,pin)
                            if(node>=0) { matrix[node][node]+=1.0/resistance;rhs[node]+=voltage/resistance }
                        }
                        return@forEach
                    }
                    if(p.kind in ElectromechanicalParts.pinNames) {
                        stampElectromechanical(p,IntArray(p.terminalCount) { index(p,it) },guess,
                            matrix,rhs,dt,coilCurrent[p.id] ?: 0.0)
                        return@forEach
                    }
                    if(p.kind in PhaseFiveParts.pinNames) {
                        stampPhaseFive(p,IntArray(p.terminalCount) { index(p,it) },guess,matrix,rhs)
                        return@forEach
                    }
                    if(p.kind in setOf(Kind.SCR,Kind.TRIAC)) {
                        stampThyristor(p,IntArray(3) { index(p,it) },guess,matrix,rhs,
                            p.id in thyristorLatched)
                        return@forEach
                    }
                    if(p.kind in setOf(Kind.LM358,Kind.LM741,Kind.COMPARATOR,
                            Kind.REGULATOR_7805,Kind.REGULATOR_LM317,Kind.L293D,Kind.ULN2003)) {
                        stampAnalogIc(p,IntArray(p.terminalCount) { index(p,it) },guess,matrix,rhs)
                        return@forEach
                    }
                    when(p.kind) {
                        Kind.DC_CURRENT_SOURCE -> {
                            val n1=index(p,0);val n2=index(p,1);val current=p.value("current")
                            if(n1>=0) rhs[n1]-=current
                            if(n2>=0) rhs[n2]+=current
                            return@forEach
                        }
                        Kind.VCCS -> { stampVccs(matrix,IntArray(4) { index(p,it) },p.value("gain"));return@forEach }
                        Kind.VCVS -> return@forEach
                        Kind.CCCS,Kind.CCVS -> return@forEach
                        Kind.TRANSFORMER -> {
                            val prior=transformerCurrent.getValue(p.id)
                            stampTransformer(matrix,rhs,IntArray(4) { index(p,it) },p,dt,prior.first,prior.second)
                            return@forEach
                        }
                        Kind.RELAY -> {
                            stamp(index(p,0),index(p,1),1.0/p.value("coilResistance"))
                            val coil=abs((if(index(p,0)>=0) guess[index(p,0)] else 0.0)-
                                (if(index(p,1)>=0) guess[index(p,1)] else 0.0))
                            stamp(index(p,2),index(p,if(coil>=p.value("pullInVoltage")) 3 else 4),1000.0)
                            return@forEach
                        }
                        Kind.SPDT_SWITCH -> {
                            stamp(index(p,0),index(p,if(p.closed) 1 else 2),1000.0)
                            return@forEach
                        }
                        Kind.RGB_LED,Kind.SEVEN_SEGMENT -> {
                            val cathode=p.terminalCount-1
                            for(pin in 0 until cathode) {
                                val n1=index(p,pin);val n2=index(p,cathode)
                                val voltage=(if(n1>=0) guess[n1] else 0.0)-
                                    (if(n2>=0) guess[n2] else 0.0)
                                val vf=if(p.kind==Kind.RGB_LED) listOf(1.8,2.2,3.0)[pin] else 1.9
                                stampBranch(matrix,rhs,n1,n2,
                                    ledBranchAt(voltage,vf,p.value("seriesResistance")))
                            }
                            return@forEach
                        }
                        else -> Unit
                    }
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
                        Kind.SWITCH,Kind.PUSH_BUTTON -> if(p.closed) stamp(n1,n2,1000.0)
                        Kind.DC_MOTOR -> {
                            val model=motorCompanion(p,dt,motorCurrent.getValue(p.id),motorSpeed.getValue(p.id))
                            stamp(n1,n2,model.conductance,model.ieq)
                        }
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
                        Kind.ZENER -> {
                            val raw=(if(n1>=0) guess[n1] else 0.0)-(if(n2>=0) guess[n2] else 0.0)
                            val model=zenerAt(p,raw)
                            stamp(n1,n2,model.conductance,model.ieq)
                        }
                        Kind.SCHOTTKY_DIODE,Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED -> {
                            val raw=(if(n1>=0) guess[n1] else 0.0)-(if(n2>=0) guess[n2] else 0.0)
                            stampBranch(matrix,rhs,n1,n2,ledBranchAt(raw,p.value("forwardVoltage"),
                                if(p.kind==Kind.SCHOTTKY_DIODE) p.value("dynamicResistance") else p.value("seriesResistance")))
                        }
                        Kind.PHOTODIODE -> stamp(n1,n2,1.0/p.value("darkResistance"),
                            -p.value("responsivity")*p.value("illuminanceLux")/100.0)
                        else -> Unit
                    }
                }
                externalDrives.forEach { drive ->
                    val node=nodes[root(drive.terminal)] ?: return@forEach
                    val conductance=1.0/drive.resistanceOhms
                    matrix[node][node]+=conductance
                    rhs[node]+=conductance*drive.voltage
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
                vcvs.forEachIndexed { k,p -> stampVcvs(matrix,IntArray(4) { index(p,it) },
                    roots.size+sources.size+opAmps.size+k,p.value("gain")) }
                currentControlled.forEachIndexed { k,p ->
                    val outputRow=if(p.kind==Kind.CCVS) roots.size+sources.size+opAmps.size+
                        vcvs.size+currentControlled.size+ccvs.indexOf(p) else null
                    stampCurrentControlled(matrix,IntArray(4) { index(p,it) },
                        roots.size+sources.size+opAmps.size+vcvs.size+k,outputRow,p.value("gain"))
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
                if(p.kind in ElectromechanicalParts.pinNames)
                    return@associate p.id to electromechanicalReading(p,volts,dt,
                        coilCurrent[p.id] ?: 0.0)
                if(p.kind in PhaseFiveParts.pinNames)
                    return@associate p.id to phaseFiveReading(p,volts)
                if(p.kind in setOf(Kind.SCR,Kind.TRIAC))
                    return@associate p.id to thyristorReading(p,volts,p.id in thyristorLatched)
                if(p.kind in setOf(Kind.LM358,Kind.LM741,Kind.COMPARATOR,
                        Kind.REGULATOR_7805,Kind.REGULATOR_LM317,Kind.L293D,Kind.ULN2003))
                    return@associate p.id to analogIcReading(p,volts)
                val v=when(p.kind) {
                    Kind.VCCS,Kind.VCVS,Kind.CCCS,Kind.CCVS -> volts.getValue(TerminalRef(p.id,2))-
                        volts.getValue(TerminalRef(p.id,3))
                    Kind.RGB_LED,Kind.SEVEN_SEGMENT -> volts.getValue(TerminalRef(p.id,0))-
                        volts.getValue(TerminalRef(p.id,p.terminalCount-1))
                    Kind.SPDT_SWITCH -> volts.getValue(TerminalRef(p.id,0))-
                        volts.getValue(TerminalRef(p.id,if(p.closed) 1 else 2))
                    Kind.IDEAL_OPAMP,Kind.OPAMP -> volts.getValue(TerminalRef(p.id,2))
                    else -> volts.getValue(TerminalRef(p.id,0))-
                        (volts[TerminalRef(p.id,if(p.terminalCount==3) 2 else 1)] ?: 0.0)
                }
                val i=when(p.kind) {
                    Kind.BATTERY,Kind.SOURCE,Kind.FUNCTION_GENERATOR -> values[roots.size+sources.indexOf(p)]
                    Kind.DC_CURRENT_SOURCE -> p.value("current")
                    Kind.IDEAL_OPAMP,Kind.OPAMP -> values[roots.size+sources.size+opAmps.indexOf(p)]
                    Kind.NPN_BJT,Kind.PNP_BJT,Kind.DARLINGTON,Kind.NMOS,Kind.PMOS -> threeTerminalCurrents(p,
                        DoubleArray(3) { volts[TerminalRef(p.id,it)] ?: 0.0 })[0]
                    Kind.POTENTIOMETER -> (volts.getValue(TerminalRef(p.id,0))-
                        volts.getValue(TerminalRef(p.id,1)))/potentiometerSegments(p).first
                    Kind.RESISTOR,Kind.LAMP,Kind.FUSE -> if(p.id in failedOpen) 0.0 else v/max(p.value("resistance"),1e-6)
                    Kind.LDR,Kind.THERMISTOR -> v/sensorResistanceOhms(p)
                    Kind.SWITCH,Kind.PUSH_BUTTON -> if(p.closed) v*1000.0 else 0.0
                    Kind.SPDT_SWITCH -> (volts.getValue(TerminalRef(p.id,0))-
                        volts.getValue(TerminalRef(p.id,if(p.closed) 1 else 2)))*1000.0
                    Kind.RELAY -> (volts.getValue(TerminalRef(p.id,0))-
                        volts.getValue(TerminalRef(p.id,1)))/p.value("coilResistance")
                    Kind.VCVS -> values[roots.size+sources.size+opAmps.size+vcvs.indexOf(p)]
                    Kind.VCCS -> p.value("gain")*(volts.getValue(TerminalRef(p.id,0))-
                        volts.getValue(TerminalRef(p.id,1)))
                    Kind.CCCS -> p.value("gain")*values[roots.size+sources.size+opAmps.size+
                        vcvs.size+currentControlled.indexOf(p)]
                    Kind.CCVS -> values[roots.size+sources.size+opAmps.size+vcvs.size+
                        currentControlled.size+ccvs.indexOf(p)]
                    Kind.TRANSFORMER -> {
                        val prior=transformerCurrent.getValue(p.id)
                        transformerCurrents(p,dt,
                            volts.getValue(TerminalRef(p.id,0))-volts.getValue(TerminalRef(p.id,1)),
                            volts.getValue(TerminalRef(p.id,2))-volts.getValue(TerminalRef(p.id,3)),
                            prior.first,prior.second).first
                    }
                    Kind.RGB_LED,Kind.SEVEN_SEGMENT -> (0 until p.terminalCount-1).sumOf { pin ->
                        val voltage=volts.getValue(TerminalRef(p.id,pin))-
                            volts.getValue(TerminalRef(p.id,p.terminalCount-1))
                        val vf=if(p.kind==Kind.RGB_LED) listOf(1.8,2.2,3.0)[pin] else 1.9
                        ledBranchAt(voltage,vf,p.value("seriesResistance")).current(voltage)
                    }
                    Kind.DC_MOTOR -> motorCompanion(p,dt,motorCurrent.getValue(p.id),
                        motorSpeed.getValue(p.id)).current(v)
                    Kind.ZENER -> zenerAt(p,v).current(v)
                    Kind.SCHOTTKY_DIODE,Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED ->
                        ledBranchAt(v,p.value("forwardVoltage"),if(p.kind==Kind.SCHOTTKY_DIODE)
                            p.value("dynamicResistance") else p.value("seriesResistance")).current(v)
                    Kind.PHOTODIODE -> v/p.value("darkResistance")-
                        p.value("responsivity")*p.value("illuminanceLux")/100.0
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
                } else if(p.terminalCount==3 && !isOpAmp(p) && !p.kind.isDigital &&
                    !isExpandedMultiport(p)) {
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
                    Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED -> if(abs(i)>p.value("maxCurrent")*1.25)
                        Health.WARNING else Health.NORMAL
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
            motorCurrent.keys.forEach { id ->
                val p=parts.first { it.id==id }
                val amps=readings.getValue(id).current
                val old=motorSpeed.getValue(id)
                motorSpeed[id]=(old+dt*p.value("motorConstant")*amps/p.value("inertia"))/
                    (1.0+dt*p.value("friction")/p.value("inertia"))
                motorCurrent[id]=amps
            }
            coilCurrent.keys.forEach { id -> coilCurrent[id]=readings.getValue(id).current }
            parts.filter { it.kind in setOf(Kind.SCR,Kind.TRIAC) }.forEach { part ->
                val pins=DoubleArray(3) { volts[TerminalRef(part.id,it)] ?: 0.0 }
                if(kotlin.math.abs(readings.getValue(part.id).current)<part.value("holdingCurrent"))
                    thyristorLatched.remove(part.id)
                else if(thyristorTriggered(part,pins)) thyristorLatched.add(part.id)
            }
            transformerCurrent.keys.forEach { id ->
                val p=parts.first { it.id==id }
                val prior=transformerCurrent.getValue(id)
                transformerCurrent[id]=transformerCurrents(p,dt,
                    volts.getValue(TerminalRef(id,0))-volts.getValue(TerminalRef(id,1)),
                    volts.getValue(TerminalRef(id,2))-volts.getValue(TerminalRef(id,3)),
                    prior.first,prior.second)
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
            frames+=TransientFrame(nextTime,volts,readings,digitalAt(nextTime,volts))
            time=nextTime
        }
        if(time<stopTime-1e-12) return TransientResult(error="Transient step limit reached; use a shorter interval.")
        val traces=traceData.mapValues { (id,samples) -> SignalTrace(parts.first { it.id==id }.reference,
            if(id in temperature) "Temperature" else "Voltage",if(id in temperature) "°C" else "V",samples) }
        return TransientResult(frames,traces,checkpoint=TransientCheckpoint(time,capHistory.toMap(),
            capCurrentHistory.toMap(),inductorHistory.toMap(),inductorVoltageHistory.toMap(),
            resistorStress.toMap(),fuseStress.toMap(),temperature.toMap(),failedOpen.toSet(),guess.toList(),
            motorCurrent.toMap(),motorSpeed.toMap(),transformerCurrent.toMap(),
            coilCurrents=coilCurrent.toMap(),thyristorLatched=thyristorLatched.toSet()))
    }

}
