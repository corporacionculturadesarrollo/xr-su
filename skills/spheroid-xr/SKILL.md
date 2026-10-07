---
name: Desarrollo XR con Spheroid Script
description: Sintaxis de Spheroid Script, estructura app.json (client/server/assets), patrones de código XR (GeoAnchor, Location, Server.action, animaciones) y flujo de edición/publicación en Spheroid Universe y Demiurge. Actívalo siempre que aparezca código .spheroid, la app ExposicionCCD, builds o publicación en Spheroid, o preguntas sobre cómo se programa en Spheroid Script — aunque solo sea "cómo se hace X en XR".
slash: true
---

# Spheroid Script / Spheroid Universe

## Qué es (30 segundos)

- **Spheroid Script**: lenguaje de Spheroid Universe, estilo Kotlin, corre en **cliente y servidor**, orientado a XR (Android/iOS). Ventaja clave oficial: los cambios sobre una app **ya publicada** se aplican al instante (no hay builds de minutos).
- Se desarrolla en el IDE en la nube **Spheroid Demiurge**; las apps corren dentro de la superapp **XR Hub** o como app propia.
- Publicar desde Demiurge = despliegue real → **requiere visto bueno del usuario** (regla de la casa).

## Estructura del proyecto (plana, en la raíz)

```
mi-app/
├── app.json            # manifiesto: listas "server" y "client" + "version"
├── client/
│   └── Client.spheroid
├── server/
│   ├── StartupAction.spheroid
│   ├── GetStationsAction.spheroid
│   └── Stations.spheroid        # datos sueltos, sin @Action
└── assets/                       # .glb, .png → Source("/archivo.ext")
```

`app.json` (observado en ExposicionCCD):

```json
{
  "server": ["./Stations.spheroid", "./StartupAction.spheroid"],
  "client": ["./Client.spheroid"],
  "version": "1.0.0"
}
```

- La estructura se mantiene **plana en la raíz** (patrón conservado del proyecto; no subcarpetar de más).
- Los assets se referencian con `Source("/nombre.ext")` (raíz de `assets/`).

## Sintaxis esencial (doc oficial resumida)

- Punto de entrada: `fun main() { ... }`
- `val` = inmutable (asignable una vez), `var` = mutable; también existen **variables a nivel superior** (top-level) que las funciones comparten.
- Comentarios: `// fin de línea` y `/* bloque */`
- Templates de cadena: `"$variable"` y `"${expresion}"`
- `if/else` (también como expresión: `return if (c) a else b`), `for (x in lista)`, rangos `for (x in 1..5)`, `listOf(...)`
- Las firmas no llevan tipos explícitos: `fun sum(a, b) { return a + b }`

Referencias oficiales (consultar ante dudas, no memorizar):

- Sintaxis: `https://spheroiduniverse.github.io/SpheroidScript/basic-syntax.html`
- API Reference (paquetes: `spheroid`, `.client`, `.client.ar`, `.client.ui`, `.collections`, `.math`, `.server`): `https://spheroiduniverse.github.io/SpheroidScript/reference/`
- Ejemplos: `github.com/SpheroidUniverse/SpheroidScript/tree/master/examples` — **CoinQuest** es un juego AR georreferenciado: el patrón más parecido al nuestro.

## Patrones cliente (validados en ExposicionCCD, build 35)

- **Carga de assets con callbacks anidados** (`model.load { success, error -> ... }`) y `handleError` centralizado; el `callback` se invoca solo cuando todo cargó.
- **Gate de ubicación**: no colocar nada hasta `Location.onCorrect { ... }` (flag `isLocationCorrect`); `Location.onChange(threshold = Distance(meters = 5))` refresca cuando el usuario se mueve.
- **Limpieza en `Location.onUninit`**: `geoAnchor.children.clear()`, `Map.markers.clear()`, vaciar el set de IDs — si no, quedan estaciones huérfanas.
- **Deduplicación**: `stationIds = mutableSetOf()` y `if (!stationIds.add(station.id)) return` — evita duplicar nodos en cada refresco.
- **Colocación georreferenciada**:

  ```kotlin
  val node = ModelNode(model = stationModel, scale = Vector3(1.5), position = Vector3(y = -1))
  node.location = LatLng(station.lat, station.lng)
  geoAnchor.children.add(node)
  Map.markers.add(MapMarker(location = LatLng(station.lat, station.lng), icon = mapIcon))
  ```

- **Animación de rotación**: `RotationAnimation(by = Vector3(y = 360), duration = TimeInterval(seconds = 8), interpolation = "linear")` → `node.getAnimationController(girar).play(loop = true)`.
- **Interacción**: `node.onTap { Notification.show(icon = notificationIcon, text = "...") }`.
- **Servidor desde cliente**: `Server.action("nombre") { response, error -> ... }` con manejo de `error` siempre.

## Patrones servidor

- `@Action` sobre la función → se invoca con `Server.action("nombre")` (el nombre es el de la función):

  ```kotlin
  @Action
  fun getStations() {
      if (!Client.location)
          return Exception("Location is not initialized.")
      return GetStationsResult(STATIONS)
  }
  ```

- Guard con `!Client.location` cuando la acción necesita la posición del usuario.
- Respuestas envueltas en un helper `XxxResult(...)` que devuelve `dynamicOf(campo = valor)` — patrón repetido en todas las acciones.
- Los **datos** (catálogos) viven en archivos propios sin `@Action` (p. ej. `Stations.spheroid` con `val STATIONS = listOf(dynamicOf(...))`).

## Flujo de edición y publicación

1. La **nube es la fuente de verdad**; el staging local puede estar desfasado → siempre leer el contenido real antes de editar.
2. Edición vía API: **`file/content/get` → modificar (splice) → `save` → `publish`**. (El detalle exacto de endpoint/auth no quedó registrado: verificarlo al reanudar desde el panel de Spheroid.)
3. `publish` = build; los builds se **numeran** (ExposicionCCD acumula 1–35). Publicar requiere visto bueno del usuario.
4. Tras publicar, probar el comportamiento en el dispositivo/app antes de dar por bueno el cambio.

## Reglas de la casa

- Contenido XR **solo institucional y aprobado** (textos, coordenadas, modelos pasan por aprobación).
- Nada de `publish` (ni push de repo) sin aprobación explícita.
- PowerShell 5.1: **no existe `&&`** (usar `;`); si hace falta npm → `cmd /c npm ...`.
- El proyecto **ExposicionCCD está en pausa**: para todo lo específico de esa app (IDs, estaciones, pendientes) usar el skill **App XR ExposicionCCD**.
