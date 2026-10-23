package com.marcadorar.core

// Servidor WebSocket embebido — port de ref/server.js al núcleo Kotlin.
// Compone Partido (reducer) + Roles (singleton) y añade el despacho de mensajes
// y el reloj del partido. Es el lado servidor: permite que el teléfono del admin
// aloje la instancia (los espectadores se conectan a él).
//
// Transporte-agnóstico: el host inyecta `emitir(clienteId, texto)` para enviar y
// llama `conectar`/`desconectar`/`recibir` con los eventos de red. Así se prueba
// sin sockets (emit capturado, tiempo virtual) y se aloja en la app (red real).

class ServidorMarcador(
    private val temp: Temporizador,
    private val instanciasValidas: (String) -> Boolean = { true },
    private val latidoTimeoutMs: Long = 10_000,
    private val tickMs: Long = 1_000,
    private val colocacionInicial: Colocacion = Colocacion(),
) {
    /** Entrega `texto` al cliente `clienteId`. Lo cablea el host (red real o test). */
    var emitir: (clienteId: String, texto: String) -> Unit = { _, _ -> }

    /** Avisa cuando la colocación AR cambia (para que el host la persista a disco). */
    var alCambiarColocacion: (Colocacion) -> Unit = {}

    private class Cliente(var deviceId: String, var rol: String)

    private class Instancia(
        val id: String, ahora: () -> Long, timeoutMs: Long, colocacion0: Colocacion,
    ) {
        val roles = Roles(timeoutMs, ahora)
        var config = Config()
        var estado = estadoInicial(config)
        var colocacion = colocacion0
        val clientes = mutableMapOf<String, Cliente>()  // clienteId -> info
        var reloj: Recurso? = null
    }

    private val instancias = mutableMapOf<String, Instancia>()
    private val membresia = mutableMapOf<String, String>()  // clienteId -> instanciaId

    private fun instancia(id: String): Instancia =
        instancias.getOrPut(id) { Instancia(id, temp::ahora, latidoTimeoutMs, colocacionInicial) }

    /** Colocación AR de la instancia "principal" (o de la primera creada). */
    fun colocacion(): Colocacion =
        instancias.values.firstOrNull()?.colocacion ?: colocacionInicial

    // ---- eventos de red (los llama el host) ----

    fun conectar(@Suppress("UNUSED_PARAMETER") clienteId: String) {
        // nada hasta el join: unirse nunca otorga el rol
    }

    fun desconectar(clienteId: String) {
        val instId = membresia.remove(clienteId) ?: return
        val inst = instancias[instId] ?: return
        inst.clientes.remove(clienteId)
        // el rol se libera por timeout de latido, no por desconectar (tolerancia)
        presencia(inst)
    }

    fun recibir(clienteId: String, texto: String) {
        val m = parsearJson(texto)?.asObj
            ?: return enviar(clienteId, error("json", "JSON inválido"))
        if (m.str("tipo") == "join") return join(clienteId, m)

        val instId = membresia[clienteId]
            ?: return enviar(clienteId, error("join", "une primero (join)"))
        val inst = instancias[instId] ?: return
        val deviceId = inst.clientes[clienteId]?.deviceId ?: return

        when (m.str("tipo")) {
            "claim" -> claim(clienteId, inst, deviceId)
            "resign" -> resign(clienteId, inst, deviceId)
            "heartbeat" -> heartbeat(clienteId, inst, deviceId)
            "cmd" -> cmd(clienteId, inst, deviceId, m.campo("cmd").asObj)
            // Reporte de coordenadas WGS84 del ancla AR: lo manda el dispositivo
            // que TIENE la sesión AR (único que puede conocerlas). No es un
            // comando del partido, así que lo puede enviar cualquier cliente
            // unido; el servidor lo fusiona en la colocación y reemite.
            "geo" -> geo(clienteId, inst, m.campo("geo").asObj)
        }
    }

    /** Detiene los relojes de todas las instancias (para el host, en onDestroy). */
    fun detener() { for (inst in instancias.values) pararReloj(inst) }

    // ---- despacho por mensaje ----

    private fun join(clienteId: String, m: Json.Obj) {
        val instId = m.str("instancia") ?: ""
        if (!instanciasValidas(instId))
            return enviar(clienteId, error("instancia", "instancia no autorizada"))
        val inst = instancia(instId)
        val deviceId = m.str("deviceId") ?: "anon"
        // recupera el rol solo si este dispositivo era el admin (reconexión en gracia)
        val rol = if (inst.roles.esAdmin(deviceId)) "admin" else "espectador"
        membresia[clienteId] = instId
        inst.clientes[clienteId] = Cliente(deviceId, rol)
        enviar(clienteId, Json.obj(
            "tipo" to Json.str("welcome"),
            "rol" to Json.str(rol),
            "config" to inst.config.aWire(),
            "state" to inst.estado.aWire(),
            "colocacion" to inst.colocacion.aWire(),
        ))
        presencia(inst)
    }

    private fun claim(clienteId: String, inst: Instancia, deviceId: String) {
        val r = inst.roles.claim(deviceId)
        if (r.ok) {
            for (c in inst.clientes.values) if (c.deviceId == deviceId) c.rol = "admin"
            enviar(clienteId, Json.obj(
                "tipo" to Json.str("role"), "granted" to Json.bool(true), "motivo" to Json.str(r.motivo)))
            pararReloj(inst)   // el nuevo admin decide cuándo correr
            presencia(inst)
        } else {
            enviar(clienteId, Json.obj(
                "tipo" to Json.str("role"), "granted" to Json.bool(false), "motivo" to Json.str(r.motivo)))
        }
    }

    private fun resign(clienteId: String, inst: Instancia, deviceId: String) {
        val r = inst.roles.resign(deviceId)
        if (r.ok) {
            for (c in inst.clientes.values) if (c.deviceId == deviceId) c.rol = "espectador"
            broadcast(inst, Json.obj(
                "tipo" to Json.str("role"), "granted" to Json.bool(false), "motivo" to Json.str("entregado")))
            presencia(inst)
        }
    }

    private fun heartbeat(clienteId: String, inst: Instancia, deviceId: String) {
        val ok = inst.roles.heartbeat(deviceId)
        if (!ok && inst.clientes[clienteId]?.rol == "admin") {
            inst.clientes[clienteId]?.rol = "espectador"
            enviar(clienteId, Json.obj(
                "tipo" to Json.str("role"), "granted" to Json.bool(false), "motivo" to Json.str("timeout")))
        }
    }

    private fun cmd(clienteId: String, inst: Instancia, deviceId: String, cmd: Json.Obj?) {
        if (!inst.roles.esAdmin(deviceId))
            return enviar(clienteId, error("rol", "solo el admin alimenta el marcador"))
        try {
            if (cmd?.str("op") == "setup") {
                val nueva = configDesdeWire(inst.config, cmd.campo("config").asObj)
                val (cfg, est) = aplicarSetup(inst.config, inst.estado, nueva)
                inst.config = cfg; inst.estado = est
                broadcast(inst, Json.obj("tipo" to Json.str("config"), "config" to cfg.aWire()))
                broadcast(inst, Json.obj("tipo" to Json.str("state"), "state" to est.aWire()))
                return
            }
            // Colocación AR del tablero: no toca el estado del partido, solo se
            // fusiona y se reemite para que todas las vistas AR la apliquen.
            if (cmd?.str("op") == "place") {
                // "geo":"fijar" no es un cambio: es una petición para que la
                // vista AR admin capture sus coordenadas WGS84 y las devuelva.
                if (cmd.str("geo") == "fijar") {
                    broadcast(inst, Json.obj("tipo" to Json.str("fijar")))
                    return
                }
                inst.colocacion = colocacionDesdeWire(inst.colocacion, cmd)
                alCambiarColocacion(inst.colocacion)
                broadcast(inst, Json.obj(
                    "tipo" to Json.str("colocacion"), "colocacion" to inst.colocacion.aWire()))
                return
            }
            val c = cmdDesdeWire(cmd) ?: return enviar(clienteId, error("cmd", "cmd desconocido"))
            val r = aplicar(inst.config, inst.estado, c)
            inst.estado = r.estado
            if (c is Cmd.Clock) { if (r.estado.running) arrancarReloj(inst) else pararReloj(inst) }
            broadcast(inst, Json.obj("tipo" to Json.str("state"), "state" to r.estado.aWire()))
            for (e in r.eventos) broadcast(inst, eventoWire(e))
        } catch (e: Exception) {
            enviar(clienteId, error("cmd", e.message ?: "error"))
        }
    }

    /** Fusiona un reporte geo (del dispositivo AR) y reemite la colocación. */
    private fun geo(clienteId: String, inst: Instancia, g: Json.Obj?) {
        if (g?.numD("lat") == null || g.numD("lon") == null || g.numD("alt") == null)
            return enviar(clienteId, error("geo", "geo incompleta (lat/lon/alt)"))
        inst.colocacion = colocacionDesdeWire(inst.colocacion,
            Json.obj("geo" to Json.obj(
                "lat" to Json.num(g.numD("lat")!!), "lon" to Json.num(g.numD("lon")!!),
                "alt" to Json.num(g.numD("alt")!!), "rot" to Json.num((g.numF("rot") ?: 0f).toDouble()),
            )))
        alCambiarColocacion(inst.colocacion)
        broadcast(inst, Json.obj(
            "tipo" to Json.str("colocacion"), "colocacion" to inst.colocacion.aWire()))
    }

    // ---- reloj del partido ----

    private fun arrancarReloj(inst: Instancia) {
        if (inst.reloj != null) return
        inst.reloj = temp.cada(tickMs) {
            val r = tick(inst.config, inst.estado)
            inst.estado = r.estado
            broadcast(inst, Json.obj("tipo" to Json.str("state"), "state" to r.estado.aWire()))
            for (e in r.eventos) broadcast(inst, eventoWire(e))
        }
    }

    private fun pararReloj(inst: Instancia) { inst.reloj?.cancelar(); inst.reloj = null }

    // ---- utilidades de salida ----

    private fun enviar(clienteId: String, msg: Json) { emitir(clienteId, msg.escribir()) }

    private fun broadcast(inst: Instancia, msg: Json) {
        val txt = msg.escribir()
        for (cid in inst.clientes.keys) emitir(cid, txt)
    }

    private fun presencia(inst: Instancia) {
        val espectadores = inst.clientes.values.count { it.rol == "espectador" }
        broadcast(inst, Json.obj(
            "tipo" to Json.str("presence"),
            "adminOnline" to Json.bool(inst.roles.adminOnline()),
            "espectadores" to Json.num(espectadores),
        ))
    }

    private fun error(code: String, message: String) = Json.obj(
        "tipo" to Json.str("error"), "code" to Json.str(code), "message" to Json.str(message))

    private fun eventoWire(e: Evento): Json = when (e) {
        Evento.Buzzer -> Json.obj("tipo" to Json.str("buzzer"))
        Evento.ShotExpired -> Json.obj("tipo" to Json.str("shot-expired"))
        is Evento.Bonus -> Json.obj(
            "tipo" to Json.str("aviso"),
            "evento" to Json.str("bonus-" + if (e.team == Team.HOME) "home" else "visitor"))
    }
}
