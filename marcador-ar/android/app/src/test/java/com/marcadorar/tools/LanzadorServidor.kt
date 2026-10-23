package com.marcadorar.tools

import com.marcadorar.app.net.ServidorWebSocket

/**
 * Lanzador de desarrollo (source set de test — NO se empaqueta en el APK).
 * Arranca el servidor WebSocket embebido de Kotlin y se queda escuchando, para
 * poder probar el tablero `preview/marcador.html` contra el servidor de Kotlin
 * en un navegador real (HTML ↔ servidor Kotlin, por socket de verdad).
 *
 * Uso: `PUERTO=8897 ./gradlew servidorKotlin` (o sin variable, 8897) y luego abrir
 * `preview/marcador.html?server=ws://127.0.0.1:<PUERTO>&instancia=principal`.
 */
fun main() {
    val puerto = System.getenv("PUERTO")?.toIntOrNull() ?: 8897
    val srv = ServidorWebSocket(puerto, instanciasValidas = { true })
    srv.start()
    if (!srv.esperarListo(5)) {
        System.err.println("No se pudo escuchar en el puerto $puerto")
        return
    }
    println("Servidor Kotlin embebido escuchando en ws://127.0.0.1:$puerto (toda instancia válida)")
    Runtime.getRuntime().addShutdownHook(Thread { srv.cerrar() })
    Thread.currentThread().join()   // bloquea indefinidamente
}
