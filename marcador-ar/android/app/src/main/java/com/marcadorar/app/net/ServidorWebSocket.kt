package com.marcadorar.app.net

import com.marcadorar.core.Colocacion
import com.marcadorar.core.ServidorMarcador
import com.marcadorar.core.TemporizadorProgramado
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer

/**
 * Servidor WebSocket real sobre Java-WebSocket — hace de transporte para el
 * núcleo [ServidorMarcador]. Es lo que permite que el teléfono del admin aloje la
 * instancia: los espectadores se conectan a esta dirección.
 *
 * Concurrencia: todos los callbacks de red (onOpen/onMessage/onClose) y los ticks
 * del reloj del partido se ejecutan en UN mismo hilo programado
 * ([TemporizadorProgramado]), así el estado del partido nunca se toca desde dos
 * hilos a la vez. `conn.send` es thread-safe, así que emitir desde ese hilo es
 * seguro.
 */
class ServidorWebSocket(
    puerto: Int,
    instanciasValidas: (String) -> Boolean = { true },
    colocacionInicial: Colocacion = Colocacion(),
) : WebSocketServer(InetSocketAddress(puerto)) {

    private val tiempo = TemporizadorProgramado()   // su único hilo serializa todo
    private val marcador = ServidorMarcador(tiempo, instanciasValidas, colocacionInicial = colocacionInicial)

    /** Colocación AR actual (la primera instancia). Para que el host la persista. */
    fun colocacion(): Colocacion = marcador.colocacion()

    /**
     * Cablea el callback de persistencia de la colocación AR. El host (MainActivity)
     * lo usa para guardarla en disco cada vez que el admin la cambia, de modo que
     * sobreviva al reinicio de la app.
     */
    var alCambiarColocacion: ((Colocacion) -> Unit)?
        get() = marcador.alCambiarColocacion
        set(v) { marcador.alCambiarColocacion = v ?: {} }

    private val ids = ConcurrentHashMap<String, WebSocket>()   // clienteId -> conn
    private val clientes = ConcurrentHashMap<WebSocket, String>() // conn -> clienteId

    /** Señala cuando el servidor ya está escuchando (listo para aceptar). */
    val listo = CountDownLatch(1)

    init {
        // SO_REUSEADDR: Java-WebSocket lo trae DESACTIVADO (el constructor llama a
        // setReuseAddr(false)). Tras un reinicio de la app, los sockets del servidor
        // anterior quedan en TIME_WAIT en este puerto (~60 s), y el bind falla con
        // "Address already in use" — la instancia arrancaría sin servidor. Activarlo
        // permite rebindear de inmediato. Debe ir ANTES de start().
        setReuseAddr(true)
        marcador.emitir = { clienteId, texto -> ids[clienteId]?.send(texto) }
    }

    // ---- WebSocketServer ----

    override fun onStart() { println("SRV onStart: escuchando reuse=${isReuseAddr()}"); listo.countDown() }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val id = UUID.randomUUID().toString()
        clientes[conn] = id
        ids[id] = conn
        println("SRV onOpen: clientes=${ids.size}")
        tiempo.accion { marcador.conectar(id) }   // en el hilo único
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        println("SRV onClose: code=$code reason=$reason remote=$remote")
        val id = clientes.remove(conn) ?: return
        ids.remove(id)
        tiempo.accion { marcador.desconectar(id) }
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val id = clientes[conn] ?: return
        tiempo.accion { marcador.recibir(id, message) }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        // una conexión rota no debe tumbar el servidor; el cliente reconecta solo.
        // El fallo de bind también llega por aquí (p. ej. puerto ocupado).
        println("SRV onError: conn=${conn != null} ex=${ex.javaClass.simpleName}: ${ex.message} reuse=${isReuseAddr()}")
    }

    // ---- ciclo de vida ----

    /** Espera a que esté escuchando (hasta `segundos`). */
    fun esperarListo(segundos: Long = 5): Boolean =
        listo.await(segundos, TimeUnit.SECONDS)

    /** Detiene el servidor, el reloj del partido y el hilo de tiempo. */
    fun cerrar() {
        println("SRV cerrar()")
        // 1) parar el servidor primero: drena los onClose mientras el hilo de
        //    tiempo sigue vivo (si no, encolar tras apagar el executor fallaría).
        try { stop(500) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        // 2) apagar el reloj del partido y el hilo de tiempo.
        marcador.detener()
        tiempo.apagar()
    }
}
