---
name: App XR ExposicionCCD
description: Proyecto ExposicionCCD en Spheroid Universe — estaciones georreferenciadas de la Corporación Cultura y Desarrollo. Actívalo para retomar, editar, añadir estaciones o publicar este proyecto específico, y cuando se mencione ExposicionCCD, el appId a8c6da8a, las estaciones de Bogotá/Ibagué, el staging exposicion-ccd o el modelo unclavo_soc.glb.
slash: true
---

# ExposicionCCD — exposición XR de la Corporación

## Estado: PAUSADA ⚠️

- **Pausa ordenada por el usuario** ("pausa en este proyecto"). El build **(35)** está publicado y probado sin errores.
- No retomar, editar ni publicar **sin orden explícita** del usuario.
- Contenido XR **solo institucional aprobado**: textos y coordenadas pasan por aprobación antes de entrar.

## Identificadores

- **appId**: `a8c6da8a-abd9-4cfe-84a3-819d20000603`
- IDs de archivo (⚠️ parciales — recuperar los completos del panel de Spheroid al reanudar):
  - client: `94fae9ab-…`
  - GetStationsAction: `539d8322-…`
  - Stations: `0862e679-…`
  - app.json: `198d093e-…`

## Carpeta del proyecto

`C:\Users\kutzm\Documents\GitHub\xr-su\exposicion-ccd\`

- Guardada en disco fijo (antes vivía en Temp, que se puede borrar); copia de seguridad de los skills en `xr-su\skills\`.
- ⚠️ **Puede estar desfasada del servidor** (la nube manda). Antes de editar: leer el contenido real con `file/content/get`.

| Archivo | Papel |
| --- | --- |
| `app.json` | Manifiesto (listas server/client + versión) |
| `client/Client.spheroid` | Carga assets, escucha Location, pide estaciones, coloca nodos+marcadores, onTap → Notification |
| `server/Stations.spheroid` | Catálogo `STATIONS` (id, lat, lng, title, text) |
| `server/GetStationsAction.spheroid` | `@Action getStations` — v1 devuelve todas; v2 pendiente: filtrar por radio |
| `server/StartupAction.spheroid` | `@Action startup` — mensaje de bienvenida |
| `assets/estacion.glb`, `map_icon.png`, `notification_icon.png` | Modelo de estación, iconos de mapa y notificación |

## Estaciones

**Actuales = placeholders** (centro de cada ciudad, textos genéricos):

- `bogota`: 4.7108, -74.0721
- `ibague`: 4.4389, -75.2322

**Pendientes (bloquean la reanudación):**

1. Reemplazar cada lat/lng por la coordenada **exacta de un Space real** de la Corporación (Spheroid marketplace → *My spaces*).
2. Textos institucionales **aprobados** (los actuales son de relleno: "Estación de la Corporación… culturaydesarrollo.org").
3. Ciudades previstas sin coordenadas ni texto: París, Londres, Moscú, Pekín, Washington…

## Flujo al reanudar (checklist)

1. Confirmar con el usuario la reanudación (sigue pausada hasta entonces).
2. Recuperar del panel de Spheroid: IDs completos, endpoint/auth de la API (`file/content/get` → `save` → `publish`) y estado de builds.
3. Leer el estado real de los 4 archivos (`file/content/get`), comparar con el staging.
4. Aplicar cambios → `save` → probar → `publish` **con visto bueno**; anotar el build nuevo.
5. Actualizar este skill con lo que cambie (IDs, estados, coordenadas).

## Assets disponibles

- `C:\Users\kutzm\Desktop\unclavo_soc.glb` (4,85 MB) — modelo 3D candidato; verificar si aplica al proyecto antes de usarlo.

## Fuera de aquí

Sintaxis, patrones de código y flujo general de Spheroid → skill **Desarrollo XR con Spheroid Script**.
