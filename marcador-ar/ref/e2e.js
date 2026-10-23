'use strict';
// E2E del servidor WebSocket: roles disputados, broadcast y multi-instancia.

const assert = require('assert');
const WebSocket = require('ws');
const { crearServidor } = require('./server');

function conectar(puerto, instancia, deviceId) {
  const ws = new WebSocket(`ws://127.0.0.1:${puerto}`);
  const bandeja = [];
  const esperar = (tipo, ms = 1500) => new Promise((res, rej) => {
    const hit = bandeja.find((m) => m.tipo === tipo);
    if (hit) return res(hit);
    const t = setTimeout(() => rej(new Error(`timeout esperando '${tipo}'`)), ms);
    ws.on('message', function h(b) {
      const m = JSON.parse(b.toString());
      if (m.tipo === tipo) { clearTimeout(t); ws.off('message', h); res(m); }
    });
  });
  ws.on('message', (b) => bandeja.push(JSON.parse(b.toString())));
  ws.on('open', () => ws.send(JSON.stringify({ tipo: 'join', instancia, deviceId })));
  return { ws, esperar, enviar: (m) => ws.send(JSON.stringify(m)) };
}

const dormir = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const srv = crearServidor({ registroInstancias: () => ['cancha-a', 'cancha-b'] });
  await new Promise((r) => srv.wss.on('listening', r));
  const puerto = srv.wss.address().port;
  let ok = 0, total = 0;
  const prueba = async (nombre, fn) => {
    total++;
    try { await fn(); ok++; console.log(`  ✓ ${nombre}`); }
    catch (e) { console.error(`  ✗ ${nombre}\n    ${e.message}`); process.exitCode = 1; }
  };

  await prueba('welcome entrega rol, config y estado inicial', async () => {
    const a = conectar(puerto, 'cancha-a', 'dev-a');
    const w = await a.esperar('welcome');
    assert.strictEqual(w.rol, 'espectador', 'unirse no otorga admin');
    assert.strictEqual(w.state.home.score, 0);
    a.ws.close();
  });

  await prueba('claim disputado: gana el primero, el segundo es denegado', async () => {
    const a = conectar(puerto, 'cancha-a', 'marcador-1');
    await a.esperar('welcome');
    a.enviar({ tipo: 'claim' });
    const ra = await a.esperar('role');
    assert.strictEqual(ra.granted, true);

    const b = conectar(puerto, 'cancha-a', 'intruso');
    await b.esperar('welcome');
    b.enviar({ tipo: 'claim' });
    const rb = await b.esperar('role');
    assert.strictEqual(rb.granted, false, 'segundo claim denegado');
    a.enviar({ tipo: 'resign' }); // el admin entrega el rol antes de irse
    await dormir(50);
    a.ws.close(); b.ws.close();
    await dormir(50); // dejar cerrar
  });

  await prueba('solo el admin escribe; el espectador recibe el broadcast', async () => {
    const admin = conectar(puerto, 'cancha-a', 'marcador-2');
    await admin.esperar('welcome');
    admin.enviar({ tipo: 'claim' });
    await admin.esperar('role');

    const esp = conectar(puerto, 'cancha-a', 'fan-1');
    await esp.esperar('welcome');

    // espectador intenta escribir → error de rol
    esp.enviar({ tipo: 'cmd', cmd: { op: 'score+', team: 'home', delta: 3 } });
    const err = await esp.esperar('error');
    assert.strictEqual(err.code, 'rol');

    // admin escribe → espectador recibe el state
    admin.enviar({ tipo: 'cmd', cmd: { op: 'score+', team: 'home', delta: 3 } });
    const st = await esp.esperar('state');
    assert.strictEqual(st.state.home.score, 3, 'broadcast llegó al espectador');
    admin.ws.close(); esp.ws.close();
    await dormir(50);
  });

  await prueba('resign libera el rol y otro puede reclamarlo', async () => {
    const a = conectar(puerto, 'cancha-a', 'marcador-2');
    await a.esperar('welcome'); // recupera por reconexión
    a.enviar({ tipo: 'resign' });
    await dormir(50);

    const b = conectar(puerto, 'cancha-a', 'marcador-3');
    await b.esperar('welcome');
    b.enviar({ tipo: 'claim' });
    const rb = await b.esperar('role');
    assert.strictEqual(rb.granted, true, 'tras resign, el sucesor gana');
    a.ws.close(); b.ws.close();
    await dormir(50);
  });

  await prueba('instancias aisladas: cancha-b no ve cancha-a', async () => {
    const a = conectar(puerto, 'cancha-a', 'marcador-x');
    await a.esperar('welcome');
    a.enviar({ tipo: 'claim' });
    await a.esperar('role');
    a.enviar({ tipo: 'cmd', cmd: { op: 'score+', team: 'visitor', delta: 5 } });

    const b = conectar(puerto, 'cancha-b', 'dev-b');
    const wb = await b.esperar('welcome');
    assert.strictEqual(wb.state.visitor.score, 0, 'cancha-b arranca en 0');
    a.ws.close(); b.ws.close();
    await dormir(50);
  });

  await prueba('instancia no autorizada es rechazada', async () => {
    const x = conectar(puerto, 'cancha-hacker', 'dev-x');
    const err = await x.esperar('error');
    assert.strictEqual(err.code, 'instancia');
    x.ws.close();
  });

  console.log(`\n${ok}/${total} pruebas E2E pasaron${ok === total ? ' ✓' : ' — HAY FALLOS'}`);
  srv.wss.close();
  process.exit(process.exitCode || 0);
})();
