# mcp-spheroid

MCP server local para trabajar con la app **ExposicionCCD** de Spheroid Universe
desde OpenCode. Registrado globalmente como servidor `spheroid`
(`~/.config/opencode/opencode.json`).

## Herramientas

| Herramienta | Qué hace | Requiere nube |
| --- | --- | --- |
| `status` | Diagnóstico de configuración (siempre disponible) | no |
| `list_files` | Lista los archivos del proyecto local | no |
| `read_file` | Lee un archivo: de la nube si hay ID, si no de la copia local (avisando que puede estar desfasada) | opcional |
| `save_file` | Guarda contenido completo (`target: cloud` \| `local`) | cloud: sí |
| `publish` | Publica (build) la app — **solo con aprobación explícita del usuario** | sí |
| `list_builds` | Lista los builds publicados | sí + `SPHEROID_ENDPOINT_BUILDS` |

## Configuración (variables de entorno)

Mientras falten, las herramientas de nube rechazan con la lista exacta de
pendientes — nada falla a ciegas.

```sh
SPHEROID_API_BASE      # URL base de la API de Spheroid        (PENDIENTE)
SPHEROID_TOKEN         # token de acceso                        (PENDIENTE)
SPHEROID_FILE_IDS      # JSON: {"server/Stations.spheroid": "<id completo>", ...}  (PENDIENTE)
SPHEROID_ENDPOINT_BUILDS   # endpoint de builds (no documentado; por defecto vacío)
# Opcionales:
SPHEROID_APP_ID        # por defecto a8c6da8a-abd9-4cfe-84a3-819d20000603
SPHEROID_AUTH_HEADER   # por defecto Authorization
SPHEROID_AUTH_SCHEME   # por defecto Bearer ("" = valor crudo)
SPHEROID_ENDPOINT_GET      # por defecto file/content/get
SPHEROID_ENDPOINT_SAVE     # por defecto save
SPHEROID_ENDPOINT_PUBLISH  # por defecto publish
SPHEROID_LOCAL_PROJECT     # por defecto ../exposicion-ccd
```

En Windows: `setx SPHEROID_API_BASE "https://..."` (y reiniciar OpenCode para
que el servidor herede el entorno), o añadir el mapa `environment` con
sustitución `{env:...}` en la entrada `spheroid` del opencode.json.

## Qué falta (recuperar al reanudar el proyecto)

1. **URL base y auth** de la API que usa Demiurge (flujo
   `file/content/get` → `save` → `publish`): capturarlas al abrir el panel
   (pestaña Network del navegador) o de la documentación interna de Spheroid.
2. **IDs completos** de los 4 archivos — en el skill solo quedaron parciales
   (`client 94fae9ab-…`, `GetStationsAction 539d8322-…`, `Stations 0862e679-…`,
   `app.json 198d093e-…`).
3. **Endpoint de builds** (la numeración 1–35 se ve en el panel).
4. Verificar el **cuerpo exacto** de cada llamada: el cliente envía
   `{appId, id, path, content}` en POST JSON; si la API responde distinto,
   ajustar en `src/index.js`.

## Desarrollo

```sh
npm install
npm test        # prueba de humo: conecta, lista herramientas, llama a status/read/save
npm start       # arranca por stdio (lo hace OpenCode, no a mano)
```
