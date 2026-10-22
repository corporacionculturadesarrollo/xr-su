# Examen y respaldo de F:\Proyecto predeterminado

**Estado:** cerrado · **Inicio:** 2026-10-21 · **Última:** 2026-10-21

## 21:32 — Mapeo del árbol F: y commit de respaldo

**Qué se hizo** — Examen completo de `F:\Proyecto predeterminado` (xr-su snapshot, spheroid-api con `.env`, docs-spheroid, marcador-ar pausado, mcp-spheroid-lang/apk, docs de REA, `.opencode`). Se localizaron 5 archivos de trabajo sin commitear y se respaldaron con commit `0a0db0c` (calibración de estaciones: pose guardada + rumbo; file-ids reales del MCP). Sin push.

**Decisiones**
- `C:\Users\kutzm\Documents\GitHub\xr-su` es la **repo viva** (MCP de 11 tools, build 50, la que apunta el MCP instalado); `F:\Proyecto predeterminado\GitHub\xr-su` es un **snapshot del 7 oct** que solo se respalda, **no se edita el proyecto ahí** — **porque** mantener dos copias editables duplicaría el trabajo y divergería.
- Respaldo sin push **porque** la regla de casa es no empujar `xr-su`/`opencode-skills` sin aprobación explícita.

**Verificación**
```powershell
git -C "F:\Proyecto predeterminado\GitHub\xr-su" log --oneline -1   # 0a0db0c y limpio
```
