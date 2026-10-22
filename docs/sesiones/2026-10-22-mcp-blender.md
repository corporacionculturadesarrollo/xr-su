# MCP de Blender instalado (comunitario mcp-for-blender)

**Estado:** abierto · **Inicio:** 2026-10-22 · **Última:** 2026-10-22

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
- [ ] **Reiniciar OpenCode** para que cargue `blender-mcp` (los MCP se cargan al arranque; hoy la sesión viva no lo ve). Motivo: la entrada ya está escrita y validada, falta solo el arranque.
- [ ] Prueba real de `generate_3d` con una foto (falta elegir imagen y decidir proveedor/trial).

**Verificación**
```powershell
# Blender abierto → socket escuchando
Get-NetTCPConnection -LocalPort 9876 -State Listen
# config global con blender-mcp
node -e "console.log(Object.keys(JSON.parse(require('fs').readFileSync('C:/Users/kutzm/.config/opencode/opencode.json','utf8')).mcp))"
```

**Errores míos** — En el `--python-expr` que habilitó el addon concatené `str + bool` en el `print` (TypeError, exit 1); el `addon_enable` y el `save_userpref` se ejecutaron antes, así que la preferencia quedó guardada igualmente. Detectado en la salida del propio comando y corregido con una expresión de verificación aparte (`ADDON-EN-PREFS: True`).
