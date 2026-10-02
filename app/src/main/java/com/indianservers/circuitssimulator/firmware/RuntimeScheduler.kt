package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcSolver

/** Shared virtual clock for every MCU and the electrical solver. */
class RuntimeScheduler {
    val fabric=ProtocolFabric()
    val peripherals=PeripheralRuntime()
    val sessions=mutableMapOf<String,FirmwareSession>()
    private val solver=DcSolver()
    fun clear() { sessions.clear();fabric.reset();peripherals.reset() }
    fun attach(session:FirmwareSession) {
        sessions[session.board.boardId]=session
        session.environment=session.board.circuit.environment
        fabric.rebuildDevices(session.board.circuit)
    }
    fun compile(circuit:Circuit):RuntimeScheduler {
        clear()
        circuit.components.filter { it.kind.isBoard }.forEach { part ->
            val attachment=circuit.firmware.firstOrNull { it.boardId==part.id } ?: return@forEach
            val board=FirmwareBoard(circuit,part.id)
            val program=when(attachment.language) {
                FirmwareLanguage.MICROPYTHON_SUBSET -> MicroPythonSubset.compile(attachment.source,board)
                FirmwareLanguage.ARDUINO_SUBSET -> ArduinoSubset.compile(attachment.source,board)
            }
            attach(FirmwareSession(program,board,fabric,circuit.environment))
        }
        return this
    }
    fun reset() { sessions.values.forEach { it.reset() };fabric.reset();peripherals.reset() }
    fun resetBoard(boardId:String?) {
        val session=sessions[boardId] ?: return
        session.reset()
        fabric.uart[boardId]?.rxBytes?.clear()
        fabric.uart[boardId]?.txBytes?.clear()
    }
    fun stop() { sessions.values.forEach { it.stop() } }
    fun advance(deltaMicros:Long):CombinedAdvance {
        if(sessions.size==1) {
            val only=sessions.values.single()
            only.environment=only.board.circuit.environment
            val next=only.advance(deltaMicros)
            return CombinedAdvance(next.frames,mapOf(only.board.boardId to next.console),next.error,
                next.timeMicros,mapOf(only.board.boardId to next.currentLine),
                mapOf(only.board.boardId to next.variables),
                mapOf(only.board.boardId to (next.frames.lastOrNull()?.pins ?: emptyMap())),
                fabric.log.toList(),fabric.displayText(),peripherals.angles())
        }
        val frames=mutableListOf<FirmwareFrame>()
        var error:FirmwareDiagnostic?=null
        var time=sessions.values.maxOfOrNull { it.timeMicros } ?: 0L
        val target=time+deltaMicros
        try {
            while(time<=target) {
                sessions.values.forEach { session ->
                    session.environment=session.board.circuit.environment
                    val slice=session.advance(minOf(2000L,(target-session.timeMicros).coerceAtLeast(0)))
                    frames+=slice.frames
                    if(slice.error!=null) error=slice.error
                }
                val circuit=sessions.values.first().board.circuit
                val envCircuit=peripherals.applyEnvironment(circuit)
                val sample=time/1_000_000.0
                val drives=sessions.values.flatMap { it.board.collectDrives(sample) }+
                    peripherals.drives(envCircuit,sessions.values.first().board.latest,time)
                val result=solver.solve(envCircuit,drives)
                sessions.values.forEach { it.board.observe(result,0.002,sample) }
                fabric.exchangeUart(circuit,time)
                peripherals.stepServos(circuit,result,0.002,sessions.values.flatMap { it.board.pwmPins().toList() }.toMap())
                time+=2000
            }
        } catch(e:Exception) {
            error=FirmwareDiagnostic(0,e.message ?: "Firmware scheduler failed")
        }
        val last=frames.lastOrNull()
        return CombinedAdvance(frames.takeLast(2500),
            sessions.mapValues { it.value.consoleLines() },error,time,
            sessions.mapValues { it.value.currentLine },
            sessions.mapValues { it.value.variables() },
            sessions.mapValues { it.value.board.let { board -> last?.pins ?: emptyMap() } },
            fabric.log.toList(),fabric.displayText(),peripherals.angles())
    }
}
