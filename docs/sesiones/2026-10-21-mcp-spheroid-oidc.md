# MCP spheroid complementado y renovación del JWT (OIDC) investigada

**Estado:** cerrado · **Inicio:** 2026-10-21 · **Última:** 2026-10-22

## 21:50 — vencimiento del JWT en `status` + guardia en `api_call`

**Qué se hizo** — El tool `status` ahora decodifica el campo `exp` del JWT y lo imprime como fecha y días restantes (nunca el token). `api_call` exige endpoint **relativo**, rechazando URLs absolutas. README pasado a 11 tools + `registry.json`; smoke test migrado a `fileURLToPath` (las rutas con espacios del equipo fallaban por `%20`). Commits: C: `f51e80b` + `1143d8e`; espejo F: `236c1fb` + `c2ac4ab`.

**Decisiones**
- Decodificar `exp` en el cliente **porque** el JWT vigente vence 2026-10-31 y hasta ahora no había forma de saberlo sin decodificar a mano.
- `api_call` solo con endpoint relativo **porque** el header `Authorization` viaja fijo en la petición: aceptar hosts arbitrarios filtraría el Bearer a terceros.
- El header `Origin: https://demiurge.spheroid.io` sigue siendo obligatorio en la API; Spheroid no tiene `api/app/delete` (404) ni endpoint propio de builds (usar `app/details` → `publications`).

**Verificación**
```powershell
cmd /c npm test   # en C:\Users\kutzm\Documents\GitHub\xr-su\mcp-spheroid → SMOKE OK (11 tools)
```

## 00:27 — OIDC: no hay refresh token posible hoy

**Qué se hizo** — Investigación del OIDC de `auth.spheroid.io` con sondas usando un token basura (sin credenciales reales). Resultado: el servidor **sí** tiene el grant de refresh habilitado para `Demiurge.Frontend` (una llamada sin refresh token devuelve `invalid_grant`, no un error de grant), pero Demiurge **no pide `offline_access`**; el device flow está cerrado (`unauthorized_client`). El token dura **24 días** (exp 2026-10-31). Documentado en el README del MCP §«Renovación del JWT» (C: `ab306d4`, F: `cc469bb`) y en la skill `exposicion-ccd` (`6097e69`).

**Decisiones**
- Renovación práctica = **abrir Demiurge con sesión viva** (su silent-renew por iframe renueva el token) y **re-extraerlo** de `sessionStorage["oidc.user:https://auth.spheroid.io:Demiurge.Frontend"]`, **porque** es el único flujo que hoy emite tokens nuevos sin intervención del servidor.
- No rellenar el `scope` a mano con `offline_access` **porque** el servidor rechaza por scope inválido en el authorization endpoint.
- Sondas solo con tokens basuras y sin tocar `connect/revocation` **porque** revocaría la sesión real de Demiurge sin remedio.

**Pendientes**
- [ ] **2026-10-31 vence el JWT** → antes de esa fecha, abrir Demiurge y re-extraer el token (el tool `status` ya avisa de los días restantes).
- [ ] Push de `xr-su` (C:) y `opencode-skills` pendiente de aprobación explícita.
- [ ] Feature request opcional a Spheroid para habilitar `offline_access` en el client.
