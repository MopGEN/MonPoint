import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  ROLES,
  aPeticion,
  desdeUsuario,
  erroresDeCausa,
  esUnoMismo,
  filtrar,
  validar,
  vacio,
} from '../src/usuarios/formulario.js'

const bueno = (c = {}) => ({ ...vacio(), nombre: 'Ana', correo: 'ana@x.test', password: 'Abc123!x', ...c })

test('los roles son los dos del enum Rol', () => {
  assert.deepEqual(ROLES.map((r) => r.valor), ['ADMIN', 'VENDEDOR'])
})

test('el alta exige nombre, correo, contrasena y rol', () => {
  assert.deepEqual(validar(bueno()), {})
  const e = validar(vacio())
  assert.ok(e.nombre && e.correo && e.password)
  assert.equal(e.rol, undefined, 'el rol por omision ya es valido')
})

test('al editar, la contrasena vacia es valida porque conserva la actual', () => {
  assert.deepEqual(validar(bueno({ password: '' }), { edicion: true }), {})
  assert.ok(validar(bueno({ password: '' })).password, 'al crear si se exige')
})

/**
 * La politica completa la aplica PasswordValidator en el servidor y responde
 * con todas las reglas incumplidas. Reproducirla aqui solo podria divergir.
 */
test('no se valida la politica de contrasena en el cliente', () => {
  assert.deepEqual(validar(bueno({ password: 'a' })), {}, 'una contrasena debil pasa el cliente')
})

test('un rol fuera del enum se detiene antes de llegar al servidor', () => {
  assert.ok(validar(bueno({ rol: 'OTRO' })).rol)
})

test('la contrasena nunca se precarga al editar', () => {
  const f = desdeUsuario({ nombre: 'Ana', correo: 'ana@x.test', rol: 'ADMIN', activo: true })
  assert.equal(f.password, '')
  assert.equal(f.rol, 'ADMIN')
})

test('el alta envia contrasena y rol siempre', () => {
  assert.deepEqual(aPeticion(bueno()), {
    nombre: 'Ana', correo: 'ana@x.test', password: 'Abc123!x', rol: 'VENDEDOR',
  })
})

test('al editar, una contrasena vacia viaja como null para conservar la actual', () => {
  const cuerpo = aPeticion(bueno({ password: '' }), { edicion: true, rolOriginal: 'VENDEDOR' })
  assert.equal(cuerpo.password, null)
})

test('al editar, el rol solo viaja cuando cambia', () => {
  const igual = aPeticion(bueno({ rol: 'VENDEDOR' }), { edicion: true, rolOriginal: 'VENDEDOR' })
  assert.equal(igual.rol, null, 'sin cambio no se envia')
  const distinto = aPeticion(bueno({ rol: 'ADMIN' }), { edicion: true, rolOriginal: 'VENDEDOR' })
  assert.equal(distinto.rol, 'ADMIN')
})

test('la contrasena no se recorta: un espacio es un caracter valido', () => {
  assert.equal(aPeticion(bueno({ password: ' Abc123!x ' })).password, ' Abc123!x ')
  assert.equal(aPeticion(bueno({ nombre: '  Ana  ' })).nombre, 'Ana', 'el nombre si se recorta')
})

test('un mapa de campos del servidor se usa tal cual', () => {
  const causa = { campos: { correo: 'Formato de correo inválido' } }
  assert.deepEqual(erroresDeCausa(causa), { correo: 'Formato de correo inválido' })
})

test('la lista de reglas de contrasena se junta bajo su campo', () => {
  const causa = {
    estado: 400,
    cuerpo: { errores: ['La contraseña debe tener al menos 8 caracteres', 'Falta una mayúscula'] },
  }
  assert.deepEqual(erroresDeCausa(causa), {
    password: 'La contraseña debe tener al menos 8 caracteres Falta una mayúscula',
  })
})

test('un 409 se atribuye al correo, que es el unico indice unico de ms-auth', () => {
  const causa = { estado: 409, message: 'Ya existe un usuario con el correo ana@x.test' }
  assert.deepEqual(erroresDeCausa(causa), { correo: 'Ya existe un usuario con el correo ana@x.test' })
})

test('un fallo que no es de campo devuelve null y va al aviso general', () => {
  assert.equal(erroresDeCausa({ estado: 500, message: 'caida' }), null)
  assert.equal(erroresDeCausa({ estado: 400, cuerpo: { errores: [] } }), null)
  assert.equal(erroresDeCausa({ campos: {} }), null)
  assert.equal(erroresDeCausa(null), null)
})

test('la cuenta propia se reconoce por id aunque cambie el correo', () => {
  assert.equal(esUnoMismo({ id: 'u1', correo: 'nuevo@x.test' }, 'u1'), true)
  assert.equal(esUnoMismo({ id: 'u2', correo: 'mismo@x.test' }, 'u1'), false)
  assert.equal(esUnoMismo({ id: 'u1' }, null), false, 'sin identidad no se atribuye la cuenta')
  assert.equal(esUnoMismo(undefined, 'u1'), false)
})

test('el filtro mira nombre, correo y rol', () => {
  const lista = [
    { nombre: 'Ana Ruiz', correo: 'ana@x.test', rol: 'ADMIN' },
    { nombre: 'Luis Pérez', correo: 'luis@x.test', rol: 'VENDEDOR' },
  ]
  assert.equal(filtrar(lista, 'admin').length, 1)
  assert.equal(filtrar(lista, 'perez').length, 1, 'ignora el acento')
  assert.equal(filtrar(lista, 'x.test').length, 2)
})
