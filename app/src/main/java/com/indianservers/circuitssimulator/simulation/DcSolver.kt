package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.*
import kotlin.math.abs
import kotlin.math.max

enum class Health { NORMAL, WARNING, OVERLOAD, THERMAL_WARNING, FAILED_OPEN, FAILED_SHORT, FAILED }
data class Reading(val voltage: Double, val current: Double, val power: Double, val health: Health = Health.NORMAL,
                   val estimatedTemperatureC:Double?=null)
data class SolverProgress(val iterations:Int,val maximumUpdate:Double,val likelyComponentId:String?=null)
data class DcResult(val nodeVoltages: Map<TerminalRef, Double> = emptyMap(),
                    val readings: Map<String, Reading> = emptyMap(), val error: String? = null,
                    val converged: Boolean = true,val progress:SolverProgress?=null,
                    val digitalStates:Map<TerminalRef,com.indianservers.circuitssimulator.simulation.digital.LogicState> = emptyMap())

/** Modified nodal DC operating-point solver. Wires are topological unions, independent of pixels. */
class DcSolver {
    fun solve(circuit: Circuit, externalDrives: List<ExternalDrive> = emptyList()): DcResult {
        val parts = circuit.components.map { it.electricalPrimitive() }
        if(parts.isEmpty()) return DcResult()
        if(parts.any { isOpAmp(it) && it.value("upperRail")<=it.value("lowerRail") })
            return DcResult(error="Op-amp supply rails are invalid.")
        if (parts.none { it.kind == Kind.GROUND }) return DcResult(error = "Circuit has no reference ground.")
        val terminals = parts.flatMap { p -> (0 until p.terminalCount).map { TerminalRef(p.id, it) } }
        if(externalDrives.any { it.terminal !in terminals })
            return DcResult(error="A driven pin is not part of the circuit.")
        val parent = terminals.associateWith { it }.toMutableMap()
        fun root(t: TerminalRef): TerminalRef {
            val p = parent[t] ?: return t
            if (p == t) return t
            val r = root(p); parent[t] = r; return r
        }
        fun union(a: TerminalRef, b: TerminalRef) { if (a in parent && b in parent) parent[root(a)] = root(b) }
        circuit.electricalConnections().forEach { (a,b) -> union(a,b) }
        val ground = parts.first { it.kind == Kind.GROUND }
        parts.filter { it.kind == Kind.GROUND }.forEach { union(TerminalRef(ground.id, 0), TerminalRef(it.id, 0)) }
        val groundRoot = root(TerminalRef(ground.id, 0))
        val roots = terminals.map(::root).distinct().filter { it != groundRoot }
        val nodes = roots.withIndex().associate { it.value to it.index }
        fun idx(p: PlacedComponent, pin: Int): Int = nodes[root(TerminalRef(p.id, pin))] ?: -1
        val sources = parts.filter { it.kind == Kind.BATTERY || it.kind == Kind.SOURCE || it.kind == Kind.FUNCTION_GENERATOR }
        val opAmps=parts.filter(::isOpAmp)
        if (sources.any { idx(it, 0) == idx(it, 1) && it.value("internalResistance")<=0.0 }) return DcResult(error = "This voltage source is short-circuited.")
        val adjacency = mutableMapOf<TerminalRef, MutableSet<TerminalRef>>()
        parts.forEach { p ->
            if (p.kind.isDigital) {
                (0 until p.terminalCount).forEach { pin ->
                    val node=root(TerminalRef(p.id,pin))
                    adjacency.getOrPut(node) { mutableSetOf() }.add(groundRoot)
                    adjacency.getOrPut(groundRoot) { mutableSetOf() }.add(node)
                }
                return@forEach
            }
            if (p.terminalCount < 2 || p.kind == Kind.CAPACITOR || p.kind == Kind.ELECTROLYTIC ||
                ((p.kind == Kind.SWITCH || p.kind==Kind.PUSH_BUTTON) && !p.closed)) return@forEach
            val a = root(TerminalRef(p.id, 0)); val b = root(TerminalRef(p.id, 1))
            adjacency.getOrPut(a) { mutableSetOf() }.add(b)
            adjacency.getOrPut(b) { mutableSetOf() }.add(a)
            for(pin in 2 until p.terminalCount) {
                val c=root(TerminalRef(p.id,pin))
                adjacency.getOrPut(a) { mutableSetOf() }.add(c)
                adjacency.getOrPut(c) { mutableSetOf() }.add(a)
            }
        }
        externalDrives.forEach { drive ->
            val driven=root(drive.terminal)
            adjacency.getOrPut(driven) { mutableSetOf() }.add(groundRoot)
            adjacency.getOrPut(groundRoot) { mutableSetOf() }.add(driven)
        }
        val reachable = mutableSetOf(groundRoot)
        val queue = ArrayDeque<TerminalRef>(); queue.add(groundRoot)
        while (queue.isNotEmpty()) {
            adjacency[queue.removeFirst()].orEmpty().forEach { if (reachable.add(it)) queue.add(it) }
        }
        if (roots.any { it !in reachable }) return DcResult(error = "Circuit has a floating node. Connect it to ground.")
        val vcvs=parts.filter { it.kind==Kind.VCVS }
        val currentControlled=parts.filter { it.kind==Kind.CCCS || it.kind==Kind.CCVS }
        val ccvs=currentControlled.filter { it.kind==Kind.CCVS }
        val size = roots.size + sources.size + opAmps.size + vcvs.size +
            currentControlled.size + ccvs.size
        if (size == 0) return DcResult(error = "Add a connected circuit to simulate.")
        var guess = DoubleArray(size)
        var converged = false
        var lastUpdate=Double.POSITIVE_INFINITY
        var lastIndex=0
        var iterations=0
        for (iteration in 0 until circuit.settings.maxIterations) {
            iterations=iteration+1
            val a = Array(size) { DoubleArray(size) }
            val b = DoubleArray(size)
            fun conduct(n1: Int, n2: Int, g: Double, ieq: Double = 0.0) {
                if (n1 >= 0) { a[n1][n1] += g; b[n1] -= ieq }
                if (n2 >= 0) { a[n2][n2] += g; b[n2] += ieq }
                if (n1 >= 0 && n2 >= 0) { a[n1][n2] -= g; a[n2][n1] -= g }
            }
            parts.forEach { p ->
                if (p.kind == Kind.GROUND) return@forEach
                if (p.kind.isDigital) {
                    (0 until p.terminalCount).forEach { pin ->
                        val node=idx(p,pin)
                        if(node>=0) a[node][node]+=1e-9
                    }
                    if(p.kind.isBoard) boardDrives(p,circuit) { pin ->
                        idx(p,pin).let { if(it>=0) guess[it] else 0.0 }
                    }.forEach { (pin,voltage,resistance) ->
                        val node=idx(p,pin)
                        if(node>=0) { a[node][node]+=1.0/resistance;b[node]+=voltage/resistance }
                    }
                    return@forEach
                }
                if(p.kind in ElectromechanicalParts.pinNames) {
                    stampElectromechanical(p,IntArray(p.terminalCount) { idx(p,it) },guess,a,b)
                    return@forEach
                }
                if(p.kind in PhaseFiveParts.pinNames) {
                    stampPhaseFive(p,IntArray(p.terminalCount) { idx(p,it) },guess,a,b)
                    return@forEach
                }
                if(p.kind in setOf(Kind.SCR,Kind.TRIAC)) {
                    stampThyristor(p,IntArray(3) { idx(p,it) },guess,a,b)
                    return@forEach
                }
                if(p.kind in setOf(Kind.LM358,Kind.LM741,Kind.COMPARATOR,
                        Kind.REGULATOR_7805,Kind.REGULATOR_LM317,Kind.L293D,Kind.ULN2003)) {
                    stampAnalogIc(p,IntArray(p.terminalCount) { idx(p,it) },guess,a,b)
                    return@forEach
                }
                when(p.kind) {
                    Kind.DC_CURRENT_SOURCE -> {
                        val n1=idx(p,0);val n2=idx(p,1);val current=p.value("current")
                        if(n1>=0) b[n1]-=current
                        if(n2>=0) b[n2]+=current
                        return@forEach
                    }
                    Kind.VCCS -> { stampVccs(a,IntArray(4) { idx(p,it) },p.value("gain"));return@forEach }
                    Kind.VCVS -> return@forEach
                    Kind.CCCS,Kind.CCVS -> return@forEach
                    Kind.TRANSFORMER -> {
                        stampBranch(a,b,idx(p,0),idx(p,1),BranchModel(1000.0,0.0))
                        stampBranch(a,b,idx(p,2),idx(p,3),BranchModel(1000.0,0.0))
                        return@forEach
                    }
                    Kind.RELAY -> {
                        conduct(idx(p,0),idx(p,1),1.0/p.value("coilResistance"))
                        val coil=abs((if(idx(p,0)>=0) guess[idx(p,0)] else 0.0)-
                            (if(idx(p,1)>=0) guess[idx(p,1)] else 0.0))
                        conduct(idx(p,2),idx(p,if(coil>=p.value("pullInVoltage")) 3 else 4),1000.0)
                        return@forEach
                    }
                    Kind.SPDT_SWITCH -> {
                        conduct(idx(p,0),idx(p,if(p.closed) 1 else 2),1000.0)
                        return@forEach
                    }
                    Kind.RGB_LED,Kind.SEVEN_SEGMENT -> {
                        val cathode=p.terminalCount-1
                        for(pin in 0 until cathode) {
                            val n1=idx(p,pin);val n2=idx(p,cathode)
                            val v=(if(n1>=0) guess[n1] else 0.0)-(if(n2>=0) guess[n2] else 0.0)
                            val vf=if(p.kind==Kind.RGB_LED) listOf(1.8,2.2,3.0)[pin] else 1.9
                            stampBranch(a,b,n1,n2,ledBranchAt(v,vf,p.value("seriesResistance")))
                        }
                        return@forEach
                    }
                    else -> Unit
                }
                if(p.kind==Kind.POTENTIOMETER) {
                    val (upper,lower)=potentiometerSegments(p)
                    conduct(idx(p,0),idx(p,1),1.0/upper)
                    conduct(idx(p,1),idx(p,2),1.0/lower)
                    return@forEach
                }
                if(p.terminalCount==3 && !isOpAmp(p)) {
                    stampThreeTerminal(p,IntArray(3) { idx(p,it) },guess,a,b)
                    return@forEach
                }
                val n1 = idx(p, 0); val n2 = idx(p, 1)
                when (p.kind) {
                    Kind.RESISTOR, Kind.LAMP,Kind.FUSE -> conduct(n1, n2, 1.0 / max(p.value("resistance"), 1e-6))
                    Kind.LDR,Kind.THERMISTOR -> conduct(n1,n2,1.0/sensorResistanceOhms(p))
                    Kind.SWITCH,Kind.PUSH_BUTTON -> if (p.closed) conduct(n1, n2, 1000.0)
                    Kind.DC_MOTOR -> {
                        val effective=p.value("resistance")+p.value("motorConstant")*
                            p.value("motorConstant")/p.value("friction").coerceAtLeast(1e-6)
                        conduct(n1,n2,1.0/effective)
                    }
                    Kind.AMMETER, Kind.INDUCTOR -> conduct(n1, n2, 1000.0)
                    Kind.VOLTMETER -> conduct(n1, n2, 1e-9)
                    Kind.LED, Kind.DIODE,Kind.RECTIFIER -> {
                        val raw = (if (n1 >= 0) guess[n1] else 0.0) - (if (n2 >= 0) guess[n2] else 0.0)
                        val model=diodeAt(p,raw)
                        conduct(n1,n2,model.conductance,model.equivalentCurrent(raw))
                    }
                    Kind.ZENER -> {
                        val raw=(if(n1>=0) guess[n1] else 0.0)-(if(n2>=0) guess[n2] else 0.0)
                        val model=zenerAt(p,raw)
                        conduct(n1,n2,model.conductance,model.ieq)
                    }
                    Kind.SCHOTTKY_DIODE,Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED -> {
                        val raw=(if(n1>=0) guess[n1] else 0.0)-(if(n2>=0) guess[n2] else 0.0)
                        val branch=ledBranchAt(raw,p.value("forwardVoltage"),
                            if(p.kind==Kind.SCHOTTKY_DIODE) p.value("dynamicResistance") else p.value("seriesResistance"))
                        conduct(n1,n2,branch.conductance,branch.ieq)
                    }
                    Kind.PHOTODIODE -> conduct(n1,n2,1.0/p.value("darkResistance"),
                        -p.value("responsivity")*p.value("illuminanceLux")/100.0)
                    else -> Unit // Capacitors are open in DC.
                }
            }
            externalDrives.forEach { drive ->
                val node=nodes[root(drive.terminal)] ?: return@forEach
                val conductance=1.0/drive.resistanceOhms
                a[node][node]+=conductance
                b[node]+=conductance*drive.voltage
            }
            sources.forEachIndexed { k, p ->
                val n1 = idx(p, 0); val n2 = idx(p, 1); val row = roots.size + k
                if (n1 >= 0) { a[n1][row] += 1.0; a[row][n1] += 1.0 }
                if (n2 >= 0) { a[n2][row] -= 1.0; a[row][n2] -= 1.0 }
                a[row][row]-=p.value("internalResistance")
                b[row] = sourceWaveform(p).valueAt(0.0)
            }
            opAmps.forEachIndexed { k,p ->
                val plus=idx(p,0);val minus=idx(p,1);val output=idx(p,2)
                val row=roots.size+sources.size+k
                val differential=(if(plus>=0) guess[plus] else 0.0)-(if(minus>=0) guess[minus] else 0.0)
                val model=opAmpAt(p,differential)
                if(output>=0) { a[output][row]+=1.0;a[row][output]+=1.0 }
                if(plus>=0) a[row][plus]-=model.slope
                if(minus>=0) a[row][minus]+=model.slope
                a[row][row]-=p.value("outputResistance")
                b[row]=model.output-model.slope*differential
            }
            vcvs.forEachIndexed { k,p -> stampVcvs(a,IntArray(4) { idx(p,it) },
                roots.size+sources.size+opAmps.size+k,p.value("gain")) }
            currentControlled.forEachIndexed { k,p ->
                val outputRow=if(p.kind==Kind.CCVS) roots.size+sources.size+opAmps.size+
                    vcvs.size+currentControlled.size+ccvs.indexOf(p) else null
                stampCurrentControlled(a,IntArray(4) { idx(p,it) },
                    roots.size+sources.size+opAmps.size+vcvs.size+k,outputRow,p.value("gain"))
            }
            // Tiny shunts keep zero-current branches numerically defined without showing fake current.
            for (n in roots.indices) a[n][n] += 1e-12
            val next = DenseLinearSolver.solve(a, b) ?: return DcResult(error =
                "Circuit is floating or has a singular connection at iteration $iterations.",
                progress=SolverProgress(iterations,lastUpdate))
            if (next.any { !it.isFinite() }) return DcResult(error = "Numerical error at iteration $iterations.",
                progress=SolverProgress(iterations,lastUpdate))
            val delta = next.indices.maxOf { abs(next[it] - guess[it]) }
            lastUpdate=delta
            lastIndex=next.indices.maxBy { abs(next[it]-guess[it]) }
            guess = DoubleArray(size) { .65 * next[it] + .35 * guess[it] }
            if (delta < circuit.settings.tolerance) { guess = next; converged = true; break }
        }
        val likely=parts.firstOrNull { p -> p.kind in setOf(Kind.LED,Kind.DIODE,Kind.RECTIFIER,
            Kind.NPN_BJT,Kind.PNP_BJT,Kind.NMOS,Kind.PMOS,Kind.OPAMP,Kind.IDEAL_OPAMP) &&
            (0 until p.terminalCount).any { idx(p,it)==lastIndex } }
        val progress=SolverProgress(iterations,lastUpdate,likely?.id)
        if (!converged) return DcResult(error =
            "Simulation did not converge after $iterations iterations (largest update ${"%.3g".format(java.util.Locale.US,lastUpdate)}; tolerance ${circuit.settings.tolerance}).",
            converged = false,progress=progress)
        val volts = terminals.associateWith { t -> nodes[root(t)]?.let { guess[it] } ?: 0.0 }
        val readings = parts.associate { p ->
            if(p.kind in ElectromechanicalParts.pinNames)
                return@associate p.id to electromechanicalReading(p,volts)
            if(p.kind in PhaseFiveParts.pinNames)
                return@associate p.id to phaseFiveReading(p,volts)
            if(p.kind in setOf(Kind.SCR,Kind.TRIAC))
                return@associate p.id to thyristorReading(p,volts)
            if(p.kind in setOf(Kind.LM358,Kind.LM741,Kind.COMPARATOR,
                    Kind.REGULATOR_7805,Kind.REGULATOR_LM317,Kind.L293D,Kind.ULN2003))
                return@associate p.id to analogIcReading(p,volts)
            val v = when(p.kind) {
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
            val i = when (p.kind) {
                Kind.BATTERY, Kind.SOURCE, Kind.FUNCTION_GENERATOR -> guess[roots.size + sources.indexOf(p)]
                Kind.DC_CURRENT_SOURCE -> p.value("current")
                Kind.IDEAL_OPAMP,Kind.OPAMP -> guess[roots.size+sources.size+opAmps.indexOf(p)]
                Kind.NPN_BJT,Kind.PNP_BJT,Kind.DARLINGTON,Kind.NMOS,Kind.PMOS -> threeTerminalCurrents(p,
                    DoubleArray(3) { volts[TerminalRef(p.id,it)] ?: 0.0 })[0]
                Kind.POTENTIOMETER -> (volts.getValue(TerminalRef(p.id,0))-
                    volts.getValue(TerminalRef(p.id,1)))/potentiometerSegments(p).first
                Kind.RESISTOR, Kind.LAMP,Kind.FUSE -> v / max(p.value("resistance"), 1e-6)
                Kind.LDR,Kind.THERMISTOR -> v / sensorResistanceOhms(p)
                Kind.SWITCH,Kind.PUSH_BUTTON -> if (p.closed) v * 1000.0 else 0.0
                Kind.SPDT_SWITCH -> (volts.getValue(TerminalRef(p.id,0))-
                    volts.getValue(TerminalRef(p.id,if(p.closed) 1 else 2)))*1000.0
                Kind.RELAY -> (volts.getValue(TerminalRef(p.id,0))-
                    volts.getValue(TerminalRef(p.id,1)))/p.value("coilResistance")
                Kind.VCVS -> guess[roots.size+sources.size+opAmps.size+vcvs.indexOf(p)]
                Kind.VCCS -> p.value("gain")*(volts.getValue(TerminalRef(p.id,0))-
                    volts.getValue(TerminalRef(p.id,1)))
                Kind.CCCS -> p.value("gain")*guess[roots.size+sources.size+opAmps.size+
                    vcvs.size+currentControlled.indexOf(p)]
                Kind.CCVS -> guess[roots.size+sources.size+opAmps.size+vcvs.size+
                    currentControlled.size+ccvs.indexOf(p)]
                Kind.TRANSFORMER -> (volts.getValue(TerminalRef(p.id,0))-
                    volts.getValue(TerminalRef(p.id,1)))*1000.0
                Kind.RGB_LED,Kind.SEVEN_SEGMENT -> (0 until p.terminalCount-1).sumOf { pin ->
                    val voltage=volts.getValue(TerminalRef(p.id,pin))-
                        volts.getValue(TerminalRef(p.id,p.terminalCount-1))
                    val vf=if(p.kind==Kind.RGB_LED) listOf(1.8,2.2,3.0)[pin] else 1.9
                    ledBranchAt(voltage,vf,p.value("seriesResistance")).current(voltage)
                }
                Kind.DC_MOTOR -> v/(p.value("resistance")+p.value("motorConstant")*
                    p.value("motorConstant")/p.value("friction").coerceAtLeast(1e-6))
                Kind.ZENER -> zenerAt(p,v).current(v)
                Kind.SCHOTTKY_DIODE,Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED ->
                    ledBranchAt(v,p.value("forwardVoltage"),if(p.kind==Kind.SCHOTTKY_DIODE)
                        p.value("dynamicResistance") else p.value("seriesResistance")).current(v)
                Kind.PHOTODIODE -> v/p.value("darkResistance")-
                    p.value("responsivity")*p.value("illuminanceLux")/100.0
                Kind.AMMETER, Kind.INDUCTOR -> v * 1000.0
                Kind.VOLTMETER -> v * 1e-9
                Kind.LED, Kind.DIODE,Kind.RECTIFIER -> diodeAt(p,v).current
                else -> 0.0
            }
            val power = if(p.kind==Kind.POTENTIOMETER) {
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
            } else abs(v * i)
            val rating = when (p.kind) {
                Kind.RESISTOR, Kind.LAMP -> if (power > p.value("rating")) Health.OVERLOAD else Health.NORMAL
                Kind.FUSE -> if(abs(i)>p.value("currentRating")) Health.WARNING else Health.NORMAL
                Kind.LED -> if (abs(i) > p.value("maxCurrent")*1.25) Health.WARNING else Health.NORMAL
                Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED -> if(abs(i)>p.value("maxCurrent")*1.25)
                    Health.WARNING else Health.NORMAL
                Kind.DIODE -> if(i>.2 || v< -100.0) Health.WARNING else Health.NORMAL
                Kind.RECTIFIER -> if(i>1.0 || v< -1000.0) Health.WARNING else Health.NORMAL
                Kind.NPN_BJT,Kind.PNP_BJT -> if(abs(i)>.2 || abs(v)>40.0 || power>.625) Health.OVERLOAD else Health.NORMAL
                Kind.NMOS -> if(abs(i)>.2 || abs(v)>60.0 || power>.4) Health.OVERLOAD else Health.NORMAL
                else -> Health.NORMAL
            }
            val thermal=ComponentRegistry.definitions.getValue(p.kind).thermal
            val temperature=thermal.junctionToAmbientCPerW?.let { thermal.ambientC+power*it }
            p.id to Reading(v, i, power, rating,temperature)
        }
        return DcResult(volts, readings,progress=progress)
    }

}
