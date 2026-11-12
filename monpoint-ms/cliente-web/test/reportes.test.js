import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  MAX_PUNTOS,
  PERIODOS,
  consultaValida,
  cuentaPuntos,
  etiquetaPunto,
  nombreDocumento,
  rangoPorOmision,
  rutaAgregado,
  validar,
  validarHistorial,
} from '../src/reportes/consulta.js'

test('las granularidades son las tres del enum Periodo del servicio', () => {
  assert.deepEqual(PERIODOS.map((p) => p.valor), ['DIA', 'MES', 'ANIO'])
})

/**
 * El "hoy" se construye en hora LOCAL, no con `Date.UTC`: el rango que espera
 * el usuario es el de su calendario. En una zona al oeste de Greenwich, la
 * medianoche UTC del dia 11 todavia es el dia 10 para quien mira la pantalla,
 * y un fixture en UTC probaria un comportamiento distinto del real.
 */
test('el rango por omision son treinta dias del calendario local', () => {
  const r = rangoPorOmision(new Date(2026, 10, 11, 12, 0, 0))
  assert.deepEqual(r, { periodo: 'DIA', desde: '2026-10-13', hasta: '2026-11-11' })
  assert.equal(cuentaPuntos('DIA', r.desde, r.hasta), 30)
})

test('la hora del dia no cambia el rango', () => {
  const temprano = rangoPorOmision(new Date(2026, 10, 11, 0, 30, 0))
  const tarde = rangoPorOmision(new Date(2026, 10, 11, 23, 30, 0))
  assert.deepEqual(temprano, tarde)
})

test('el rango por omision es valido', () => {
  assert.equal(consultaValida(rangoPorOmision(new Date(2026, 10, 11, 12, 0, 0))), true)
})

/** Reproduce `Periodo.periodosEntre`: cuenta periodos, no dias. */
test('la cuenta de puntos sigue la granularidad, no el calendario', () => {
  assert.equal(cuentaPuntos('DIA', '2026-11-10', '2026-11-12'), 3)
  assert.equal(cuentaPuntos('MES', '2026-11-30', '2026-12-01'), 2, 'dos meses aunque sea un dia')
  assert.equal(cuentaPuntos('ANIO', '2026-01-01', '2026-12-31'), 1, 'un solo anio')
  assert.equal(cuentaPuntos('ANIO', '2025-12-31', '2026-01-01'), 2)
})

test('la cuenta de dias cruza un cambio de mes y un anio bisiesto', () => {
  assert.equal(cuentaPuntos('DIA', '2026-01-31', '2026-02-01'), 2)
  assert.equal(cuentaPuntos('DIA', '2024-02-28', '2024-03-01'), 3, '2024 es bisiesto')
})

test('una fecha inexistente se rechaza en vez de desplazarse', () => {
  assert.equal(cuentaPuntos('DIA', '2026-02-31', '2026-03-01'), null)
  assert.ok(validar({ periodo: 'DIA', desde: '2026-02-31', hasta: '2026-03-01' }).desde)
  assert.ok(validar({ periodo: 'DIA', desde: '11/11/2026', hasta: '2026-11-12' }).desde)
})

test('la fecha final no puede ser anterior a la inicial', () => {
  const e = validar({ periodo: 'DIA', desde: '2026-11-12', hasta: '2026-11-11' })
  assert.ok(e.hasta)
  // Un solo dia es un rango valido.
  assert.deepEqual(validar({ periodo: 'DIA', desde: '2026-11-11', hasta: '2026-11-11' }), {})
})

/**
 * El tope visual 370 es mas estricto que los 1000 puntos del servidor.
 */
test('un rango que excede el tope de puntos se rechaza con su cuenta', () => {
  const e = validar({ periodo: 'DIA', desde: '2020-01-01', hasta: '2026-11-11' })
  assert.ok(e.hasta.includes(String(cuentaPuntos('DIA', '2020-01-01', '2026-11-11'))))
  assert.ok(e.hasta.includes(String(MAX_PUNTOS)))
})

test('el mismo rango pasa con una granularidad mayor', () => {
  const largo = { desde: '2020-01-01', hasta: '2026-11-11' }
  assert.ok(validar({ periodo: 'DIA', ...largo }).hasta)
  assert.deepEqual(validar({ periodo: 'MES', ...largo }), {}, '83 meses caben')
  assert.deepEqual(validar({ periodo: 'ANIO', ...largo }), {})
})

test('la ruta del agregado escapa sus parametros', () => {
  assert.equal(
    rutaAgregado({ periodo: 'DIA', desde: '2026-11-01', hasta: '2026-11-30' }),
    '/reportes/ventas/agregado?periodo=DIA&desde=2026-11-01&hasta=2026-11-30',
  )
})

/**
 * Construir la etiqueta con `new Date(iso)` la interpretaria en UTC y en una
 * zona al oeste de Greenwich mostraria el dia anterior. La zona del proyecto
 * es America/Mexico_City, asi que el fallo seria visible, no teorico.
 */
test('la etiqueta no se desplaza un dia por la zona horaria', () => {
  assert.equal(etiquetaPunto('DIA', '2026-11-01'), '1 nov')
  assert.equal(etiquetaPunto('DIA', '2026-01-01'), '1 ene')
  assert.equal(etiquetaPunto('MES', '2026-03-01'), 'mar 2026')
  assert.equal(etiquetaPunto('ANIO', '2026-01-01'), '2026')
})

test('una etiqueta de una fecha ilegible devuelve el texto original', () => {
  assert.equal(etiquetaPunto('DIA', 'sin-fecha'), 'sin-fecha')
})

test('el nombre del documento lo pone el cliente porque el servidor no lo expone', () => {
  assert.equal(nombreDocumento('listado-productos'), 'listado-productos.pdf')
  assert.equal(
    nombreDocumento('historial-ventas', '2026-11-01_2026-11-30'),
    'historial-ventas-2026-11-01_2026-11-30.pdf',
  )
})

test('un periodo desconocido no se interpreta como DIA', () => {
  const consulta = { periodo: 'SEMANA', desde: '2026-11-01', hasta: '2026-11-11' }
  assert.ok(validar(consulta).periodo)
  assert.equal(cuentaPuntos('SEMANA', consulta.desde, consulta.hasta), null)
})

test('el historial limita dias aunque la agregacion permita meses o anios', () => {
  const consulta = { periodo: 'MES', desde: '2024-01-01', hasta: '2024-12-31' }
  assert.deepEqual(validar(consulta), {})
  assert.deepEqual(validarHistorial(consulta), {}, '366 dias inclusivos')
  assert.ok(validarHistorial({ ...consulta, hasta: '2025-01-01' }).hasta)
  assert.ok(validarHistorial({ desde: '2026-11-12', hasta: '2026-11-11' }).hasta)
  assert.ok(validarHistorial({ desde: '', hasta: '2026-11-11' }).desde)
})

test('las fechas de anios menores que 100 no se desplazan al siglo XX', () => {
  assert.deepEqual(validar({ periodo: 'DIA', desde: '0050-01-01', hasta: '0050-01-02' }), {})
  assert.equal(cuentaPuntos('DIA', '0050-01-01', '0050-01-02'), 2)
})
