package com.marcadorar.core

// Ciclo de vida de la conexión — orquesta transporte, latido y reconexión
// (mismo comportamiento que el cliente de referencia marcador.html: join al abrir,
// latido cada 3s solo siendo admin, reconexión con espera fija de 2.5s tras caer).
// Tiempo y transporte van inyectados para probar sin Android ni red real.

// ---- abstracciones inyectables ----

/** Conexión abierta. La implementación Android la cablea a un WebSocket (OkHttp). */
interface Transporte {
    fun enviar(texto: String)
    fun cerrar()
}

/** Eventos que la conexión entrega de vuelta al supervisor. */
interface OyenteRed {
    fun alConectar()
    fun alRecibir(texto: String)
    fun alDesconectar()
}

/** Crea conexiones, alimentando sus eventos al oyente. */
interface FabricaTransporte {
    fun abrir(oyente: OyenteRed): Transporte
}

/** Programador de tareas. Android usa Handler; los tests usan uno virtual. */
interface Temporizador {
    fun tras(ms: Long, tarea: () -> Unit): Recurso   // una vez, tras ms
    fun cada(ms: Long, tarea: () -> Unit): Recurso   // periódico cada ms
    fun ahora(): Long                                 // instante actual (para timeouts)
}
interface Recurso { fun cancelar() }

// ---- supervisor ----

/**
 * Mantiene viva la sesión: conecta, manda `join`, latida cada [latidoMs] solo
 * mientras sea admin, y reconecta cada [reconexionMs] si la conexión cae.
 * No decide nada del partido — solo transporte, latido y rol.
 */
class Supervisor(
    private val fabrica: FabricaTransporte,
    val cliente: ClienteMarcador,
    private val temp: Temporizador,
    private val latidoMs: Long = 3000,
    private val reconexionMs: Long = 2500,
) : OyenteRed {

    private var transporte: Transporte? = null
    private var latido: Recurso? = null
    private var reconexion: Recurso? = null
    private var activo = true

    init {
        // El cliente emite por la conexión viva actual (puente hacia `transporte`).
        cliente.transporte = object : Transporte {
            override fun enviar(texto: String) { transporte?.enviar(texto) }
            override fun cerrar() { transporte?.cerrar() }
        }
    }

    fun iniciar() { activo = true; conectar() }

    fun detener() {
        activo = false                // ignora callbacks tardíos tras detener
        reconexion?.cancelar(); reconexion = null
        detenerLatido()
        transporte?.cerrar(); transporte = null
        cliente.onDesconectado()
    }

    private fun conectar() {
        reconexion?.cancelar(); reconexion = null
        transporte = fabrica.abrir(this)
    }

    // ---- OyenteRed (la conexión nos avisa) ----

    override fun alConectar() {
        if (!activo) return
        cliente.onConectado()
        cliente.join()
        // latido periódico: el tick decide enviar según rol, así no hay que
        // encadenar callbacks de rol ni correr riesgo de timers huérfanos.
        if (latido == null) {
            latido = temp.cada(latidoMs) {
                if (cliente.conectado && cliente.rol == "admin") cliente.heartbeat()
            }
        }
    }

    override fun alRecibir(texto: String) { if (activo) cliente.onMensaje(texto) }

    override fun alDesconectar() {
        if (!activo) return
        cliente.onDesconectado()          // degrada a espectador y marca desconectado
        detenerLatido()
        // reconexión con espera fija (una sola vez; se reprograma si vuelve a caer)
        if (reconexion == null) {
            reconexion = temp.tras(reconexionMs) { reconexion = null; conectar() }
        }
    }

    private fun detenerLatido() { latido?.cancelar(); latido = null }
}
