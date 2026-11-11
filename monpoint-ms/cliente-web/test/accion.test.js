import { test } from 'node:test'
import assert from 'node:assert/strict'
import { effectScope } from 'vue'
import { useAccion } from '../src/composables/useAccion.js'

/**
 * El caso que motiva este modulo es el segundo: dos envios disparados antes de
 * que la interfaz se repinte. Las demas pruebas cubren el reparto del error,
 * que es la otra decision que estaba repitiendose por pantalla.
 */

function diferida () {
  let resolver, rechazar
  const promesa = new Promise((res, rej) => { resolver = res; rechazar = rej })
  return { promesa, resolver, rechazar }
}

const enAmbito = (fn) => effectScope().run(fn)

test('una escritura correcta devuelve su valor y no deja error', async () => {
  const a = enAmbito(() => useAccion(async (x) => x * 2))
  const r = await a.correr(21)
  assert.deepEqual(r, { ok: true, valor: 42 })
  assert.equal(a.enCurso.value, false)
  assert.equal(a.error.value, null)
  assert.deepEqual(a.campos.value, {})
})

test('dos envios en el mismo turno ejecutan la operacion una sola vez', async () => {
  const d = diferida()
  let veces = 0
  const a = enAmbito(() => useAccion(() => { veces++; return d.promesa }))

  // Sin `await` entre ambas: es exactamente el doble clic antes del repintado.
  const primera = a.correr()
  const segunda = a.correr()

  assert.deepEqual(await segunda, { ok: false, omitida: true })
  d.resolver('hecho')
  assert.deepEqual(await primera, { ok: true, valor: 'hecho' })
  assert.equal(veces, 1, 'la operacion no se repitio')
})

test('tras terminar, la guarda se libera y admite la siguiente escritura', async () => {
  let veces = 0
  const a = enAmbito(() => useAccion(async () => ++veces))
  await a.correr()
  await a.correr()
  assert.equal(veces, 2)
})

test('la guarda se libera tambien cuando la operacion falla', async () => {
  let veces = 0
  const a = enAmbito(() =>
    useAccion(async () => { veces++; if (veces === 1) throw new Error('caida'); return 'bien' }),
  )
  assert.equal((await a.correr()).ok, false)
  assert.deepEqual(await a.correr(), { ok: true, valor: 'bien' })
})

test('un error con campos los reparte y silencia el aviso general', async () => {
  const fallo = Object.assign(new Error('Uno o mas campos no cumplen las validaciones'), {
    estado: 400,
    campos: { nombre: 'El nombre es obligatorio' },
  })
  const a = enAmbito(() => useAccion(async () => { throw fallo }))

  const r = await a.correr()
  assert.equal(r.ok, false)
  assert.equal(r.causa, fallo)
  assert.deepEqual(a.campos.value, { nombre: 'El nombre es obligatorio' })
  assert.equal(a.error.value, null, 'con mensaje por campo, el generico sobra')
})

test('un error sin campos va al aviso general', async () => {
  const fallo = Object.assign(new Error('El servidor respondio 500.'), { estado: 500 })
  const a = enAmbito(() => useAccion(async () => { throw fallo }))
  await a.correr()
  assert.equal(a.error.value, fallo)
  assert.deepEqual(a.campos.value, {})
})

test('un mapa de campos vacio se trata como ausente', async () => {
  const fallo = Object.assign(new Error('Conflicto'), { estado: 409, campos: {} })
  const a = enAmbito(() => useAccion(async () => { throw fallo }))
  await a.correr()
  assert.equal(a.error.value, fallo)
  assert.deepEqual(a.campos.value, {})
})

test('los errores de la pantalla y los del servidor se leen del mismo sitio', async () => {
  const a = enAmbito(() => useAccion(async () => 'ok'))
  a.fijarCampos({ correo: 'El correo no tiene un formato válido' })
  assert.deepEqual(a.campos.value, { correo: 'El correo no tiene un formato válido' })

  // Una escritura posterior limpia lo anterior antes de empezar.
  await a.correr()
  assert.deepEqual(a.campos.value, {})
})

test('limpiar borra campos y aviso general', async () => {
  const fallo = Object.assign(new Error('x'), { estado: 500 })
  const a = enAmbito(() => useAccion(async () => { throw fallo }))
  await a.correr()
  a.limpiar()
  assert.equal(a.error.value, null)
  assert.deepEqual(a.campos.value, {})
})
