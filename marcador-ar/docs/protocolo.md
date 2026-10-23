# Marcador AR — Especificación de protocolo v1

Sistema de marcador de básquetbol en realidad aumentada. Una sola app con roles.
Estado y mensajes que viajan entre el servidor (teléfono del admin) y los clientes.

## Roles

| Rol | Poder | Alcance |
|---|---|---|
| Super admin | Gestiona ubicaciones e instancias; genera QR firmados | Global |
| Admin de instancia | Único alimenta el resultado. Singleton con heartbeat y renuncia | Su instancia |
| Espectador | Solo lectura: ve el tablero AR, foto/video | Su instancia |

## Instancia

- Una por escenario, identificada por `instanceId` (token firmado dentro del QR).
- Servidor WebSocket embebido en el teléfono del admin de esa instancia.
- Independiente entre sí: estado, roles y red scoped por instancia.
- QR = identidad + dirección del servidor + geometría de anclaje (altura, orientación).

## Configuración de instancia (diálogo SETUP)

Se edita desde el panel del admin y se transmite a todos los clientes.

| Campo | Defecto | Efecto |
|---|---|---|
| `periodMinutes` | 10 | Duración del reloj de período |
| `periods` | 4 | Cantidad de períodos |
| `foulLimit` | 5 | Faltas por período antes del bonus (indicador) |
| `rule24_14` | true | Activa reglas de shot clock: 24 s / 14 s tras rebote ofensivo |
| `homeName` | LOCAL TEAM | Nombre del equipo local |
| `visitorName` | VISITOR TEAM | Nombre del equipo visitante |

`resetAll` (botón RESET ALL VALUES) limpia marcador, faltas, relojes y período.

## Estado de partido (GameState)

```
clockSec        Int      reloj de período (countdown)
shotClockSec    Int      shot clock
period          Int      período actual
running         Bool     relojes corriendo
possession      home|visitor
home  { score: Int, fouls: Int }
visitor { score: Int, fouls: Int }
```

Derivados: `bonus` (fouls >= foulLimit), `periodFinished` (clockSec == 0).

## Shot clock (con rule24_14 activo)

- Corre **automáticamente** junto al reloj (auto-decremento del servidor).
- **Canasta anotada** → 24 s para el otro equipo (possession cambia).
- **Rebote ofensivo** → el admin pulsa RESET 14 (o reset 24 si es mayor ya disponible).
- Flechas ◀ ▶ del tablero: ajuste manual ±1 s por el admin.
- Con `rule24_14 = false` el shot clock queda oculto/inactivo (config de liga local).

## Mensajes WebSocket (JSON)

### Admin → servidor

| Mensaje | Payload | Nota |
|---|---|---|
| `claim` | `{ deviceId }` | Reclama rol admin; si está libre → `ok`, si no → `denied` |
| `resign` | `{}` | Entrega el rol; slot queda libre |
| `heartbeat` | `{}` | Cada 3 s; >10 s sin latido → rol libera solo |
| `setup` | objeto Config completo | Aplica nueva configuración |
| `cmd` | ver comandos | Única vía de escritura del marcador |

Comandos (`cmd.op`):

```
score+  { team: home|visitor, delta: 1|2|3 }
score-  { team }
foul+   { team }    foul- { team }
clock   { op: start|pause|reset, secs? }
shot    { op: reset24|reset14|inc|dec }
period  { op: next|prev|set, value? }
buzzer  { }
state   { op: reset | fullReset }
```

### Servidor → clientes

| Mensaje | Payload |
|---|---|
| `welcome` | `{ role: admin|spectator, config, state }` snapshot inicial |
| `state` | GameState completo (tras cada cmd o tick relevante) |
| `config` | Config completa (tras setup o resetAll) |
| `presence` | `{ adminOnline: Bool, spectators: Int }` |
| `role` | `{ granted: Bool }` aviso de cambio de rol admin |
| `error` | `{ code, message }` |

### Reglas de rol

- `claim` atómico: gana el primero; los demás reciben `denied`.
- Heartbeat del admin cada 3 s; timeout 10 s → `role{granted:false}` broadcast y slot libre.
- `resign` → mismo broadcast inmediato.
- Reconexión con mismo `deviceId` que era el admin → recupera el rol sin claim.

## Sonido (buzzer)

- Buzzer de fin de período y de aviso de bonus: **local del admin** por defecto.
- Extensión futura: flag en Config para emitirlo también en espectadores.

## Panel del admin (zonas táctiles sobre el tablero en vivo)

El admin ve el tablero renderizado y toca zonas invisibles superpuestas (WYSIWYG).
Mapeo de cada zona a su mensaje:

| Zona (posición) | Mensaje |
|---|---|
| SETUP MENU (⚙ arriba-izq) | abre diálogo Config |
| START/PAUSE ×2 (flanquean el reloj) | `cmd{op:"clock", op2:"start\|pause"}` — doble botón espejo por comodidad de agarre |
| BUZZER (🔊 arriba-der) | `cmd{op:"buzzer"}` — dispara el buzzer manual |
| FOUL +1 (sobre cada caja FOULS) | `cmd{op:"foul+", team}` |
| SCORE +1 / SCORE −1 (flanquean cada score) | `cmd{op:"score+", delta:1}` / `cmd{op:"score-", delta:1}` |
| SET 24 SEC (izq del shot clock) | `cmd{op:"shot", op2:"reset24"}` |
| SET 14 SEC (der del shot clock) | `cmd{op:"shot", op2:"reset14"}` |
| PERIOD +1 (bajo el período) | `cmd{op:"period", op2:"next"}` |
| BACK (esquina superior derecha) | sale del panel / navega atrás |

Notas de fidelidad al diseño original:
- El panel expone **FOUL solo +1** por equipo; `foul-` queda en el protocolo para
  corrección (uso interno / futuro long-press).
- **BUZZER** existe como disparo manual además del automático de fin de período.
- El shot clock **corre solo**; SET 24/14 son los resets de posesión, no arranques manuales.

## Vista AR del espectador

1. Escanea el QR del escenario → valida firma → une a la instancia por WebSocket.
2. Ancla el tablero 3D a la geometría del QR (altura y orientación fijas, sin GPS).
3. Tablero de **dos caras** (dot-matrix LED emisivo, fondo negro, marco blanco):
   reloj blanco, shot clock rojo (24/14), period verde, scores amarillos, fouls verdes.
4. Foto y video grabados desde la app con el tablero compuesto.

## No-hacer (v1)

- Sin escritura desde espectadores.
- Sin nube: todo local a la instancia.
- Sin historial de partidos (fase posterior).
