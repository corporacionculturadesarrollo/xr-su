package com.marcadorar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Los dobles (TemporizadorVirtual, FabricaFalsa) viven en DoblesPrueba.kt.

class SupervisorTest {

    private fun escenario(): Triple<Supervisor, FabricaFalsa, TemporizadorVirtual> {
        val fab = FabricaFalsa()
        val temp = TemporizadorVirtual()
        val cliente = ClienteMarcador("principal", "dev-1")
        val sup = Supervisor(fab, cliente, temp)
        return Triple(sup, fab, temp)
    }

    /** Abre la conexión y fija el rol (admin si `granted`). */
    private fun abrirYAdmin(sup: Supervisor, fab: FabricaFalsa, granted: Boolean): ConexionFalsa {
        sup.iniciar()
        val c = fab.conexiones.last()
        c.oyente.alConectar()
        if (granted) c.oyente.alRecibir("""{"tipo":"role","granted":true}""")
        return c
    }

    @Test fun `al conectar manda join`() {
        val (sup, fab, _) = escenario()
        val c = abrirYAdmin(sup, fab, granted = false)
        assertTrue("debería enviar join al abrir", c.tiposEnviados().contains("join"))
        assertTrue(sup.cliente.conectado)
    }

    @Test fun `no late siendo espectador`() {
        val (sup, fab, temp) = escenario()
        val c = abrirYAdmin(sup, fab, granted = false)
        val antes = c.enviado.size
        temp.avanzar(9000) // varios intervalos de latido
        assertEquals("sin claim no se envía latido", antes, c.enviado.size)
    }

    @Test fun `tras claim late cada latidoMs`() {
        val (sup, fab, temp) = escenario()
        val c = abrirYAdmin(sup, fab, granted = true)
        assertEquals("admin", sup.cliente.rol)
        temp.avanzar(3000)
        assertEquals(1, c.tiposEnviados().count { it == "heartbeat" })
        temp.avanzar(6000) // 2 latidos más
        assertEquals(3, c.tiposEnviados().count { it == "heartbeat" })
    }

    @Test fun `al caer degrada, para el latido y reconecta tras la espera`() {
        val (sup, fab, temp) = escenario()
        abrirYAdmin(sup, fab, granted = true)
        val conexionesAntes = fab.conexiones.size

        fab.conexiones.last().oyente.alDesconectar()
        assertFalse(sup.cliente.conectado)
        assertEquals("espectador", sup.cliente.rol) // degradado al caer

        temp.avanzar(2500) // espera fija de reconexión
        assertEquals("debería abrir una conexión nueva", conexionesAntes + 1, fab.conexiones.size)
    }

    @Test fun `reconectar vuelve a mandar join`() {
        val (sup, fab, temp) = escenario()
        abrirYAdmin(sup, fab, granted = true)
        fab.conexiones.last().oyente.alDesconectar()
        temp.avanzar(2500)
        val nueva = fab.conexiones.last()
        nueva.oyente.alConectar()
        assertTrue(nueva.tiposEnviados().contains("join"))
    }

    @Test fun `perder el rol por timeout deja de latir`() {
        val (sup, fab, temp) = escenario()
        val c = abrirYAdmin(sup, fab, granted = true)
        c.oyente.alRecibir("""{"tipo":"role","granted":false,"motivo":"timeout"}""")
        assertEquals("espectador", sup.cliente.rol)
        val heartbeatsTrasPerder = c.tiposEnviados().count { it == "heartbeat" }
        temp.avanzar(9000)
        assertEquals("sin rol no se late más", heartbeatsTrasPerder,
            c.tiposEnviados().count { it == "heartbeat" })
    }

    @Test fun `detener cancela timers y cierra la conexión`() {
        val (sup, fab, temp) = escenario()
        val c = abrirYAdmin(sup, fab, granted = true)
        sup.detener()
        assertTrue("debería cerrar la conexión", c.cerrada)
        assertFalse(sup.cliente.conectado)
        assertEquals("no deben quedar timers", 0, temp.pendientes())
    }

    @Test fun `tras detener, un alDesconectar tardío no reconecta`() {
        val (sup, fab, temp) = escenario()
        val c = abrirYAdmin(sup, fab, granted = true)
        val antes = fab.conexiones.size
        sup.detener()
        // el cierre del transporte dispara alDesconectar (como OkHttp haría tras close)
        c.oyente.alDesconectar()
        temp.avanzar(10000)
        assertEquals("no debe reabrir tras detener", antes, fab.conexiones.size)
        assertEquals("no deben quedar timers", 0, temp.pendientes())
    }

    @Test fun `los comandos del admin viajan por la conexión viva`() {
        val (sup, fab, _) = escenario()
        val c = abrirYAdmin(sup, fab, granted = true)
        assertTrue(sup.cliente.accion(Cmd.ScoreMas(Team.HOME, delta = 2)))
        val cmd = parsearJson(c.enviado.last()).asObj.campo("cmd").asObj
        assertEquals("score+", cmd.str("op"))
        assertEquals(2, cmd.int("delta"))
    }
}
