// Las identidades son estables entre ejecuciones. No usar UUID ni fecha en los nombres.
export const vendedor = { nombre: 'Semilla 3.1 - Vendedor', correo: 'vendedor.semilla@monpoint.test', rol: 'VENDEDOR' }
export const clientes = [
  { nombre: 'Semilla 3.1 - Ana', correo: 'ana.semilla@example.test', telefono: '5550100101', direccion: 'Domicilio de prueba 1' },
  { nombre: 'Semilla 3.1 - Luis', correo: 'luis.semilla@example.test', telefono: '5550100102', direccion: 'Domicilio de prueba 2' },
]
export const productos = [
  { nombre: 'Semilla 3.1 - Tinta', descripcion: 'Producto de prueba', precio: 41.15, existencia: 10, umbralStock: 5 },
  { nombre: 'Semilla 3.1 - Papel', descripcion: 'Producto de prueba', precio: 75.50, existencia: 10, umbralStock: 5 },
  { nombre: 'Semilla 3.1 - Sello', descripcion: 'Producto de prueba', precio: 120, existencia: 10, umbralStock: 5 },
]

export function exigir(condicion, mensaje) {
  if (!condicion) throw new Error(mensaje)
}

// Comparar el valor decimal, no su representación JSON. No redondear ni pasar
// cadenas por Number: perdería diferencias como "41.150000000000000001".
export function decimalCanonico(valor) {
  if (typeof valor !== 'string' && typeof valor !== 'number') return null
  const m = /^(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(String(valor))
  if (!m) return null
  const fraccion = m[2] ?? ''
  const cifras = (m[1] + fraccion).replace(/^0+/, '')
  if (!cifras) return '0'
  const coeficiente = cifras.replace(/0+$/, '')
  const exponente = BigInt(m[3] ?? '0') - BigInt(fraccion.length) + BigInt(cifras.length - coeficiente.length)
  return `${coeficiente}e${exponente}`
}

export function coincidencia(lista, clave, esperado, campos) {
  exigir(Array.isArray(lista), 'La API no devolvió un listado válido.')
  const encontrados = lista.filter(item => item[clave] === esperado[clave])
  exigir(encontrados.length <= 1, `Identidad ambigua: ${esperado[clave]}. Corregir los duplicados antes de continuar.`)
  const actual = encontrados[0]
  if (actual) {
    exigir(typeof actual.id === 'string' && actual.id.length > 0, `Falta id de ${esperado[clave]}.`)
    for (const campo of campos) {
      const coincide = campo === 'precio'
        ? decimalCanonico(actual[campo]) !== null && decimalCanonico(actual[campo]) === decimalCanonico(esperado[campo])
        : actual[campo] === esperado[campo]
      exigir(coincide, `Conflicto en ${esperado[clave]}: ${campo} difiere. No se sobrescribió.`)
    }
  }
  return actual
}

export function origenGateway(env) {
  const error = 'SEMILLA_GATEWAY_URL debe ser un origen HTTP(S) sin credenciales ni ruta.'
  let gateway
  try { gateway = new URL(env.SEMILLA_GATEWAY_URL ?? 'http://localhost:8080') }
  catch { throw new Error(error) }
  exigir(['http:', 'https:'].includes(gateway.protocol) && !gateway.username && !gateway.password
    && gateway.pathname === '/' && !gateway.search && !gateway.hash, error)
  return gateway
}
