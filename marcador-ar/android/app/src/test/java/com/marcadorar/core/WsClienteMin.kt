package com.marcadorar.core

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Cliente WebSocket mínimo (solo cliente, texto, sin TLS) — para el test de interop
 * contra ref/server.js sin añadir dependencias. Implementa el handshake RFC 6455 y
 * el enmascarado obligatorio de los frames del cliente.
 *
 * No es código de producción de la app (Android usará OkHttp); es infraestructura
 * de prueba autocontenida.
 */
class WsClienteMin(host: String, port: Int, ruta: String = "/") : AutoCloseable {
    private val socket = Socket(host, port)
    private val inn: InputStream = socket.getInputStream()
    private val out: OutputStream = socket.getOutputStream()
    private val cola = LinkedBlockingQueue<String>()
    private val rnd = java.util.Random()
    @Volatile private var cerrado = false

    init {
        handshake(host, port, ruta)
        Thread({ leerBucle() }, "ws-min-lector").apply { isDaemon = true; start() }
    }

    private fun handshake(host: String, port: Int, ruta: String) {
        // RFC 6455: la clave solo necesita 16 bytes aleatorios en base64 (no verificamos
        // el Sec-WebSocket-Accept de vuelta, así que su contenido es indistinto).
        val bytes = ByteArray(16).also { rnd.nextBytes(it) }
        val clave = Base64.getEncoder().encodeToString(bytes)
        val req = "GET $ruta HTTP/1.1\r\n" +
            "Host: $host:$port\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $clave\r\n" +
            "Sec-WebSocket-Version: 13\r\n\r\n"
        out.write(req.toByteArray(Charsets.US_ASCII)); out.flush()
        // leer cabeceras hasta \r\n\r\n
        val sb = StringBuilder()
        while (!sb.contains("\r\n\r\n")) {
            val b = inn.read()
            if (b < 0) throw IllegalStateException("cierre durante handshake")
            sb.append(b.toChar())
        }
        if (!sb.contains("101")) throw IllegalStateException("handshake rechazado: ${sb.take(40)}")
    }

    private fun leerBucle() {
        try {
            while (!cerrado) {
                val b0 = inn.read(); if (b0 < 0) break
                val b1 = inn.read(); if (b1 < 0) break
                val opcode = b0 and 0x0f
                val enmascarado = (b1 and 0x80) != 0
                var len = (b1 and 0x7f).toLong()
                if (len == 126L) len = ((inn.read() shl 8) or inn.read()).toLong()
                else if (len == 127L) {
                    len = 0
                    repeat(8) { len = (len shl 8) or inn.read().toLong() }
                }
                val clave = ByteArray(4)
                if (enmascarado) inn.readFully(clave)
                val payload = ByteArray(len.toInt())
                inn.readFully(payload)
                if (enmascarado) for (i in payload.indices) payload[i] = (payload[i].toInt() xor clave[i % 4].toInt()).toByte()
                when (opcode) {
                    0x1 -> cola.put(payload.toString(Charsets.UTF_8))   // text
                    0x8 -> { cerrado = true; cola.put("\u0000CLOSE") }  // close
                    0x9 -> enviarFrame(0xA, payload)                    // ping -> pong
                    // 0xA (pong) y binario: ignorar
                }
            }
        } catch (_: Exception) { /* cierre */ }
    }

    private fun InputStream.readFully(b: ByteArray) {
        var off = 0
        while (off < b.size) {
            val n = read(b, off, b.size - off)
            if (n < 0) throw IllegalStateException("stream cortado")
            off += n
        }
    }

    fun enviar(texto: String) = enviarFrame(0x1, texto.toByteArray(Charsets.UTF_8))

    private fun enviarFrame(opcode: Int, payload: ByteArray) {
        val mask = ByteArray(4).also { rnd.nextBytes(it) }
        val len = payload.size
        val hdr = ArrayList<Byte>()
        hdr.add((0x80 or opcode).toByte())
        when {
            len < 126 -> hdr.add((0x80 or len).toByte())
            len < 65536 -> { hdr.add((0x80 or 126).toByte()); hdr.add((len shr 8).toByte()); hdr.add(len.toByte()) }
            else -> { hdr.add((0x80 or 127).toByte()); repeat(8) { i -> hdr.add((len shr (56 - 8 * i)).toByte()) } }
        }
        hdr.addAll(mask.toList())
        val enmascarado = ByteArray(len)
        for (i in 0 until len) enmascarado[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        out.write(hdr.toByteArray()); out.write(enmascarado); out.flush()
    }

    /** Siguiente mensaje de texto, o null si se agota el tiempo. */
    fun tomar(timeoutMs: Long = 4000): String? =
        cola.poll(timeoutMs, TimeUnit.MILLISECONDS)

    /** Espera el siguiente mensaje con el `tipo` dado (devuelve el objeto parseado). */
    fun esperar(tipo: String, timeoutMs: Long = 4000): Json.Obj {
        val fin = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < fin) {
            val m = tomar(fin - System.currentTimeMillis()) ?: break
            val o = parsearJson(m)?.asObj
            if (o != null && o.str("tipo") == tipo) return o
        }
        throw AssertionError("sin mensaje tipo=$tipo en ${timeoutMs}ms")
    }

    override fun close() {
        try { enviarFrame(0x8, ByteArray(0)) } catch (_: Exception) {}
        cerrado = true
        try { socket.close() } catch (_: Exception) {}
    }
}
