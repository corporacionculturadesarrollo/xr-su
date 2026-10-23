package com.marcadorar.app.net

import android.os.Handler
import android.os.Looper
import com.marcadorar.core.Recurso
import com.marcadorar.core.Temporizador

/**
 * [Temporizador] sobre el `Handler` del hilo principal — para el lado cliente
 * (Supervisor) de la app Android. Al correr en el hilo principal, el latido y la
 * reconexión comparten hilo con los callbacks de red de [FabricaOkHttp] (que
 * también postea al principal), así no hay carreras sobre el estado del cliente.
 */
class TemporizadorAndroid(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : Temporizador {

    override fun ahora(): Long = System.currentTimeMillis()

    override fun tras(ms: Long, tarea: () -> Unit): Recurso {
        val r = Runnable(tarea)
        handler.postDelayed(r, ms)
        return object : Recurso { override fun cancelar() { handler.removeCallbacks(r) } }
    }

    // Handler no tiene periódico nativo: se reprograma a sí mismo tras cada disparo.
    // El Recurso devuelto cancela SIEMPRE el último pendiente (no uno stale).
    override fun cada(ms: Long, tarea: () -> Unit): Recurso {
        val ultimo = arrayOfNulls<Recurso>(1)
        lateinit var tick: () -> Unit
        tick = {
            tarea()
            ultimo[0] = tras(ms, tick)   // se reprograma a sí mismo
        }
        ultimo[0] = tras(ms, tick)
        return object : Recurso { override fun cancelar() { ultimo[0]?.cancelar() } }
    }
}
