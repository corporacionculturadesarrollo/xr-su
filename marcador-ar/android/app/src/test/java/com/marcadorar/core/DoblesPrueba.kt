package com.marcadorar.core

// Dobbles de prueba compartidos: tiempo virtual y red falsa. viven en el sourceset
// de test; no son código de producción.

// ---- tiempo virtual ----

private class Tarea(var en: Long, val cada: Long?, val tarea: () -> Unit)

/** Temporizador virtual: `avanzar(ms)` ejecuta en orden las tareas vencidas. */
class TemporizadorVirtual : Temporizador {
    private var reloj = 0L
    private val tareas = mutableListOf<Tarea>()

    override fun ahora(): Long = reloj
    override fun tras(ms: Long, tarea: () -> Unit): Recurso =
        add(Tarea(reloj + ms, null, tarea))
    override fun cada(ms: Long, tarea: () -> Unit): Recurso =
        add(Tarea(reloj + ms, ms, tarea))

    private fun add(t: Tarea): Recurso {
        tareas.add(t)
        return object : Recurso { override fun cancelar() { tareas.remove(t) } }
    }

    fun avanzar(ms: Long) {
        val fin = reloj + ms
        while (true) {
            val prox = tareas.filter { it.en <= fin }.minByOrNull { it.en } ?: break
            reloj = prox.en
            if (prox.cada != null) prox.en += prox.cada else tareas.remove(prox)
            prox.tarea()
        }
        reloj = fin
    }
    fun pendientes() = tareas.size
}

// ---- red falsa (cliente -> supervisor) ----

class ConexionFalsa(val oyente: OyenteRed) : Transporte {
    val enviado = mutableListOf<String>()
    var cerrada = false
    override fun enviar(texto: String) { enviado.add(texto) }
    override fun cerrar() { cerrada = true }
    fun tiposEnviados(): List<String> =
        enviado.mapNotNull { parsearJson(it)?.asObj?.str("tipo") }
}

class FabricaFalsa : FabricaTransporte {
    val conexiones = mutableListOf<ConexionFalsa>()
    override fun abrir(oyente: OyenteRed): Transporte =
        ConexionFalsa(oyente).also { conexiones.add(it) }
}
