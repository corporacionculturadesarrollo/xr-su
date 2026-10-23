# Evaluación: ¿montar el marcador AR sobre Spheroid Script (Stack B)?

Contraste de los requisitos del protocolo congelado
([`protocolo.md`](protocolo.md)) contra la API de Spheroid Script documentada
en [`../../spheroid-api/lenguaje/`](../../spheroid-api/lenguaje/).
Fecha de la evaluación: corpus `spheroid-api` v1 (oct 2026).

## Veredicto corto

**Spheroid Script resuelve la parte difícil del "dónde va el tablero"
(anclaje AR georreferenciado), pero tiene huecos críticos para el tiempo real
y la captura foto/video con overlay.** El Stack A (Kotlin+ARCore), ya
avanzado con 17 pruebas en verde, sigue siendo el plan A para el producto
completo. Stack B sirve como referencia de anclaje o para un MVP acotado.

## Requisito por requisito

| Requisito del protocolo | ¿Spheroid Script lo cubre? | Fuente / hueco |
|---|---|---|
| Tablero 3D flotando **fijo en el venue** (coordenadas predeterminadas) | ✅ **Sí, fuerte** | `GeoAnchor.default`, `node.location = LatLng(...)`, `ModelNode` — es justamente el fuerte de la plataforma |
| **Dos caras** del tablero (ambos lados del venue) | ✅ Sí | Dos `ModelNode` con rotación opuesta |
| **Réplica en tiempo real** admin→espectadores | ⚠️ **Hueco crítico** | El registro expone `Server.action` (request/response) **sin push ni WebSocket**. El `WebSocketProvider` existe en `infrastructure` pero **no** está en el API de 295 términos. Solo queda **polling con `Timer`** → latencia alta + batería |
| Dígitos vivos (reloj, marcador) sobre el modelo 3D | ⚠️ **Hueco** | `ModelNode` renderiza `.glb` estáticos; **no** hay texto/ni díntámico en 3D. Habría que generar dígitos como geometría 3D o superponer UI 2D (fuera de la escena) |
| Panel del admin para alimentar resultados | ✅ Sí | Motor UI (`Page`, `Button`, `TextField`) + `@Action` |
| **Captura foto/video con el overlay** del tablero | ❌ **No documentado** | El registro **no** expone captura de cámara ni guardado a galería (servicios internos `Video`/`Camera` no son API de scripting) |
| Escanear **QR** (identidad de instancia) | ❌ **No documentado** | No hay API de QR/escaneo en los 295 términos |
| **Instancias múltiples** + rol admin singleton | ✅ Lógica sí | `@Action` + Spheroid SQL (`[07-sql.md]`) pueden modelar instancias y singleton; pero sin push, la confirmación sería lenta |
| Buzzer / sonidos | ✅ Sí | `Audio(source=...)`, `SceneAudio` |

## Conclusión

- **Stack B es ideal para**: anclar un tablero XR en coordenadas del venue —
  la capacidad diferencial de la plataforma y lo más laborioso de posicionar.
- **Stack B no cubre (hoy, en el API público)**: tiempo real push, dígitos
  dinámicos en 3D, captura foto/video con overlay, escaneo QR. Cubrirlos en
  Spheroid obligaría a hacks (polling costoso, geometría de dígitos) o a
  APIs internas no documentadas (riesgo de romperse en actualizaciones).

### Recomendación

1. **Conservar Stack A (Kotlin+ARCore) como producto completo** — ya tiene la
   especificación probada (reducer + roles + 17 pruebas) y cubre nativamente
   tiempo real (WebSocket), render de dígitos, captura CameraX+GL y QR.
2. **Usar Stack B como complemento**, no reemplazo: si se quiere publicar en
   el ecosistema Spheroid (distribución vía XR Hub sin redistribuir APK), un
   **MVP acotado** de "tablero anclado de solo lectura" es viable con
   `GeoAnchor` + `Timer` polling + `@Action` para el estado. La captura y el
   QR quedarían fuera o en el cliente nativo.
3. **No migrar el protocolo congelado**: `protocolo.md` y el reducer son
   agnósticos al stack; si algún día Spheroid expone tiempo real y captura,
   el mismo protocolo se porta a `.spheroid`.

Detalle de huecos y su severidad en
[`../../spheroid-api/lenguaje/08-trampas.md`](../../spheroid-api/lenguaje/08-trampas.md).
