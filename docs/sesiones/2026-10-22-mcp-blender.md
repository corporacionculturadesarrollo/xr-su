# MCP de Blender instalado (comunitario mcp-for-blender)

**Estado:** cerrado · **Inicio:** 2026-10-22 · **Última:** 2026-10-22

## 00:30 — Instalación completa y prueba end-to-end

**Qué se hizo** — Instalación del MCP **comunitario `mcp-for-blender`** (ahujasid): `uv` 0.13.4 vía winget, addon `blender_mcp.py` en Blender 5.2.2 LTS **habilitado en preferencias** (auto-start del socket en `localhost:9876`), Blender lanzado y prueba end-to-end por stdio: servidor **v1.30.0**, **9 tools** (`execute_blender_code`, `look`, `get_scene_info`, `generate_3d`, `search_assets`, `import_asset`…) y `get_addon_status` respondiendo desde Blender (addon v1.8, protocolo 14). Entrada `blender-mcp` añadida a `opencode.json` global con respaldo.

**Decisiones**
- Comunitaria (ahujasid), **no** la oficial de la Blender Foundation (5.1+, sin guardas de red, orientada `.mcpb`) **porque** la comunitaria tiene soporte explícito de OpenCode, `BLENDER_MCP_SAFE_MODE=1` y `DISABLE_TELEMETRY=true`. Una sola variante a la vez.
- `command` con la **ruta absoluta** de `uvx` (`…\WinGet\Packages\astral-sh.uv_…\uvx.exe`), **no** `cmd /c uvx` **porque** los procesos de GUI no heredan el PATH que winget actualizó en tiempo de sesión.
- Safe mode activo **porque** bloquea red/archivos/procesos a scripts del agente sin tocar modelado, materiales, guardar e import/export de GLB.
- Respaldo del config: `C:\Users\kutzm\.config\opencode\opencode.json.bak-20261022` antes de editar.
- Blender se lanzó desprendido vía `Win32_Process.Create` (WMI) **porque** los procesos que lanza el agente mueren con sus shells.

**Datos para retomar**
- El addon atiende **un cliente MCP a la vez**: no usar OpenCode y OpenDesign contra el mismo Blender simultáneamente.
- `generate_3d`: acepta `image` = **ruta absoluta o URL** (las fotos pegadas en el chat no se pueden pasar); proveedores auto/tripo/hunyuan3d/hyper3d/meshy; clave de prueba gratuita de Hyper3D Rodin = `vibecoding` (checkbox *Use Hyper3D Rodin* en el panel de Blender); 1 objeto texturizado por llamada, 1–3 min.
- OpenDesign (workspace web/2D instalado en el equipo) llega a este mismo Blender **a través de su agente conectado, OpenCode**, vía la entrada `open-design` del `opencode.json`.

**Pendientes**
- [x] **Reiniciar OpenCode** — resuelto: la sesión del 22 oct ya cargó `blender-mcp` (9 tools activas, prueba E2E en vivo).
- [x] **Prueba real de `generate_3d`** — probado con foto real: requiere generador con saldo (la trial `vibecoding` está agotada → `API_INSUFFICIENT_FUNDS`).

**Verificación**
```powershell
# Blender abierto → socket escuchando
Get-NetTCPConnection -LocalPort 9876 -State Listen
# config global con blender-mcp
node -e "console.log(Object.keys(JSON.parse(require('fs').readFileSync('C:/Users/kutzm/.config/opencode/opencode.json','utf8')).mcp))"
```

**Errores míos** — En el `--python-expr` que habilitó el addon concatené `str + bool` en el `print` (TypeError, exit 1); el `addon_enable` y el `save_userpref` se ejecutaron antes, así que la preferencia quedó guardada igualmente. Detectado en la salida del propio comando y corregido con una expresión de verificación aparte (`ADDON-EN-PREFS: True`).

## 09:15 — Primer modelo desde foto: IA sin saldo, modelado manual rechazado

**Qué se hizo** — `generate_3d` probado end-to-end con una foto real (`Nail original.jpg`, martillo+clavo sobre bloque): sin generador habilitado dio un error claro; habilitada la clave de prueba Rodin (`vibecoding`, panel del addon) devolvió `API_INSUFFICIENT_FUNDS` — trial compartida agotada. Se construyó entonces una réplica procedural de la composición que el usuario rechazó por calidad: se eliminó el GLB generado (15 MB), los objetos de la escena y los materiales propios, sin tocar `Nail Sculpture.glb` (del usuario) ni la foto.

**Decisiones**
- Preguntar antes de gastar cuota de API en vez de asumir un proveedor.
- Borrar **solo** lo generado en la sesión; los archivos previos del usuario no se tocan.

**Pendientes**
- [ ] API key propia (Meshy/Tripo/Hyper3D) si algún día se quiere generación fotorreal desde foto — el usuario ya tiene cuenta de Meshy (hay evidencia en la carpeta).

## 10:15 — unclavo_soc.glb: aplanado de iluminación, iteraciones rechazadas, reversión

**Qué se hizo** — Importado `unclavo_soc.glb` (escultura real de la foto). Hallazgo clave: el material traía **`metallic=1.0`** con la luz horneada en `Image_0` — esa combinación es lo que se ve oscuro/sucio en visores. Se intentó el aplanado «luz ambiental homogénea» en tres rondas: (1) compresión por canal `×0.5+0.32` → colores lavados; (2) remapeo de la cara superior a un rectángulo del atlas **con huecos negros entre islas** → franja negra invadiendo la cara; (3) rehorneado con la **emisión sin conectar a la salida** → textura negra. El usuario revirtió todo: purga de datos propios y reimport limpio del GLB.

**Decisiones**
- Revertir al original y exigir correcciones **quirúrgicas mínimas** — el modelo original es un escaneo/publicación del proyecto y su look no se reescribe con fórmulas globales.

**Errores míos** — Bake EMIT sin enlazar la emisión a la salida del material (textura salió negra); rectángulo de atlas elegido sin verificar los huecos (franja negra); la compresión por canal desaturaba (velo blanquecino). El safe mode bloqueó correctamente un `import os` y una lambda — reescritos en lenguaje permitido (buena señal del guardia).

## 11:40 — Corrección quirúrgica de la cara superior + proporción áurea medida

**Qué se hizo** — Diagnóstico numérico: la cara superior del bloque muestreaba una zona **lisa** del atlas (desviación de luminancia 0,055). Búsqueda automática de un rectángulo del atlas sin píxeles negros (0 %), con veta (std 0,087) y tono acorde → región `tablas_med`; reasignadas **solo** las UV de esas 4 361 caras. Medición posterior de la distribución resultante: eje V divide la cara en **61,3 / 38,7 → ratio 1,581 (2,3 % de φ)** — sección áurea casi exacta; eje U ratio 2,478 ≈ φ² (5,3 %). Emergencia del contenido del atlas + el rectángulo elegido, no diseño; el usuario pidió dejarlo tal cual.

## 13:18 — Escena empaquetada y GLB exportado

**Qué se hizo** — Blender estaba cerrado; verificación por CLI: `.blend` (17,1 MB) con `Image_0` ya embebida (`pack_all` confirmó), purga de huérfanos del primer intento (texturas Poly Haven sin material que las usara) → **5 MB**, 1 malla / 1 material / 1 textura. Exportación del modelo sin luces de vista (fiel al original): **`un_clavo_topfix.glb` 4,6 MB** (el original del repo pesa 4,8 MB — todo viaja).

**Pendientes**
- [x] Ninguno bloqueante. Entregables en `Desktop\DIMITRI\NAILS\`: `un_clavo_retoque.blend` (editable, autónomo) + `un_clavo_topfix.glb`; `assets\unclavo_soc.glb` del repo **intacto**.

**Verificación**
```powershell
Get-Item "C:\Users\kutzm\Desktop\DIMITRI\NAILS\un_clavo_topfix.glb", "C:\Users\kutzm\Desktop\DIMITRI\NAILS\un_clavo_retoque.blend" | Select Name, Length
```
