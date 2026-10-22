# 2026-10-22 — Evaluación DeepSeek Harness (DSH) y pendientes aparcados

**Estado:** abierto (pendientes aparcados) · **Inicio:** 2026-10-22 · **Última:** 2026-10-22

## Tarde — DSH vs OpenCode: evaluación a fondo

**Qué se hizo** — Investigación de **DeepSeek Harness** (`deepseek-ai/deepseek-harness`, preview `0.2.1-rc.2` del 17 oct 2026) a partir de su documentación oficial: README, SAFETY.md, arquitectura, subsystems (MCP, session, agent-loop), guía de providers y CLI. Contrastado con OpenCode V2 (doc oficial v2).

**Hallazgos clave**
- Arquitectura **Cordis «todo es plugin»** (hasta el agent loop y la UI); log de sesión *append-only* con fork/resume/replay y **Trajectory view** (qué vio el modelo, exacto) — su gran baza.
- **MCP completo**: cliente por servidor (stdio + Streamable HTTP), tools + resources + instructions al prompt; límites: sin prompt-templates, elicitation ni suscripciones de recursos.
- **Modelos**: DeepSeek por defecto, pero multi-provider built-in (Anthropic, OpenAI, Kimi, GLM) + *custom API* (OpenAI/Anthropic protocols) → admite cualquier gateway, incluso Ollama local.
- Interfaces: Web UI + Desktop Electron + perfil **headless** de una sola pasada + ACP + SDK TS/Python. **Sin TUI**.
- SAFETY.md explícito: preview **sin auditoría de seguridad**, no production-ready; recomienda VM/copia de seguridad.

**Decisión** — **No migrar.** Motivos: ausencia de TUI (flujo diario en terminal), MCPs propios (Blender 9876, spheroid) y repo `opencode-skills` ya cableados y funcionando en OpenCode, y preview con cambios rompiendo + sin auditar sobre máquinas con credenciales reales (JWT, SMTP). Ideas a «robar» mentalmente: Trajectory view y el perfil headless para automatizaciones (perfil *loop engineering*).

**Verificación** — Node del equipo v24.20.0 (cumple requisito DSH ≥24) medido con `node --version`; resto, lectura de docs oficiales (GitHub raw + deepseek.com/harness). No se instaló ni ejecutó DSH.

## Pendientes aparcados

- [ ] **Fine-tune con corpus del blog** (tema Soup, sesión de la mañana): extraer los 14 artículos MDX (~0,1 MB) a JSONL de chat; entrenar en la nube (Colab T4 gratis o GPU de pago) — la GT 710 de 1 GB descarta entrenamiento local. Excluir datos sensibles (config-smtp, autores-emails, roles, runbook).
- [ ] **Prueba contenida de DSH** (si algún día interesa): `npx @deepseek-ai/dsh web` en carpeta desechable con API key de DeepSeek de prueba; alternativa sin DSH: añadir DeepSeek como provider más en OpenCode.
- [ ] **JWT de Spheroid vence 2026-10-31** — abrir Demiurge y re-extraerlo (recordatorio global, ver `2026-10-22-mcp-blender.md`).
