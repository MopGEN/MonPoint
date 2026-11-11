import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  aPeticion,
  correoPlausible,
  desdeCliente,
  filtrar,
  validar,
  vacio,
} from '../src/clientes/formulario.js'

const bueno = (cambios = {}) => ({ ...vacio(), nombre: 'Ana Ruiz', ...cambios })

test('solo el nombre es obligatorio', () => {
  assert.deepEqual(validar(bueno()), {})
  assert.equal(validar(vacio()).nombre, 'El nombre es obligatorio')
  assert.ok(validar(bueno({ nombre: '   ' })).nombre)
})

test('el correo vacio no se valida porque es opcional', () => {
  assert.deepEqual(validar(bueno({ correo: '' })), {})
  assert.deepEqual(validar(bueno({ correo: '   ' })), {})
})

test('la comprobacion basica del correo acepta formas comunes y rechaza errores evidentes', () => {
  for (const valido of ['a@b', 'ana.ruiz@example.test', 'x+etiqueta@dominio.mx']) {
    assert.equal(correoPlausible(valido), true, valido)
  }
  for (const invalido of ['sinarroba', 'a@@b', '@b', 'a@', 'con espacio@b.test']) {
    assert.equal(correoPlausible(invalido), false, invalido)
  }
})

test('una parte local citada no se rechaza por espacios o arrobas interiores', () => {
  for (const correo of ['"Ana Ruiz"@example.test', '"ana@oficina"@example.test']) {
    assert.equal(correoPlausible(correo), true)
    assert.deepEqual(validar(bueno({ correo })), {})
    assert.equal(aPeticion(bueno({ correo })).correo, correo)
  }
  assert.equal(correoPlausible('Ana Ruiz@example.test'), false)
})

/**
 * El servidor usa `@Email` de Jakarta, cuyo patron es permisivo. Si el cliente
 * fuera mas estricto rechazaria direcciones que el servidor aceptaria y el
 * usuario no tendria forma de saber que el limite lo pone la pantalla.
 */
test('la comprobacion admite dominios sin punto y apostrofos en la parte local', () => {
  assert.equal(correoPlausible('a@b'), true, 'sin punto en el dominio')
  assert.equal(correoPlausible('nombre@localhost'), true)
  assert.equal(correoPlausible("o'brien@example.test"), true)
})

test('los opcionales vacios viajan como null, no como cadena vacia', () => {
  const cuerpo = aPeticion(bueno({ correo: '  ', telefono: '', direccion: '   ' }))
  assert.deepEqual(cuerpo, { nombre: 'Ana Ruiz', correo: null, telefono: null, direccion: null })
})

test('se recortan los espacios de todos los campos', () => {
  const cuerpo = aPeticion({
    nombre: '  Ana  ', correo: ' a@b ', telefono: ' 33 ', direccion: ' Calle 1 ',
  })
  assert.deepEqual(cuerpo, {
    nombre: 'Ana', correo: 'a@b', telefono: '33', direccion: 'Calle 1',
  })
})

test('desdeCliente convierte los nulos del servidor en campos vacios', () => {
  assert.deepEqual(
    desdeCliente({ nombre: 'Luis', correo: null, telefono: null, direccion: null }),
    { nombre: 'Luis', correo: '', telefono: '', direccion: '' },
  )
  assert.deepEqual(desdeCliente(undefined), { nombre: '', correo: '', telefono: '', direccion: '' })
})

test('ida y vuelta: cargar un cliente y reenviarlo no inventa datos', () => {
  const delServidor = { nombre: 'Luis', correo: null, telefono: '3312345678', direccion: null }
  assert.deepEqual(aPeticion(desdeCliente(delServidor)), delServidor)
})

test('el filtro mira nombre, correo y telefono, pero no direccion', () => {
  const lista = [
    { nombre: 'Ana Ruiz', correo: 'ana@example.test', telefono: '33', direccion: 'Av. Juárez 10' },
    { nombre: 'Luis Pérez', correo: null, telefono: '55', direccion: 'Calle Ana 3' },
  ]
  assert.equal(filtrar(lista, 'ana').length, 1, 'la direccion con "Ana" no cuenta')
  assert.equal(filtrar(lista, 'perez').length, 1, 'ignora el acento')
  assert.equal(filtrar(lista, '55').length, 1)
  assert.equal(filtrar(lista, '').length, 2)
})

test('un cliente sin correo no rompe el filtro', () => {
  assert.doesNotThrow(() => filtrar([{ nombre: 'Luis' }], 'lu'))
  assert.equal(filtrar([{ nombre: 'Luis' }], 'lu').length, 1)
})
