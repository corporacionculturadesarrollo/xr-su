package com.marcadorar.core

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * [Temporizador] sobre un [ScheduledExecutorService] — implementación real (JVM y
 * Android). El transport del servidor la usa con UN solo hilo programado, de modo
 * que los mensajes de red y los ticks del reloj quedan serializados en ese hilo
 * y no hay carreras sobre el estado del partido.
 */
class TemporizadorProgramado(private val exec: ScheduledExecutorService) : Temporizador {

    /** Crea un programador de UN solo hilo con hilos daemon (no bloquea la salida del JVM). */
    constructor() : this(
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "marcador-tiempo").apply { isDaemon = true }
        }
    )

    override fun ahora(): Long = System.currentTimeMillis()

    override fun tras(ms: Long, tarea: () -> Unit): Recurso =
        RecursoExec(exec.schedule(tarea, ms, TimeUnit.MILLISECONDS))

    override fun cada(ms: Long, tarea: () -> Unit): Recurso =
        RecursoExec(exec.scheduleAtFixedRate(tarea, ms, ms, TimeUnit.MILLISECONDS))

    /** Encola `tarea` en el hilo único del programador (serializa con los ticks). */
    fun accion(tarea: () -> Unit) {
        try { exec.execute(tarea) } catch (_: java.util.concurrent.RejectedExecutionException) {
            // executor ya apagado (cierre tardío): se ignora
        }
    }

    fun apagar() { exec.shutdown() }

    private class RecursoExec(private val f: ScheduledFuture<*>) : Recurso {
        override fun cancelar() { f.cancel(false) }
    }
}
