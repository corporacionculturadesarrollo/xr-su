'use strict';
// Pruebas del protocolo Marcador AR — núcleo de datos y roles.
// Cubre: máquina de estados, singleton admin (claim/heartbeat/resign),
// multi-instancia y regla "solo admin escribe".

const assert = require('assert');
const { configInicial, estadoInicial, aplicar, tick, aplicarSetup } = require('./estado');
const { Roles } = require('./roles');

let pasados = 0, total = 0;
function prueba(nombre, fn) {
  total++;
  try { fn(); pasados++; console.log(`  ✓ ${nombre}`); }
  catch (e) { console.error(`  ✗ ${nombre}\n    ${e.message}`); process.exitCode = 1; }
}

// =========================================================================
console.log('\n— máquina de estados del partido —');

prueba('estado inicial refleja la config', () => {
  const cfg = configInicial({ periodMinutes: 10 });
  const s = estadoInicial(cfg);
  assert.strictEqual(s.clockSec, 600);
  assert.strictEqual(s.shotClockSec, 24);
  assert.strictEqual(s.period, 1);
  assert.strictEqual(s.running, false);
  assert.deepStrictEqual(s.home, { score: 0, fouls: 0 });
});

prueba('score+ suma y cambia posesión, resetea shot a 24', () => {
  const cfg = configInicial();
  let s = estadoInicial(cfg);
  s.shotClockSec = 12;
  ({ state: s } = aplicar(s, cfg, { op: 'score+', team: 'home', delta: 2 }));
  assert.strictEqual(s.home.score, 2);
  assert.strictEqual(s.possession, 'visitor');
  assert.strictEqual(s.shotClockSec, 24);
});

prueba('score- nunca baja de 0', () => {
  const cfg = configInicial();
  let s = estadoInicial(cfg);
  ({ state: s } = aplicar(s, cfg, { op: 'score-', team: 'home' }));
  assert.strictEqual(s.home.score, 0);
});

prueba('foul+ dispara bonus exactamente al límite', () => {
  const cfg = configInicial({ foulLimit: 5 });
  let s = estadoInicial(cfg);
  let ev = [];
  for (let i = 0; i < 5; i++) ({ state: s, eventos: ev } = aplicar(s, cfg, { op: 'foul+', team: 'home' }));
  assert.ok(ev.includes('bonus-home'), 'debería emitir bonus al 5º');
  // 6ª falta no repite bonus
  ({ state: s, eventos: ev } = aplicar(s, cfg, { op: 'foul+', team: 'home' }));
  assert.ok(!ev.includes('bonus-home'));
});

prueba('shot reset24/reset14/inc/dec respetan límites', () => {
  const cfg = configInicial();
  let s = estadoInicial(cfg);
  ({ state: s } = aplicar(s, cfg, { op: 'shot', op2: 'reset14' }));
  assert.strictEqual(s.shotClockSec, 14);
  ({ state: s } = aplicar(s, cfg, { op: 'shot', op2: 'inc' }));
  assert.strictEqual(s.shotClockSec, 15);
  s.shotClockSec = 24;
  ({ state: s } = aplicar(s, cfg, { op: 'shot', op2: 'inc' }));
  assert.strictEqual(s.shotClockSec, 24, 'tope 24');
  ({ state: s } = aplicar(s, cfg, { op: 'shot', op2: 'dec' }));
  assert.strictEqual(s.shotClockSec, 23);
});

prueba('shot clock inactivo con rule24_14=false', () => {
  const cfg = configInicial({ rule24_14: false });
  let s = estadoInicial(cfg);
  assert.strictEqual(s.shotClockSec, 0);
  ({ state: s } = aplicar(s, cfg, { op: 'shot', op2: 'reset24' }));
  assert.strictEqual(s.shotClockSec, 0, 'sin regla no hay shot clock');
});

prueba('tick decrementa solo si running; buzzer al llegar a 0', () => {
  const cfg = configInicial();
  let s = estadoInicial(cfg);
  let r = tick(s, cfg);
  assert.strictEqual(r.state.clockSec, 600, 'pausado no avanza');
  s.running = true; s.clockSec = 1;
  r = tick(s, cfg);
  assert.strictEqual(r.state.clockSec, 0);
  assert.strictEqual(r.state.running, false);
  assert.ok(r.eventos.includes('buzzer'));
});

prueba('period next limpia faltas y relojes; en el último suena buzzer', () => {
  const cfg = configInicial({ periods: 4 });
  let s = estadoInicial(cfg);
  s.home.fouls = 3; s.visitor.fouls = 2; s.running = true;
  ({ state: s } = aplicar(s, cfg, { op: 'period', op2: 'next' }));
  assert.strictEqual(s.period, 2);
  assert.strictEqual(s.home.fouls, 0);
  assert.strictEqual(s.clockSec, 600);
  assert.strictEqual(s.running, false);
  // ir al 4º y intentar pasar del último
  s.period = 4;
  let ev; ({ state: s, eventos: ev } = aplicar(s, cfg, { op: 'period', op2: 'next' }));
  assert.strictEqual(s.period, 4, 'no pasa del último');
  assert.ok(ev.includes('buzzer'));
});

prueba('setup reconcilia config sin romper el estado', () => {
  const cfg = configInicial();
  let s = estadoInicial(cfg);
  s.clockSec = 720; // más que los nuevos 8 min
  const { config, state } = aplicarSetup(cfg, s, { periodMinutes: 8, homeName: 'TIGRES' });
  assert.strictEqual(config.periodMinutes, 8);
  assert.strictEqual(config.homeName, 'TIGRES');
  assert.ok(state.clockSec <= 8 * 60, 'reloj acotado al nuevo tope');
});

// =========================================================================
console.log('\n— roles: singleton admin —');

prueba('claim atómico: el primero gana, el segundo es denegado', () => {
  const r = new Roles();
  assert.ok(r.claim('A').ok);
  const seg = r.claim('B');
  assert.ok(!seg.ok);
  assert.strictEqual(seg.motivo, 'ocupado');
  assert.strictEqual(r.admin, 'A');
});

prueba('reconexión del propio admin recupera el rol', () => {
  const r = new Roles();
  r.claim('A');
  const rec = r.claim('A');
  assert.ok(rec.ok);
  assert.strictEqual(rec.motivo, 'reconectado');
});

prueba('resign solo lo puede hacer el admin; libera el slot', () => {
  const r = new Roles();
  r.claim('A');
  assert.ok(!r.resign('B').ok, 'B no es admin');
  assert.ok(r.resign('A').ok);
  assert.strictEqual(r.admin, null);
  assert.ok(r.claim('B').ok, 'tras resignar, B puede reclamar');
});

prueba('heartbeat timeout libera el rol solo', () => {
  let t = 0;
  const r = new Roles({ timeoutMs: 10000, ahora: () => t });
  r.claim('A');
  t += 3000; assert.ok(r.heartbeat('A').ok);
  t += 10001; // > 10 s desde el último latido (t=3000)
  assert.strictEqual(r.adminOnline(), false, 'rol liberado por timeout');
  assert.ok(r.claim('B').ok, 'B puede reclamar tras timeout');
});

prueba('tras timeout, el admin viejo ya no escribe', () => {
  let t = 0;
  const r = new Roles({ timeoutMs: 10000, ahora: () => t });
  r.claim('A');
  t += 11000;
  assert.strictEqual(r.esAdmin('A'), false);
});

// =========================================================================
console.log('\n— ciclo de vida combinado (integración del núcleo) —');

prueba('flujo completo: claim → gobernar → resign → sucesor', () => {
  const r = new Roles();
  const cfg = configInicial();
  let s = estadoInicial(cfg);

  assert.ok(r.claim('marcador-1').ok);
  // solo el admin escribe
  assert.ok(r.esAdmin('marcador-1'));
  assert.ok(!r.esAdmin('espectador-9'));

  ({ state: s } = aplicar(s, cfg, { op: 'score+', team: 'home', delta: 3 }));
  assert.strictEqual(s.home.score, 3);

  assert.ok(r.resign('marcador-1').ok);
  assert.ok(r.claim('marcador-2').ok, 'sucesor toma el rol');
  ({ state: s } = aplicar(s, cfg, { op: 'score+', team: 'visitor', delta: 2 }));
  assert.strictEqual(s.visitor.score, 2);
  assert.strictEqual(s.home.score, 3, 'el marcador previo se conserva');
});

console.log(`\n${pasados}/${total} pruebas pasaron${pasados === total ? ' ✓' : ' — HAY FALLOS'}`);
