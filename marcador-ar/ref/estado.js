'use strict';
// Máquina de estados del partido — reducer puro (docs/protocolo.md).
// Sin red ni reloj propios: el servidor le pasa los ticks.

const CFG_DEFECTO = Object.freeze({
  periodMinutes: 10,
  periods: 4,
  foulLimit: 5,
  rule24_14: true,
  homeName: 'LOCAL TEAM',
  visitorName: 'VISITOR TEAM',
});

function configInicial(cfg) {
  return { ...CFG_DEFECTO, ...cfg };
}

function estadoInicial(cfg) {
  return {
    clockSec: cfg.periodMinutes * 60,
    shotClockSec: cfg.rule24_14 ? 24 : 0,
    period: 1,
    running: false,
    possession: 'home',
    home: { score: 0, fouls: 0 },
    visitor: { score: 0, fouls: 0 },
  };
}

const clonar = (o) => JSON.parse(JSON.stringify(o));

// Devuelve { state, eventos } — eventos: ['buzzer'] | ['buzzer', 'bonus-home'] ...
function aplicar(state, cfg, cmd) {
  const s = clonar(state);
  const eventos = [];
  const equipo = (t) => (t === 'visitor' ? s.visitor : s.home);

  switch (cmd.op) {
    case 'score+': {
      const e = equipo(cmd.team);
      e.score = Math.max(0, e.score + (cmd.delta || 1));
      s.possession = cmd.team === 'home' ? 'visitor' : 'home';
      if (cfg.rule24_14) s.shotClockSec = 24; // canasta → posesión nueva
      break;
    }
    case 'score-': {
      const e = equipo(cmd.team);
      e.score = Math.max(0, e.score - 1);
      break;
    }
    case 'foul+': {
      const e = equipo(cmd.team);
      e.fouls += 1;
      if (e.fouls === cfg.foulLimit) eventos.push(`bonus-${cmd.team}`);
      break;
    }
    case 'foul-': {
      const e = equipo(cmd.team);
      e.fouls = Math.max(0, e.fouls - 1);
      break;
    }
    case 'clock': {
      if (cmd.op2 === 'start') s.running = true;
      else if (cmd.op2 === 'pause') s.running = false;
      else if (cmd.op2 === 'reset') {
        s.running = false;
        s.clockSec = cfg.periodMinutes * 60;
        if (cfg.rule24_14) s.shotClockSec = 24;
      }
      break;
    }
    case 'shot': {
      if (!cfg.rule24_14) break; // regla desactivada → sin shot clock
      if (cmd.op2 === 'reset24') s.shotClockSec = 24;
      else if (cmd.op2 === 'reset14') s.shotClockSec = 14;
      else if (cmd.op2 === 'inc') s.shotClockSec = Math.min(24, s.shotClockSec + 1);
      else if (cmd.op2 === 'dec') s.shotClockSec = Math.max(0, s.shotClockSec - 1);
      break;
    }
    case 'period': {
      const ir = (p) => {
        s.period = Math.min(cfg.periods, Math.max(1, p));
        s.clockSec = cfg.periodMinutes * 60;
        if (cfg.rule24_14) s.shotClockSec = 24;
        s.home.fouls = 0;
        s.visitor.fouls = 0;
        s.running = false;
      };
      if (cmd.op2 === 'next') {
        if (s.period < cfg.periods) ir(s.period + 1);
        else eventos.push('buzzer'); // ya era el último
      } else if (cmd.op2 === 'prev') ir(s.period - 1);
      else if (cmd.op2 === 'set') ir(cmd.value || 1);
      break;
    }
    case 'buzzer':
      eventos.push('buzzer');
      break;
    case 'state': {
      if (cmd.op2 === 'reset' || cmd.op2 === 'fullReset') {
        const limpio = estadoInicial(cfg);
        Object.assign(s, limpio);
      }
      break;
    }
    default:
      throw new Error(`cmd desconocido: ${cmd.op}`);
  }
  return { state: s, eventos };
}

// Tick de reloj (1 s) — solo si running. shotClockSec auto-decrementa con la regla.
function tick(state, cfg) {
  const s = clonar(state);
  const eventos = [];
  if (!s.running) return { state: s, eventos };
  if (s.clockSec > 0) s.clockSec -= 1;
  if (cfg.rule24_14 && s.shotClockSec > 0) s.shotClockSec -= 1;
  if (s.clockSec === 0) {
    if (s.running) { s.running = false; eventos.push('buzzer'); }
  }
  if (s.shotClockSec === 0) eventos.push('shot-expired');
  return { state: s, eventos };
}

// Aplica un setup (diálogo) — devuelve { config, state } reconciliado.
function aplicarSetup(cfgPrev, statePrev, nuevo) {
  const cfg = configInicial({ ...cfgPrev, ...nuevo });
  const s = clonar(statePrev);
  // minutos cambiaron y el reloj estaba en el tope del período viejo → recolocar
  s.clockSec = Math.min(s.clockSec, cfg.periodMinutes * 60);
  if (cfg.rule24_14 && s.shotClockSec === 0) s.shotClockSec = 24;
  if (!cfg.rule24_14) s.shotClockSec = 0;
  return { config: cfg, state: s };
}

module.exports = { CFG_DEFECTO, configInicial, estadoInicial, aplicar, tick, aplicarSetup };
