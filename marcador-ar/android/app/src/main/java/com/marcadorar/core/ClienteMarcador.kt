package com.marcadorar.core

// Cliente de sesión del protocolo v1 — lógica pura de red/roles/estado con el
// transporte inyectado (mismo patrón que Partido inyecta el reloj). No conoce
// WebSocket: solo emite strings por `enviar` y consume strings por `onMensaje`.
// Así se prueba sin red (transporte falso) y contra el server Node real (interop).
// El ciclo de vida (conexión, latido, reconexión) vive en Supervisor.kt.

class ClienteMarcador(
    private val instancia: String,
    val deviceId: String,
) {
    // estado de sesión reflejado desde el servidor
    var conectado: Boolean = false; private set
    var rol: String = "espectador"; private set
    var config: Config = Config(); private set
    var estado: Estado = estadoInicial(config); private set
    var colocacion: Colocacion = Colocacion(); private set
    var espectadores: Int = 0; private set
    var adminOnline: Boolean = false; private set

    // transporte inyectado
    var transporte: Transporte? = null

    // callbacks (la UI los suscribe)
    var onEstado: ((Estado) -> Unit)? = null
    var onConfig: ((Config) -> Unit)? = null
    var onColocacion: ((Colocacion) -> Unit)? = null
    var onRol: ((String, String?) -> Unit)? = null
    var onBuzzer: (() -> Unit)? = null
    var onShotExpired: (() -> Unit)? = null
    var onFijar: (() -> Unit)? = null
    var onPresencia: ((Boolean, Int) -> Unit)? = null
    var onError: ((String, String) -> Unit)? = null

    private fun enviar(m: Json) { transporte?.enviar(m.escribir()) }

    /** Abre sesión: la primera unión fija la instancia. */
    fun join() { enviar(mensajeJoin(instancia, deviceId)) }

    fun claim() { enviar(mensajeClaim()) }
    fun resign() { enviar(mensajeResign()) }
    fun heartbeat() { enviar(mensajeHeartbeat()) }

    /**
     * Envía un comando si se es admin. Devuelve false (y no envía) si no se tiene
     * el rol — el guardia de solo-lectura vive aquí, no en la UI.
     */
    fun accion(cmd: Cmd): Boolean {
        if (rol != "admin") return false
        enviar(cmd.aMensajeWire())
        return true
    }

    /** Envía un setup completo (solo admin). */
    fun enviarSetup(nueva: Config): Boolean {
        if (rol != "admin") return false
        enviar(mensajeSetup(nueva))
        return true
    }

    /** Envía la colocación AR completa (solo admin). */
    fun enviarColocacion(col: Colocacion): Boolean = enviarColocacion(col, borrarGeo = false)

    /** Envía la colocación AR; [borrarGeo] quita el fijado geográfico. */
    fun enviarColocacion(col: Colocacion, borrarGeo: Boolean): Boolean {
        if (rol != "admin") return false
        enviar(mensajePlace(col, borrarGeo))
        return true
    }

    /**
     * Reporta la pose WGS84 del ancla AR al servidor (fijado geográfico).
     * Puede cualquier cliente unido: solo el dispositivo con sesión AR conoce
     * sus propias coordenadas.
     */
    fun enviarGeo(g: GeoColocacion) { enviar(mensajeGeo(g)) }

    fun onConectado() { conectado = true }
    fun onDesconectado() { conectado = false; rol = "espectador" }

    /** Procesa un texto entrante. Devuelve el mensaje tipado (o null si ilegible). */
    fun onMensaje(texto: String): Entrada? {
        val e = entradaDesdeWire(texto) ?: return null
        when (e) {
            is Entrada.Welcome -> {
                rol = e.rol; config = e.config; estado = e.estado; colocacion = e.colocacion
                onConfig?.invoke(config); onEstado?.invoke(estado); onColocacion?.invoke(colocacion)
                onRol?.invoke(rol, null)
            }
            is Entrada.EstadoMsg -> { estado = e.estado; onEstado?.invoke(estado) }
            is Entrada.ConfigMsg -> { config = e.config; onConfig?.invoke(config) }
            is Entrada.ColocacionMsg -> { colocacion = e.colocacion; onColocacion?.invoke(colocacion) }
            is Entrada.Role -> {
                rol = if (e.granted) "admin" else "espectador"
                onRol?.invoke(rol, e.motivo)
            }
            is Entrada.Presencia -> {
                adminOnline = e.adminOnline; espectadores = e.espectadores
                onPresencia?.invoke(e.adminOnline, e.espectadores)
            }
            is Entrada.Buzzer -> onBuzzer?.invoke()
            is Entrada.ShotExpired -> onShotExpired?.invoke()
            is Entrada.Fijar -> onFijar?.invoke()
            is Entrada.Error -> onError?.invoke(e.code, e.message)
            is Entrada.Desconocido -> { /* ignora tipos futuros */ }
        }
        return e
    }
}
