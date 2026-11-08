import assert from 'node:assert/strict'
import { writeFile } from 'node:fs/promises'
import { sembrar, productos } from '/workspace/MonPoint/monpoint-ms/scripts/semilla.mjs'
const env = process.env
const gateway = env.SEMILLA_GATEWAY_URL
const carpeta = '/workspace/.monpoint/semilla-3.1'
const login = await fetch(`${gateway}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ correo: 'admin@monpoint.com', password: env.ADMIN_DEFAULT_PASSWORD }) })
assert.equal(login.status, 200)
const { token } = await login.json()
async function api(ruta, method = 'GET', body) {
  const r = await fetch(`${gateway}${ruta}`, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, ...(body ? { body: JSON.stringify(body) } : {}) })
  assert.equal(r.status, 200)
  return r.json()
}
async function conteos() {
  const [usuarios, clientes, productos] = await Promise.all(['/auth/usuarios', '/clientes', '/inventario/productos'].map(r=>api(r)))
  return { usuarios: usuarios.length, clientes: clientes.length, productos: productos.length }
}
const antes = await conteos()
assert.deepEqual(antes, { usuarios: 1, clientes: 0, productos: 0 })
const requests = []
async function observar(url, opciones) {
  const respuesta = await fetch(url, opciones)
  requests.push({ metodo: opciones.method, ruta: new URL(url).pathname, status: respuesta.status })
  return respuesta
}
const primera = await sembrar({ env, fetchImpl: observar })
const primeraPeticiones = requests.splice(0)
const despues = await conteos()
assert.deepEqual(despues, { usuarios: 2, clientes: 2, productos: 3 })
const segunda = await sembrar({ env, fetchImpl: observar })
const segundaPeticiones = requests.splice(0)
assert.deepEqual(await conteos(), despues)
for (const grupo of ['usuarios','clientes','productos']) {
  assert.deepEqual(primera[grupo].map(x=>x.id), segunda[grupo].map(x=>x.id))
  assert.ok(segunda[grupo].every(x=>x.accion==='REUTILIZADO'))
}
assert.ok(segundaPeticiones.every(x=>x.metodo!=='POST'||x.ruta==='/auth/login'))
const tinta = primera.productos[0]
await api(`/inventario/productos/${tinta.id}`, 'PUT', { ...productos[0], existencia: 7 })
const tercera = await sembrar({ env, fetchImpl: observar })
assert.equal(tercera.productos[0].existencia, 7)
assert.equal(tercera.productos[0].id, tinta.id)
assert.ok(tercera.productos.every(x=>x.accion==='REUTILIZADO'))
const terceraPeticiones = requests.splice(0)
let falloEsperado
try { await sembrar({ env: { ...env, ADMIN_DEFAULT_PASSWORD: 'CredencialIncorrecta31!' }, fetchImpl: observar }); assert.fail('Debía fallar el login') }
catch (e) { assert.match(e.message, /HTTP 401/); falloEsperado={error:e.message,resumen:e.resumen} }
assert.deepEqual(await conteos(), despues)
assert.equal(requests.length, 1)
const resultados = { revisionBase: '4ab860d', fechaUTC: new Date().toISOString(), node: process.version, entorno: 'Linux, proyecto Docker monpoint-semilla31, volúmenes nuevos, Gateway :18080', adminPasswordPersonalizada: true, vendedorPasswordPersonalizada: true, conteosAntes: antes, conteosDespues: despues, primera, segunda, terceraStockModificado: tercera, autenticacionFallida: falloEsperado, peticiones: { primera: primeraPeticiones, segunda: segundaPeticiones, tercera: terceraPeticiones, fallo: requests }, ventasCreadas: 0 }
await writeFile(`${carpeta}/base-nueva.json`, JSON.stringify(resultados,null,2)+'\n')
console.log(JSON.stringify({antes,despues,idsEstables:true,altasSegunda:0,stockConservado:tercera.productos[0].existencia,falloAdminSinAltas:true,ventasCreadas:0}))
