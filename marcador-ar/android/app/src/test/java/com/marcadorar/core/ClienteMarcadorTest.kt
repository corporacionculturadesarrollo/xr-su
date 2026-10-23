package com.marcadorar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Transporte falso: captura lo que el cliente envía, sin red.
class TransporteFalso : Transporte {
    val enviado = mutableListOf<String>()
    override fun enviar(texto: String) { enviado.add(texto) }
    override fun cerrar() {}
}

class ClienteMarcadorTest {

    private fun cliente(): Pair<ClienteMarcador, TransporteFalso> {
        val c = ClienteMarcador("principal", "dev-1")
        val t = TransporteFalso()
        c.transporte = t
        return c to t
    }

    @Test fun `join envia instancia y deviceId`() {
        val (c, t) = cliente()
        c.join()
        val m = parsearJson(t.enviado.single()).asObj
        assertEquals("join", m.str("tipo"))
        assertEquals("principal", m.str("instancia"))
        assertEquals("dev-1", m.str("deviceId"))
    }

    @Test fun `por defecto es espectador y no puede accionar`() {
        val (c, _) = cliente()
        assertFalse(c.accion(Cmd.ScoreMas(Team.HOME)))
        assertFalse(c.enviarSetup(Config()))
    }

    @Test fun `tras role granted puede accionar y envia cmd`() {
        val (c, t) = cliente()
        c.onMensaje("""{"tipo":"role","granted":true}""")
        assertEquals("admin", c.rol)
        assertTrue(c.accion(Cmd.FoulMas(Team.HOME)))
        val m = parsearJson(t.enviado.last()).asObj
        assertEquals("cmd", m.str("tipo"))
        assertEquals("foul+", m.campo("cmd").asObj.str("op"))
    }

    @Test fun `welcome fija rol config y estado y dispara callbacks`() {
        val (c, _) = cliente()
        var estadoRecibido: Estado? = null
        var configRecibida: Config? = null
        c.onEstado = { estadoRecibido = it }
        c.onConfig = { configRecibida = it }
        c.onMensaje(
            """{"tipo":"welcome","rol":"admin",
               "config":{"periodMinutes":8,"homeName":"TIGRES"},
               "state":{"clockSec":480,"shotClockSec":24,"period":1,"running":false,
                        "possession":"home","home":{"score":0,"fouls":0},
                        "visitor":{"score":0,"fouls":0}}}"""
        )
        assertEquals("admin", c.rol)
        assertEquals(8, c.config.periodMinutes)
        assertEquals(480, c.estado.clockSec)
        assertEquals(480, estadoRecibido?.clockSec)
        assertEquals("TIGRES", configRecibida?.homeName)
    }

    @Test fun `state actualiza estado local`() {
        val (c, _) = cliente()
        c.onMensaje(
            """{"tipo":"state","state":{"clockSec":300,"shotClockSec":10,"period":2,"running":true,
               "possession":"visitor","home":{"score":5,"fouls":1},"visitor":{"score":7,"fouls":2}}}"""
        )
        assertEquals(300, c.estado.clockSec)
        assertEquals(Team.VISITOR, c.estado.possession)
        assertEquals(7, c.estado.visitor.score)
        assertTrue(c.estado.running)
    }

    @Test fun `role denied degradado a espectador con motivo`() {
        val (c, _) = cliente()
        c.onMensaje("""{"tipo":"role","granted":true}""")
        var motivo: String? = null
        c.onRol = { _, m -> motivo = m }
        c.onMensaje("""{"tipo":"role","granted":false,"motivo":"ocupado"}""")
        assertEquals("espectador", c.rol)
        assertEquals("ocupado", motivo)
        assertFalse(c.accion(Cmd.Buzzer)) // ya no admin
    }

    @Test fun `buzzer shot-expired y presencia disparan callbacks`() {
        val (c, _) = cliente()
        var buzzer = 0; var shot = 0; var pres: Pair<Boolean, Int>? = null
        c.onBuzzer = { buzzer++ }
        c.onShotExpired = { shot++ }
        c.onPresencia = { a, e -> pres = a to e }
        c.onMensaje("""{"tipo":"buzzer"}""")
        c.onMensaje("""{"tipo":"shot-expired"}""")
        c.onMensaje("""{"tipo":"presence","adminOnline":true,"espectadores":4}""")
        assertEquals(1, buzzer)
        assertEquals(1, shot)
        assertEquals(true to 4, pres)
        assertTrue(c.adminOnline)
        assertEquals(4, c.espectadores)
    }

    @Test fun `onDesconectado degrada a espectador`() {
        val (c, _) = cliente()
        c.onMensaje("""{"tipo":"role","granted":true}""")
        c.onDesconectado()
        assertFalse(c.conectado)
        assertEquals("espectador", c.rol)
    }

    @Test fun `json ilegible devuelve null, tipo desconocido no traba`() {
        val (c, _) = cliente()
        assertEquals(null, c.onMensaje("no json"))
        // un tipo desconocido se tipa como Desconocido (no null) y no traba
        assertEquals(Entrada.Desconocido("desconocido"),
            c.onMensaje("""{"tipo":"desconocido"}"""))
    }
}
