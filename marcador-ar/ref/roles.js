'use strict';
// Gestor de roles de una instancia — singleton admin (docs/protocolo.md).
// Claim atómico, heartbeat con timeout, renuncia y reconexión del propio admin.

const HEARTBEAT_MS = 3000;   // cada cuánto latioda el admin
const TIMEOUT_MS = 10000;    // sin latido → rol liberado

class Roles {
  constructor({ timeoutMs = TIMEOUT_MS, ahora = () => Date.now() } = {}) {
    this.timeoutMs = timeoutMs;
    this.ahora = ahora;
    this.admin = null;      // deviceId del admin actual
    this.ultimoLatido = 0;
  }

  // Reclama el rol. Devuelve { ok, motivo }.
  claim(deviceId) {
    this._sweep();
    if (this.admin === null) {
      this.admin = deviceId;
      this.ultimoLatido = this.ahora();
      return { ok: true, motivo: 'concedido' };
    }
    if (this.admin === deviceId) {
      this.ultimoLatido = this.ahora();
      return { ok: true, motivo: 'reconectado' }; // reconexión del propio admin
    }
    return { ok: false, motivo: 'ocupado' };
  }

  // Renuncia explícita. Solo el admin puede entregar el rol.
  resign(deviceId) {
    if (this.admin === deviceId) {
      this.admin = null;
      this.ultimoLatido = 0;
      return { ok: true, motivo: 'entregado' };
    }
    return { ok: false, motivo: 'no-es-admin' };
  }

  // Latido del admin. Si rol ya fue liberado por timeout, lo pierde.
  heartbeat(deviceId) {
    if (this.admin === deviceId) {
      this.ultimoLatido = this.ahora();
      return { ok: true };
    }
    return { ok: false };
  }

  // ¿Sigues siendo admin (tras sweep)? Para denegar comandos.
  esAdmin(deviceId) {
    this._sweep();
    return this.admin === deviceId;
  }

  adminOnline() {
    this._sweep();
    return this.admin !== null;
  }

  // Libera el rol si el latido venció. Devuelve true si hubo liberación.
  _sweep() {
    if (this.admin !== null && this.ahora() - this.ultimoLatido > this.timeoutMs) {
      this.admin = null;
      this.ultimoLatido = 0;
      return true;
    }
    return false;
  }
}

module.exports = { Roles, HEARTBEAT_MS, TIMEOUT_MS };
