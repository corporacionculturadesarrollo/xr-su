'use strict';
// Servidor de referencia del protocolo Marcador AR.
// Compone: instancias independientes × roles (singleton admin) × estado del partido.
// Transporte: WebSocket (docs/protocolo.md).

const { WebSocketServer } = require('ws');
const { configInicial, estadoInicial, aplicar, tick, aplicarSetup } = require('./estado');
const { Roles, HEARTBEAT_MS } = require('./roles');

// ---- instancia = un escenario --------------------------------------------
class Instancia {
  constructor(id) {
    this.id = id;
    this.config = configInicial();
    this.estado = estadoInicial(this.config);
    this.roles = new Roles();
    this.clientes = new Map();       // ws -> { deviceId, rol }
    this.timer = null;               // reloj del partido
  }

  adminId() { return this.roles.admin; }

  arrancarReloj() {
    if (this.timer) return;
    this.timer = setInterval(() => {
      const { state, eventos } = tick(this.estado, this.config);
      this.estado = state;
      this.emitir({ tipo: 'state', state });
      for (const e of eventos) {
        if (e === 'buzzer') this.emitir({ tipo: 'buzzer' });
        else if (e === 'shot-expired') this.emitir({ tipo: 'shot-expired' });
        else this.emitir({ tipo: 'aviso', evento: e });
      }
    }, 1000);
  }

  pararReloj() {
    if (this.timer) { clearInterval(this.timer); this.timer = null; }
  }

  emitir(msg) {
    const dato = JSON.stringify(msg);
    for (const ws of this.clientes.keys()) {
      if (ws.readyState === 1) ws.send(dato);
    }
  }

  presencia() {
    this.roles._sweep();
    let espectadores = 0;
    for (const { rol } of this.clientes.values()) if (rol === 'espectador') espectadores++;
    this.emitir({ tipo: 'presence', adminOnline: this.roles.adminOnline(), espectadores });
  }
}

// ---- servidor --------------------------------------------------------------
function crearServidor({ puerto = 0, registroInstancias = () => ['principal'] } = {}) {
  const instancias = new Map();

  function obtener(id) {
    if (!instancias.has(id)) instancias.set(id, new Instancia(id));
    return instancias.get(id);
  }

  const wss = new WebSocketServer({ port: puerto });
  const puertoActual = () => wss.address().port;

  wss.onConnection = null; // hook de pruebas

  wss.on('connection', (ws) => {
    let inst = null;
    let deviceId = null;
    const enviar = (msg) => { if (ws.readyState === 1) ws.send(JSON.stringify(msg)); };

    ws.on('message', (buf) => {
      let m;
      try { m = JSON.parse(buf.toString()); } catch { return enviar({ tipo: 'error', code: 'json', message: 'JSON inválido' }); }
      if (!m || typeof m.tipo !== 'string') return;

      // ---- registro: primera unión fija la instancia ----
      if (m.tipo === 'join') {
        const validas = registroInstancias();
        // '*' como comodín acepta cualquier instancia (útil para pruebas de interop
        // y para despliegues multi-sede donde el registro lo controla otro nivel).
        const permitida = validas.includes('*') || validas.includes(m.instancia);
        if (!permitida) {
          return enviar({ tipo: 'error', code: 'instancia', message: 'instancia no autorizada' });
        }
        inst = obtener(m.instancia);
        deviceId = String(m.deviceId || 'anon');
        inst.clientes.set(ws, { deviceId, rol: 'espectador' });
        // solo RECUPERA el rol si este dispositivo era el admin (reconexión
        // dentro del período de gracia) — unirse nunca otorga el rol
        if (inst.roles.esAdmin(deviceId)) {
          inst.clientes.set(ws, { deviceId, rol: 'admin' });
        }
        enviar({
          tipo: 'welcome',
          rol: inst.clientes.get(ws).rol,
          config: inst.config,
          state: inst.estado,
        });
        inst.presencia();
        return;
      }

      if (!inst) return enviar({ tipo: 'error', code: 'join', message: 'une primero (join)' });

      // ---- roles ----
      if (m.tipo === 'claim') {
        const r = inst.roles.claim(deviceId);
        if (r.ok) {
          // solo el reclamante pasa a admin; los demás espectadores se quedan
          for (const [k, v] of inst.clientes) {
            if (v.deviceId === deviceId) v.rol = 'admin';
          }
          enviar({ tipo: 'role', granted: true, motivo: r.motivo });
          inst.pararReloj(); // el nuevo admin decide cuándo correr
          inst.presencia();
        } else {
          enviar({ tipo: 'role', granted: false, motivo: r.motivo });
        }
        return;
      }

      if (m.tipo === 'resign') {
        const r = inst.roles.resign(deviceId);
        if (r.ok) {
          for (const [k, v] of inst.clientes) if (v.deviceId === deviceId) v.rol = 'espectador';
          inst.emitir({ tipo: 'role', granted: false, motivo: 'entregado' });
          inst.presencia();
        }
        return;
      }

      if (m.tipo === 'heartbeat') {
        const ok = inst.roles.heartbeat(deviceId);
        if (!ok && inst.clientes.get(ws)?.rol === 'admin') {
          // perdió el rol por timeout
          inst.clientes.set(ws, { deviceId, rol: 'espectador' });
          enviar({ tipo: 'role', granted: false, motivo: 'timeout' });
        }
        return;
      }

      // ---- comandos del partido: solo admin ----
      if (m.tipo === 'cmd') {
        if (!inst.roles.esAdmin(deviceId)) {
          return enviar({ tipo: 'error', code: 'rol', message: 'solo el admin alimenta el marcador' });
        }
        try {
          if (m.cmd?.op === 'setup') {
            const { config, state } = aplicarSetup(inst.config, inst.estado, m.cmd.config || {});
            inst.config = config;
            inst.estado = state;
            inst.emitir({ tipo: 'config', config });
            inst.emitir({ tipo: 'state', state });
            return;
          }
          if (m.cmd?.op === 'clock') {
            const { state, eventos } = aplicar(inst.estado, inst.config, m.cmd);
            inst.estado = state;
            if (state.running) inst.arrancarReloj(); else inst.pararReloj();
            inst.emitir({ tipo: 'state', state });
            for (const e of eventos) if (e === 'buzzer') inst.emitir({ tipo: 'buzzer' });
            return;
          }
          const { state, eventos } = aplicar(inst.estado, inst.config, m.cmd);
          inst.estado = state;
          inst.emitir({ tipo: 'state', state });
          for (const e of eventos) {
            if (e === 'buzzer') inst.emitir({ tipo: 'buzzer' });
            else inst.emitir({ tipo: 'aviso', evento: e });
          }
        } catch (err) {
          enviar({ tipo: 'error', code: 'cmd', message: String(err.message || err) });
        }
        return;
      }
    });

    ws.on('close', () => {
      if (inst && inst.clientes.has(ws)) {
        const { rol } = inst.clientes.get(ws);
        inst.clientes.delete(ws);
        // si el admin se desconectó, el timeout del heartbeat lo libera solo;
        // no hacemos nada agresivo aquí a propósito (tolerancia a reconexión)
        inst.presencia();
      }
    });
  });

  return { wss, puerto: puertoActual, instancias, obtener };
}

// ---- arranque CLI ----------------------------------------------------------
if (require.main === module) {
  const PUERTO = Number(process.env.PUERTO || 8899);
  // INSTANCIAS: lista separada por comas de instancias autorizadas, o '*' para
  // aceptar cualquier instancia (auto-creación). Por defecto solo 'principal'.
  const rawInst = process.env.INSTANCIAS || 'principal';
  const listaInst = rawInst.split(',').map((s) => s.trim()).filter(Boolean);
  const srv = crearServidor({ puerto: PUERTO, registroInstancias: () => listaInst });
  srv.wss.on('listening', () => console.log(`Marcador AR ref server — ws://127.0.0.1:${srv.wss.address().port} (instancias: ${listaInst.join(', ')})`));
}

module.exports = { crearServidor, Instancia };
