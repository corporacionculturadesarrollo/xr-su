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
2. Edición vía API (**verificada 7 oct 2026**): base `https://platform-api.spheroid.io` con Bearer JWT — **`POST api/file/content/get {appId, fileId}` → modificar (splice) → `POST api/file/content/save {appId, fileId, content}`**. Archivos nuevos: `file/create {appId, fileName}` (el campo `content` se ignora → crear y luego `save`; el árbol de la nube es **plano**, solo nombre) . **Binarios (assets)**: `POST api/file/upload` multipart → campo `files` = archivo + `metadata` = JSON `{appId, directoryId}` (forma real extraída del bundle de Demiurge). `file/delete {appId, fileId}` — ojo: HTTP 200 con `success:false` si falta `appId`.
3. Publicar en **2 fases**: `POST api/publish/startup {appId}` y luego `POST api/publish/publish {appId, publishClient, publishServer, isDebug, layerIds, newLayerTitle}`. El número de build sale de `GET api/app/details?appId=` → `publications[0].appVersion.build`. Publicar requiere visto bueno del usuario.
4. Tras publicar, probar el comportamiento en el dispositivo/app antes de dar por bueno el cambio.

## Límites de la API (verificados 7 oct 2026)

- **Sin reloj**: `Date()` solo tiene `<init>()` + `toString()` (formato opaco); `String` solo `toString()` (sin split/substring/parseo); `Long()/Double()` sin argumentos; SQL propio sin `datetime('now')` → **no hay forma de leer la hora actual en números**. Workaround: inyectar hora/día como `var` y pedir la feature a Spheroid (junto con la de luz).
- **math** solo trae `abs, cos, sin, sqrt, PI` — sin `tan/atan2/asin/acos`. `atan2` se implementa con doble reducción de argumento + Taylor (validado: error < 1e-11) — patrón listo en `exposicion-ccd/client/Sun.spheroid`.
- **Sin API de luz**: `client.ar` no tiene `Light`; nodos sin color/material/opacidad ni `isVisible` (ocultar = `scale = Vector3(0)`); `Scene` solo `camera`; `Color` es de UI.
- **Nodos** (`spheroid.client.ar`): `Node` = children, eulerAngles(var), location(var: LatLng?), position(var), rotation, scale(var), worldPosition; `ModelNode` añade **`model` (var legible)** → discriminador perfecto si hay varios modelos en `geoAnchor` (los paneles/botones deben filtrar `child.model == stationModel` para ignorar nodos auxiliares como sombras).
- `Timer.schedule(period | delay | delay+period, callback)` → `Timer.schedule(period = TimeInterval(seconds = 10)) { ... }` (companion, como `Location.onChange`).
- **Ejes del mundo georreferenciado** (derivado de `placeHere` en ExposicionCCD): **+x = Este, +z = Sur, +y = arriba** (norte = −z). Azimut desde el norte en sentido horario (0=N, 90=E) → dirección de sombra `(x, z) = (−sin az, cos az)`; giro del eje largo (que va en +x) = `atan2(−cos az, −sin az)`.

## Prototipo de luz ambiental (fase 1: sombra solar)

- Estudio base: **Zhu, Liu, Sun, Yuan, Wu (2023)**, *A Lighting Consistency Technique for Outdoor AR Based on Multi-Source Geo-Information*, ISPRS IJGI 12(8):324 (CC BY): sol por lat/lng/hora (SPA/NOAA) → coherencia de luz en AR outdoor sin marcadores. Complementos: ARCore Environmental HDR, WebXR Lighting Estimation, SunCalc.
- `Sun.spheroid` (cliente): `solAzimutElevacion(lat, lng, horaUtc, diaAnio)` → `dynamicOf(az, elev)` con el NOAA simplificado (ano fraccional → ecuación del tiempo + declinación → vector sol E/N/U → atan2 propio); `giroSombra(az)`; `largoSombra(elev)` = 1/tan recortada a [1, 6], y < 3° → 0 (noche).
- Sombra = **GLB propio** (`assets/sombra_sol.glb`, ~6,4 KB): elipse en el plano xz con **vertex colors alfa** (alpha = 1−r², anillos 0.5/0.85/1, 48 segmentos) + material negro `alphaMode: "BLEND"`, `doubleSided` — generada con un script Node (buffer JSON+BIN, accessors VEC3/VEC4/SCALAR). Ocultar de noche con `scale = Vector3(0)`.
- Hora: placeholders `horaSolUtc`/`diaAnioSol` + `modoDemoSol` (el Timer avanza 1 h/10 s → día completo en 4 min, visible en dispositivo); `println("RELOJ:" + Date())` para capturar el formato real si la consola del IDE lo muestra.
- **Fase 2** (pendiente de API de luz/reloj): variantes tintadas del GLB por franja horaria (`unclavo_soc_{franja}.glb`) + `ModelNode.model` settable para intercambiarlas.

## Reglas de la casa

- Contenido XR **solo institucional y aprobado** (textos, coordenadas, modelos pasan por aprobación).
- Nada de `publish` (ni push de repo) sin aprobación explícita.
- PowerShell 5.1: **no existe `&&`** (usar `;`); si hace falta npm → `cmd /c npm ...`.
- El proyecto **ExposicionCCD está en pausa**: para todo lo específico de esa app (IDs, estaciones, pendientes) usar el skill **App XR ExposicionCCD**.
