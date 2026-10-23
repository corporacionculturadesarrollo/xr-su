package com.marcadorar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Interop real contra el servidor de referencia Node (ref/server.js).
 * Prueba que el códec Kotlin habla el protocolo v1 byte a byte con el server real,
 * usando el cliente WebSocket mínimo (WsClienteMin, sin dependencias).
 *
 * Cada test usa su propia instancia para aislar estado y roles (el server acepta
 * cualquier instancia con INSTANCIAS='*'), lo que además valida el aislamiento
 * multi-instancia que promete el protocolo.
 *
 * Requiere el server en INTEROP_PUERTO (8901). Si no responde, el test se omite
 * (assumeTrue) para que el suite quede verde sin red.
 *
 * Arranque:  cd ref && PUERTO=8901 INSTANCIAS='*' node server.js
 */
class InteropTest {

    companion object {
        const val INTEROP_PUERTO = 8901
        // Sufijo único por corrida: las instancias persisten en el server (y su estado
        // se acumula), así que cada ejecución usa instancias frescas para no contaminar.
        val RUN_ID = System.nanoTime().toString(36)
    }

    /** Abre conexión, hace join a una instancia y espera el welcome. */
    private fun conectar(base: String, deviceId: String): WsClienteMin = try {
        val instancia = "$base-$RUN_ID"
        val w = WsClienteMin("127.0.0.1", INTEROP_PUERTO)
        w.enviar(mensajeJoin(instancia, deviceId).escribir())
        w.esperar("welcome")
        w
    } catch (e: Throwable) { throw AssertionError("no se pudo conectar/join a '$base': $e") }

    private fun servidorArriba(): Boolean = try {
        WsClienteMin("127.0.0.1", INTEROP_PUERTO).let { it.close(); true }
    } catch (e: Exception) { false }

    @Test fun `join recibe welcome con config y state por defecto`() {
        assumeTrue("server de interop no arriba en :$INTEROP_PUERTO", servidorArriba())
        val w = WsClienteMin("127.0.0.1", INTEROP_PUERTO)
        try {
            w.enviar(mensajeJoin("it-join-$RUN_ID", "kotlin-dev-1").escribir())
            val welcome = w.esperar("welcome")
            assertEquals("espectador", welcome.str("rol")) // unirse nunca otorga rol
            assertEquals(10, welcome.campo("config").asObj.int("periodMinutes"))
            val st = welcome.campo("state").asObj
            assertEquals(1, st.int("period"))
            assertEquals(600, st.int("clockSec"))
        } finally { w.close() }
    }

    @Test fun `claim otorga rol y cmd score+ refleja en state broadcast`() {
        assumeTrue("server de interop no arriba en :$INTEROP_PUERTO", servidorArriba())
        val w = conectar("it-score", "kotlin-dev-2")
        try {
            w.enviar(mensajeClaim().escribir())
            val role = w.esperar("role")
            assertTrue("claim debería conceder", role.bool("granted") == true)

            w.enviar(Cmd.ScoreMas(Team.HOME, delta = 1).aMensajeWire().escribir())
            val st = w.esperar("state").campo("state").asObj
            assertEquals(1, st.campo("home").asObj.int("score"))
            assertEquals("visitor", st.str("possession")) // canasta cambia posesión
        } finally { w.close() }
    }

    @Test fun `clock start enciende running y tick baja el reloj`() {
        assumeTrue("server de interop no arriba en :$INTEROP_PUERTO", servidorArriba())
        val w = conectar("it-clock", "kotlin-dev-3")
        try {
            w.enviar(mensajeClaim().escribir()); w.esperar("role")

            w.enviar(Cmd.Clock(ClockOp.START).aMensajeWire().escribir())
            val st = w.esperar("state").campo("state").asObj
            assertTrue("running debería ser true tras start", st.bool("running") == true)

            val fin = System.currentTimeMillis() + 2500
            var bajo = false
            while (System.currentTimeMillis() < fin && !bajo) {
                val s = w.esperar("state", 2000).campo("state").asObj
                if ((s.int("clockSec") ?: 600) < 600) bajo = true
            }
            assertTrue("el reloj debería haber bajado tras los ticks", bajo)

            w.enviar(Cmd.Clock(ClockOp.PAUSE).aMensajeWire().escribir())
            val pausa = w.esperar("state").campo("state").asObj
            assertEquals(false, pausa.bool("running"))
        } finally { w.close() }
    }

    @Test fun `espectador sin claim recibe error rol al enviar cmd`() {
        assumeTrue("server de interop no arriba en :$INTEROP_PUERTO", servidorArriba())
        val w = conectar("it-espectador", "kotlin-dev-4")
        try {
            w.enviar(Cmd.ScoreMas(Team.HOME).aMensajeWire().escribir())
            val err = w.esperar("error")
            assertEquals("rol", err.str("code"))
        } finally { w.close() }
    }

    @Test fun `shot reset14 period next y setup viajan correctos`() {
        assumeTrue("server de interop no arriba en :$INTEROP_PUERTO", servidorArriba())
        val w = conectar("it-setup", "kotlin-dev-5")
        try {
            w.enviar(mensajeClaim().escribir()); w.esperar("role")

            w.enviar(Cmd.Shot(ShotOp.RESET14).aMensajeWire().escribir())
            assertEquals(14, w.esperar("state").campo("state").asObj.int("shotClockSec"))

            w.enviar(Cmd.Period(PeriodOp.NEXT).aMensajeWire().escribir())
            assertEquals(2, w.esperar("state").campo("state").asObj.int("period"))

            w.enviar(mensajeSetup(Config(homeName = "TIGRES")).escribir())
            val cfg = w.esperar("config").campo("config").asObj
            assertEquals("TIGRES", cfg.str("homeName"))
        } finally { w.close() }
    }
}
