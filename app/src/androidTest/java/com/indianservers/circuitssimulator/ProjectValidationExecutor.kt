package com.indianservers.circuitssimulator

import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Non-UI circuits own their scheduler, solver and protocol fabric. Keep UI tests serial. */
internal fun <T> executeIndependentProjects(items: Collection<T>, parallelism: Int = 3, run: (T) -> Unit) {
    val workers = Executors.newFixedThreadPool(parallelism)
    try {
        workers.invokeAll(items.map { item -> Callable { run(item) } }).forEach { it.get() }
    } finally {
        workers.shutdownNow()
    }
}
