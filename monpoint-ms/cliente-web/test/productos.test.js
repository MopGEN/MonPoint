import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  UMBRAL_POR_OMISION,
  aPeticion,
  bajoUmbral,
  desdeProducto,
  filtrar,
  validar,
  vacio,
} from '../src/productos/formulario.js'

/** Formulario valido al que cada prueba cambia lo que le interesa. */
const bueno = (cambios = {}) => ({
  ...vacio(),
  nombre: 'Tinta',
  precio: '41.15',
  existencia: '10',
  umbralStock: '5',
  ...cambios,
})

test('un formulario completo no produce errores', () => {
  assert.deepEqual(validar(bueno()), {})
})

test('los campos obligatorios se nombran con la clave del DTO', () => {
  const e = validar({ ...vacio() })
  assert.equal(e.nombre, 'El nombre es obligatorio')
  assert.ok(e.precio)
  assert.ok(e.existencia)
  // Al crear, el umbral puede faltar: el servidor pone el valor por omision.
  assert.equal(e.umbralStock, undefined)
})

test('el nombre en blanco no cuenta como nombre', () => {
  assert.ok(validar(bueno({ nombre: '   ' })).nombre)
})

test('el precio admite cero y hasta dos decimales', () => {
  assert.deepEqual(validar(bueno({ precio: '0' })), {})
  assert.deepEqual(validar(bueno({ precio: '0.5' })), {})
  assert.deepEqual(validar(bueno({ precio: '120.00' })), {})
})

test('el precio rechaza negativos, notacion cientifica y tres decimales', () => {
  for (const malo of ['-1', '1e3', '41.159', 'abc', '41,15', '']) {
    assert.ok(validar(bueno({ precio: malo })).precio, `deberia rechazar ${JSON.stringify(malo)}`)
  }
})

test('el precio rechaza un literal que Number alteraria en silencio', () => {
  // Trece digitos enteros ya exceden lo que un doble representa exactamente
  // junto con dos decimales.
  assert.ok(validar(bueno({ precio: '12345678901234.56' })).precio)
  assert.deepEqual(validar(bueno({ precio: '123456789012.34' })), {})
})

test('existencia y umbral exigen enteros no negativos dentro del rango de int', () => {
  assert.deepEqual(validar(bueno({ existencia: '0', umbralStock: '0' })), {})
  assert.ok(validar(bueno({ existencia: '1.5' })).existencia)
  assert.ok(validar(bueno({ existencia: '-3' })).existencia)
  assert.ok(validar(bueno({ existencia: '2147483648' })).existencia)
  assert.deepEqual(validar(bueno({ existencia: '2147483647' })), {})
})

test('al editar, el umbral vacio es un error porque conservaria el anterior', () => {
  const forma = bueno({ umbralStock: '' })
  assert.deepEqual(validar(forma), {}, 'al crear se permite')
  assert.ok(validar(forma, { edicion: true }).umbralStock, 'al editar no')
})

test('la peticion manda numeros y convierte la descripcion vacia en null', () => {
  const cuerpo = aPeticion(bueno({ descripcion: '  ' }))
  assert.deepEqual(cuerpo, {
    nombre: 'Tinta',
    descripcion: null,
    precio: 41.15,
    existencia: 10,
    umbralStock: 5,
  })
  assert.equal(typeof cuerpo.precio, 'number')
})

test('un umbral vacio viaja como null para que el servidor ponga el suyo', () => {
  assert.equal(aPeticion(bueno({ umbralStock: '' })).umbralStock, null)
  assert.equal(UMBRAL_POR_OMISION, 5)
})

test('el formulario recorta los espacios del nombre y la descripcion', () => {
  const cuerpo = aPeticion(bueno({ nombre: '  Tinta  ', descripcion: '  azul  ' }))
  assert.equal(cuerpo.nombre, 'Tinta')
  assert.equal(cuerpo.descripcion, 'azul')
})

test('desdeProducto convierte a texto sin perder el cero ni el precio', () => {
  const f = desdeProducto({ nombre: 'Papel', descripcion: null, precio: 75.5, existencia: 0, umbralStock: 0 })
  assert.equal(f.descripcion, '')
  assert.equal(f.precio, '75.5')
  // Cero es un valor, no una ausencia: debe llegar al formulario como "0".
  assert.equal(f.existencia, '0')
  assert.equal(f.umbralStock, '0')
})

test('bajoUmbral incluye la igualdad, que es cuando se emite la alerta', () => {
  assert.equal(bajoUmbral({ existencia: 5, umbralStock: 5 }), true)
  assert.equal(bajoUmbral({ existencia: 4, umbralStock: 5 }), true)
  assert.equal(bajoUmbral({ existencia: 6, umbralStock: 5 }), false)
  assert.equal(bajoUmbral({ existencia: undefined, umbralStock: 5 }), false)
})

test('el filtro ignora acentos y mayusculas y mira tambien la descripcion', () => {
  const lista = [
    { nombre: 'Sello automático', descripcion: 'Oficina' },
    { nombre: 'Tinta', descripcion: 'Para sellos' },
    { nombre: 'Papel', descripcion: null },
  ]
  assert.equal(filtrar(lista, 'AUTOMATICO').length, 1)
  assert.equal(filtrar(lista, 'sello').length, 2, 'coincide por nombre y por descripcion')
  assert.equal(filtrar(lista, '  ').length, 3, 'una consulta en blanco no filtra')
  assert.equal(filtrar(lista, 'zzz').length, 0)
})
