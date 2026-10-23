package com.marcadorar.core

// Gestor de roles de una instancia — singleton admin (protocolo v1).
// Port directo de ref/roles.js: claim atómico, heartbeat con timeout,
// renuncia explícita y reconexión del propio admin.
// El reloj es inyectable para poder simular el timeout en pruebas.

data class ResultadoRol(val ok: Boolean, val motivo: String)

class Roles(
    private val timeoutMs: Long = 10_000,
    private val ahora: () -> Long = { System.currentTimeMillis() },
) {
    var admin: String? = null
        private set
    private var ultimoLatido: Long = 0

    /** Reclama el rol. Gana el primero; si ya lo tienes, reconecta. */
    fun claim(deviceId: String): ResultadoRol {
        sweep()
        return when {
            admin == null -> { admin = deviceId; ultimoLatido = ahora(); ResultadoRol(true, "concedido") }
            admin == deviceId -> { ultimoLatido = ahora(); ResultadoRol(true, "reconectado") }
            else -> ResultadoRol(false, "ocupado")
        }
    }

    /** Renuncia explícita. Solo el admin puede entregar el rol. */
    fun resign(deviceId: String): ResultadoRol =
        if (admin == deviceId) {
            admin = null; ultimoLatido = 0; ResultadoRol(true, "entregado")
        } else ResultadoRol(false, "no-es-admin")

    /** Latido del admin. Devuelve false si ya perdió el rol (timeout). */
    fun heartbeat(deviceId: String): Boolean =
        if (admin == deviceId) { ultimoLatido = ahora(); true } else false

    /** ¿Sigue siendo admin (tras barrer los vencidos)? Para denegar comandos. */
    fun esAdmin(deviceId: String): Boolean { sweep(); return admin == deviceId }

    fun adminOnline(): Boolean { sweep(); return admin != null }

    /** Libera el rol si el latido venció. */
    private fun sweep(): Boolean {
        if (admin != null && ahora() - ultimoLatido > timeoutMs) {
            admin = null; ultimoLatido = 0; return true
        }
        return false
    }
}
