package com.marcadorar.core

import com.marcadorar.app.net.ServidorWebSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Interop del servidor Kotlin embebido contra un socket WebSocket REAL.
 * Prueba que [ServidorWebSocket] (Java-WebSocket + ServidorMarcador) habla el
 * protocolo v1 por red de verdad — no con `emit` capturado. Usa el cliente
 * mínimo [WsClienteMin] como espectador/admin remoto.
 */
class ServidorWebSocketTest {

    private fun servidor(): ServidorWebSocket {
        val srv = ServidorWebSocket(0, instanciasValidas = { true })  // puerto efímero
        srv.start()
        assertTrue("el servidor debería estar escuchando", srv.esperarListo(5))
        return srv
    }

    @Test fun `socket real - join claim y canasta reflejan`() {
        val srv = servidor()
        try {
            WsClienteMin("127.0.0.1", srv.port).use { w ->
                w.enviar(mensajeJoin("cancha", "admin-1").escribir())
                val wel = w.esperar("welcome")
                assertEquals("espectador", wel.str("rol"))  // unirse nunca otorga rol

                w.enviar(mensajeClaim().escribir())
                assertTrue("claim debería conceder", w.esperar("role").bool("granted") == true)

                w.enviar(Cmd.ScoreMas(Team.HOME, delta = 2).aMensajeWire().escribir())
                val st = w.esperar("state").campo("state").asObj
                assertEquals(2, st.campo("home").asObj.int("score"))
                assertEquals("visitor", st.str("possession"))
            }
        } finally { srv.cerrar() }
    }

    @Test fun `socket real - espectador escucha el broadcast del admin`() {
        val srv = servidor()
        try {
            WsClienteMin("127.0.0.1", srv.port).use { admin ->
                WsClienteMin("127.0.0.1", srv.port).use { esp ->
                    admin.enviar(mensajeJoin("cancha", "admin-1").escribir()); admin.esperar("welcome")
                    esp.enviar(mensajeJoin("cancha", "espectador-1").escribir()); esp.esperar("welcome")

                    admin.enviar(mensajeClaim().escribir()); admin.esperar("role")
                    admin.enviar(Cmd.FoulMas(Team.VISITOR).aMensajeWire().escribir())
                    admin.esperar("state")

                    // el espectador recibe el mismo state (broadcast)
                    val st = esp.esperar("state").campo("state").asObj
                    assertEquals(1, st.campo("visitor").asObj.int("fouls"))
                }
            }
        } finally { srv.cerrar() }
    }

    @Test fun `socket real - cmd sin rol es rechazado con error`() {
        val srv = servidor()
        try {
            WsClienteMin("127.0.0.1", srv.port).use { w ->
                w.enviar(mensajeJoin("cancha", "espectador-9").escribir()); w.esperar("welcome")
                w.enviar(Cmd.Clock(ClockOp.START).aMensajeWire().escribir())
                assertEquals("rol", w.esperar("error").str("code"))
            }
        } finally { srv.cerrar() }
    }

    @Test fun `socket real - el reloj del servidor hace tick y transmite`() {
        val srv = servidor()
        try {
            WsClienteMin("127.0.0.1", srv.port).use { w ->
                w.enviar(mensajeJoin("cancha", "admin-1").escribir()); w.esperar("welcome")
                w.enviar(mensajeClaim().escribir()); w.esperar("role")
                w.enviar(Cmd.Clock(ClockOp.START).aMensajeWire().escribir())
                assertTrue(w.esperar("state").campo("state").asObj.bool("running") == true)

                // el reloj real del servidor (1 s/tick) debe bajar el estado
                val fin = System.currentTimeMillis() + 2500
                var bajo = false
                while (System.currentTimeMillis() < fin && !bajo) {
                    val s = w.esperar("state", 2000).campo("state").asObj
                    if ((s.int("clockSec") ?: 600) < 600) bajo = true
                }
                assertTrue("el reloj del servidor debería hacer tick", bajo)
            }
        } finally { srv.cerrar() }
    }

    @Test fun `socket real - instancia no autorizada se rechaza`() {
        val srv = ServidorWebSocket(0, instanciasValidas = { it == "principal" })
        srv.start(); assertTrue(srv.esperarListo(5))
        try {
            WsClienteMin("127.0.0.1", srv.port).use { w ->
                w.enviar(mensajeJoin("otra", "dev-1").escribir())
                assertEquals("instancia", w.esperar("error").str("code"))
            }
        } finally { srv.cerrar() }
    }
}
