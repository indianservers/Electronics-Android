package com.indianservers.circuitssimulator.firmware

/** UI wall-clock ticks may span many bus edges after a delayed frame or lifecycle transition. */
object FirmwarePlayback {
    private const val QUANTUM_MICROS=10_000L
    private const val RETAINED_FRAMES=4000

    fun advance(runtime:RuntimeScheduler,deltaMicros:Long):CombinedAdvance {
        require(deltaMicros in 0..10_000_000)
        require(runtime.sessions.isNotEmpty())
        var remaining=deltaMicros
        val frames=ArrayDeque<FirmwareFrame>()
        var latest:CombinedAdvance
        do {
            val quantum=minOf(remaining,QUANTUM_MICROS)
            latest=if(runtime.sessions.size>1) runtime.advance(quantum) else {
                // Preserve the UI's explicit live environment rather than replacing it
                // with a potentially older saved-circuit environment.
                val session=runtime.sessions.values.single()
                val single=session.advance(quantum)
                CombinedAdvance(single.frames,mapOf(session.board.boardId to single.console),
                    single.error,single.timeMicros,mapOf(session.board.boardId to single.currentLine),
                    mapOf(session.board.boardId to single.variables),
                    mapOf(session.board.boardId to (single.frames.lastOrNull()?.pins ?: emptyMap())),
                    runtime.fabric.log.toList(),runtime.fabric.displayText())
            }
            latest.frames.forEach {frame->
                if(frames.size==RETAINED_FRAMES)frames.removeFirst()
                frames.addLast(frame)
            }
            remaining-=quantum
        }while(remaining>0 && latest.error==null)
        // All electrical steps execute. Only the displayed trace history is bounded,
        // as it already is in the ViewModel; engine frame/instruction guards remain.
        return latest.copy(frames=frames.toList())
    }
}
