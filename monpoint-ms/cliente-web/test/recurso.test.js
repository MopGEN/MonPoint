import { test } from 'node:test'
import assert from 'node:assert/strict'
import { effectScope, nextTick } from 'vue'
import { useRecurso } from '../src/composables/useRecurso.js'

/**
 * Pruebas de la carga compartida de listados.
 *
 * Lo que se prueba aqui no es que una peticion feliz funcione —eso falla de
 * forma evidente en cuanto se abre la pantalla— sino los casos que pasan
 * desapercibidos: una respuesta lenta que llega tarde y pisa a la reciente, un
 * 403 tratado como error reintentable, y una respuesta que vuelve despues de
 * que el componente ya no existe.
 */

/** Promesa que se resuelve o rechaza desde fuera. */
function diferida () {
  let resolver, rechazar
  const promesa = new Promise((res, rej) => { resolver = res; rechazar = rej })
  return { promesa, resolver, rechazar }
}

/** Ejecuta `fn` dentro de un ambito de efectos y devuelve su resultado. */
function enAmbito (fn) {
  const ambito = effectScope()
  const resultado = ambito.run(fn)
  return { resultado, detener: () => ambito.stop() }
}

test('la carga feliz deja datos, sin error y resuelto', async () => {
  const { resultado: r } = enAmbito(() => useRecurso(async () => [1, 2, 3]))
  await nextTick()
  await nextTick()
  assert.deepEqual(r.datos.value, [1, 2, 3])
  assert.equal(r.cargando.value, false)
  assert.equal(r.error.value, null)
  assert.equal(r.resuelto.value, true)
})

test('una respuesta vieja no pisa a la nueva', async () => {
  const lenta = diferida()
  const rapida = diferida()
  let llamada = 0
  const { resultado: r } = enAmbito(() =>
    useRecurso(() => (++llamada === 1 ? lenta.promesa : rapida.promesa), { inmediato: false }),
  )

  const primera = r.recargar()
  const segunda = r.recargar()

  // La segunda termina antes que la primera, que es el orden que rompe el
  // codigo ingenuo.
  rapida.resolver('nueva')
  await segunda
  lenta.resolver('vieja')
  await primera

  assert.equal(r.datos.value, 'nueva')
  assert.equal(r.cargando.value, false)
})

test('un 403 marca denegado y no se confunde con un error reintentable', async () => {
  const fallo = Object.assign(new Error('Solo un ADMIN puede consultar esto'), { estado: 403 })
  const { resultado: r } = enAmbito(() => useRecurso(async () => { throw fallo }))
  await nextTick()
  await nextTick()
  assert.equal(r.denegado.value, true)
  assert.equal(r.error.value, fallo)
  assert.equal(r.resuelto.value, true)
})

test('un 500 es error pero no denegado', async () => {
  const fallo = Object.assign(new Error('El servidor respondio 500.'), { estado: 500 })
  const { resultado: r } = enAmbito(() => useRecurso(async () => { throw fallo }))
  await nextTick()
  await nextTick()
  assert.equal(r.denegado.value, false)
  assert.equal(r.error.value, fallo)
})

test('la cancelacion no deja la pantalla en estado de error', async () => {
  const d = diferida()
  let senalRecibida
  const { resultado: r } = enAmbito(() =>
    useRecurso(({ senal }) => { senalRecibida = senal; return d.promesa }, { inmediato: false }),
  )

  const primera = r.recargar()
  const segunda = r.recargar()
  assert.equal(senalRecibida.aborted, false, 'la segunda carga recibe una senal nueva')

  d.rechazar(Object.assign(new Error('abortada'), { name: 'AbortError' }))
  await primera
  await segunda

  assert.equal(r.error.value, null)
  assert.equal(r.denegado.value, false)
})

test('al destruir el ambito se aborta la peticion en vuelo', async () => {
  const d = diferida()
  let senal
  const { resultado: r, detener } = enAmbito(() =>
    useRecurso(({ senal: s }) => { senal = s; return d.promesa }),
  )

  await nextTick()
  assert.equal(senal.aborted, false)
  detener()
  assert.equal(senal.aborted, true)

  // Una respuesta que llega despues del desmontaje no escribe nada.
  d.resolver('tardia')
  await nextTick()
  assert.equal(r.datos.value, null)
})

test('recargar limpia el error anterior antes de volver a pedir', async () => {
  let fallar = true
  const fallo = Object.assign(new Error('caida'), { estado: 500 })
  const { resultado: r } = enAmbito(() =>
    useRecurso(async () => { if (fallar) throw fallo; return 'bien' }),
  )
  await nextTick()
  await nextTick()
  assert.equal(r.error.value, fallo)

  fallar = false
  await r.recargar()
  assert.equal(r.error.value, null)
  assert.equal(r.datos.value, 'bien')
})
