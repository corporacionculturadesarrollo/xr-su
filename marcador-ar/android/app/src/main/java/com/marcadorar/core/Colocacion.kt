package com.marcadorar.core

// Colocación del tablero AR dentro de una instancia — el admin la ajusta con
// controles interactivos (botones + entradas numéricas) y TODOS los espectadores
// la replican. Es relativa al punto de anclaje (el tap de cada espectador),
// salvo `geo`: si está fijada, las vistas AR recrean el ancla directamente en
// esas coordenadas WGS84 y el tablero sobrevive reinicios de la app.
//
// Separada del partido a propósito: mover el tablero no cambia el estado del
// juego, pero sí viaja por el mismo canal (cmd admin-only) y se guarda en la
// instancia.

data class GeoColocacion(
    val lat: Double,
    val lon: Double,
    val alt: Double,  // altura WGS84 (m sobre el elipsoide)
    val rot: Float,   // orientación alrededor de Y-up (grados, marco EUS de ARCore:
                      // X=east, Y=up, Z=south — rotación positiva = horario visto
                      // desde arriba ≈ heading desde el norte)
)

data class Colocacion(
    val ancho: Float = 9f,   // metros de ancho del tablero (cara frontal, medida real de estadio)
    val alto: Float = 6f,    // metros de alto del tablero (cara frontal, medida real de estadio)
    val x: Float = 0f,       // desplazamiento lateral (m)
    val y: Float = 11f,      // altura sobre el ancla (m — un marcador real cuelga a ~11 m del suelo)
    val z: Float = 0f,       // profundidad (m)
    val rot: Float = 0f,     // orientación (grados, eje Y — giro horizontal)
    val inc: Float = 0f,     // inclinación (grados, eje X — pivote adelante/atrás)
    val geo: GeoColocacion? = null,  // fijado geográfico (sobrevive reinicios)
)
