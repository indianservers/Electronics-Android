package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.*

/** Shared virtual clock for every MCU and the electrical solver. */
class RuntimeScheduler {
    val fabric=ProtocolFabric()
    val peripherals=PeripheralRuntime()
    val sessions=mutableMapOf<String,FirmwareSession>()
    fun clear() { sessions.clear();fabric.reset();peripherals.reset() }
    fun attach(session:FirmwareSession) {
        sessions[session.board.boardId]=session
        sessions.values.forEach { target ->
            target.board.peerDrives={ sample -> sessions.values.filter { it!==target }.flatMap { it.board.collectDrives(sample) } }
        }
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
            attach(FirmwareSession(program,board,fabric,circuit.environment,peripherals))
        }
        return this
    }
    fun reset() { sessions.values.forEach { it.reset() };fabric.reset();peripherals.reset() }
    fun resetBoard(boardId:String?) {
        val session=sessions[boardId] ?: return
        session.reset(sessions.values.maxOf { it.timeMicros })
        fabric.uart[boardId]?.rxBytes?.clear()
        fabric.uart[boardId]?.txBytes?.clear()
    }
    fun stop() { sessions.values.forEach { it.stop() } }
    fun advance(deltaMicros:Long):CombinedAdvance {
        require(deltaMicros in 0..10_000_000) { "Advance interval must be 0–10 seconds." }
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
            while(sessions.isNotEmpty()) {
                // Execute only work due now, then expose all GPIO changes at the same clock.
                // Advancing an MCU for a whole 2 ms slice used to hide short clock pulses
                // from peers, whose local gate engines only saw its final GPIO levels.
                sessions.values.forEach { session ->
                    session.environment=session.board.circuit.environment
                    val slice=session.advance(0)
                    frames+=slice.frames
                    if(slice.error!=null) error=FirmwareDiagnostic(slice.error.line,"${session.board.part.reference}: ${slice.error.message}")
                }
                sessions.values.forEach { session ->
                    val slice=session.advanceElectricalTo(time)
                    frames+=slice.frames
                    if(slice.error!=null) error=FirmwareDiagnostic(slice.error.line,"${session.board.part.reference}: ${slice.error.message}")
                }
                val circuit=sessions.values.first().board.circuit
                val envCircuit=peripherals.applyEnvironment(circuit)
                val sample=time/1_000_000.0
                val signals=fabric.waveforms.drives(time)
                val refs=signals.map { it.terminal }.toSet()
                val drives=sessions.values.flatMap { it.board.collectDrives(sample) }+
                    (peripherals.drives(envCircuit,sessions.values.first().board.latest,time)+
                        fabric.uartIdleDrives(circuit,sessions.values.first().board.latest)).filter { it.terminal !in refs }+signals
                // Preserve gate/clock/latch outputs when producing the shared MCU frame.
                val result=sessions.values.first().board.settleSharedCircuit(envCircuit,drives,time)
                fabric.observe(circuit,result,time)
                sessions.values.forEach { it.board.observe(result,0.0,sample) }
                frames+=FirmwareFrame(time,result,emptyMap())
                fabric.exchangeUart(circuit,time)
                if(time>=target || sessions.values.all { it.isStopped }) break
                val limit=minOf(target,time+2000L)
                val next=sessions.values.minOf { it.nextSchedulerEvent(limit) }.coerceAtLeast(time+1)
                sessions.values.forEach { session ->
                    val slice=session.advanceElectricalTo(next)
                    frames+=slice.frames
                    if(slice.error!=null) error=FirmwareDiagnostic(slice.error.line,"${session.board.part.reference}: ${slice.error.message}")
                }
                time=next
            }
        } catch(e:Exception) {
            error=FirmwareDiagnostic(0,e.message ?: "Firmware scheduler failed")
        }
        return CombinedAdvance(frames.takeLast(2500),
            sessions.mapValues { it.value.consoleLines() },error,time,
            sessions.mapValues { it.value.currentLine },
            sessions.mapValues { it.value.variables() },
            sessions.mapValues { it.value.board.latestPins },
            fabric.log.toList(),fabric.displayText(),peripherals.angles())
    }
}
