package com.marcadorar.app.net

import android.os.Handler
import android.os.Looper
import com.marcadorar.core.FabricaTransporte
import com.marcadorar.core.OyenteRed
import com.marcadorar.core.Transporte
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Transporte WebSocket de la app sobre OkHttp — implementación Android de
 * [FabricaTransporte]. Mapea los callbacks de OkHttp a [OyenteRed].
 *
 * Todos los callbacks se entregan en el **hilo principal** (el mismo que corre el
 * [com.marcadorar.core.Supervisor]), de modo que no hay carreras entre el estado
 * del supervisor y la red: OkHttp avisa en su propio hilo, y aquí se postea al
 * principal antes de tocar el oyente.
 *
 * Como `abrir` devuelve el `Transporte` de forma síncrona (el WebSocket de OkHttp
 * es manejable desde el primer instante) y `onOpen` se postea al principal, el
 * `Supervisor` ya tiene `transporte` asignado cuando procesa `alConectar` — sin
 * pérdida del `join` inicial.
 */
class FabricaOkHttp(url: String) : FabricaTransporte {
    private val url = url
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)   // detecta conexiones muertas
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
    private val main = Handler(Looper.getMainLooper())

    override fun abrir(oyente: OyenteRed): Transporte {
        val req = Request.Builder().url(url).build()
        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                main.post { oyente.alConectar() }
            }
            override fun onMessage(ws: WebSocket, text: String) {
                main.post { oyente.alRecibir(text) }
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                main.post { oyente.alDesconectar() }
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                main.post { oyente.alDesconectar() }   // caída → el supervisor reconecta
            }
        })
        return object : Transporte {
            override fun enviar(texto: String) { ws.send(texto) }
            override fun cerrar() { ws.close(1000, "fin") }
        }
    }
}
