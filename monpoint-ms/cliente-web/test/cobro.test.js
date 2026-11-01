import { test } from 'node:test'
import assert from 'node:assert/strict'
import { estadoInicial, crearCobro, centavos, dinero, estimado } from '../src/caja/cobro.js'
const flush = async () => {
  for (let i = 0; i < 8; i++) await Promise.resolve()
}
function relojFalso() {
  let ahora = 0,
    id = 0
  const tareas = new Map()
  return {
    ahora: () => ahora,
    poner: (fn, ms) => {
      tareas.set(++id, { fn, tiempo: ahora + ms })
      return id
    },
    quitar: (id) => tareas.delete(id),
    async avanzar(ms) {
      const fin = ahora + ms
      while (true) {
        const siguiente = [...tareas]
          .filter(([, t]) => t.tiempo <= fin)
          .sort((a, b) => a[1].tiempo - b[1].tiempo)[0]
        if (!siguiente) break
        const [id, t] = siguiente
        ahora = t.tiempo
        tareas.delete(id)
        t.fn()
        await flush()
      }
      ahora = fin
      await flush()
    },
    pendientes: () => tareas.size,
  }
}
function preparar(api) {
  const s = estadoInicial(),
    reloj = relojFalso()
  s.lineas = [{ productoId: 'p1', precio: '41.15', cantidad: 3, nombre: 'Tinta' }]
  return { s, reloj, caja: crearCobro(s, api, reloj) }
}
const aceptada = { id: 'v1', folio: 1042, estado: 'PENDIENTE' }
const terminal = (estado) => ({
  ...aceptada,
  estado,
  total: '123.45',
  motivo: 'Existencia insuficiente',
  productoIdRechazado: 'p1',
})
const enviar = async () => aceptada

test('estimado decimal exacto y formato', () => {
  assert.equal(centavos('41.15'), 4115n)
  assert.equal(dinero(estimado([{ precio: '41.15', cantidad: 3 }])), '$123.45')
})
test('doble clic: un POST, payload sin importes y total final del servidor', async () => {
  let terminar,
    llamadas = 0,
    payload
  const { s, caja } = preparar({
    enviar: (_, p) => {
      llamadas++
      payload = p
      return new Promise((r) => {
        terminar = r
      })
    },
    obtener: async () => ({ ...terminal('CONFIRMADA'), total: '125.00' }),
  })
  const a = caja.cobrar()
  await caja.cobrar()
  assert.equal(llamadas, 1)
  assert.equal(s.estado, 'enviando')
  assert.deepEqual(payload, { clienteId: null, lineas: [{ productoId: 'p1', cantidad: 3 }] })
  terminar(aceptada)
  await a
  await flush()
  assert.equal(s.estado, 'confirmada')
  assert.equal(s.venta.total, '125.00')
  assert.equal(s.lineas.length, 0)
  await caja.cobrar()
  assert.equal(llamadas, 1)
})
test('cancelación conserva carrito, motivo e identificador; permite corregir y cobrar', async () => {
  let veces = 0
  const { s, caja } = preparar({
    enviar: async () => {
      veces++
      return aceptada
    },
    obtener: async () => terminal('CANCELADA'),
  })
  await caja.cobrar()
  await flush()
  assert.equal(s.estado, 'cancelada')
  assert.equal(s.lineas[0].cantidad, 3)
  assert.equal(s.venta.productoIdRechazado, 'p1')
  assert.equal(s.venta.motivo, 'Existencia insuficiente')
  s.lineas[0].cantidad = 1
  await caja.cobrar()
  await flush()
  assert.equal(veces, 2)
})
test('cancelaciones históricas sin producto rechazado', async () => {
  const { s, caja } = preparar({
    enviar,
    obtener: async () => ({ ...terminal('CANCELADA'), productoIdRechazado: null }),
  })
  await caja.cobrar()
  await flush()
  assert.equal(s.estado, 'cancelada')
  assert.equal(s.lineas.length, 1)
})
test('sondeo secuencial espera 800 ms después de responder', async () => {
  let respuesta,
    consultas = 0
  const { s, reloj, caja } = preparar({
    enviar,
    obtener: () => {
      consultas++
      return new Promise((r) => {
        respuesta = r
      })
    },
  })
  await caja.cobrar()
  await reloj.avanzar(900)
  assert.equal(consultas, 1)
  respuesta(aceptada)
  await flush()
  await reloj.avanzar(799)
  assert.equal(consultas, 1)
  await reloj.avanzar(1)
  assert.equal(consultas, 2)
  assert.equal(s.estado, 'enProceso')
  caja.detener()
})
test('corte real a 10 s aborta petición colgada e ignora respuesta tardía', async () => {
  let resolver, signal
  const { s, reloj, caja } = preparar({
    enviar,
    obtener: (_, o) => {
      signal = o.senal
      return new Promise((r) => {
        resolver = r
      })
    },
  })
  await caja.cobrar()
  await reloj.avanzar(9999)
  assert.equal(s.estado, 'enProceso')
  await reloj.avanzar(1)
  assert.equal(s.estado, 'sinRespuesta')
  assert.equal(signal.aborted, true)
  assert.equal(s.pendientes[0].folio, 1042)
  assert.equal(s.lineas.length, 0)
  resolver(terminal('CONFIRMADA'))
  await flush()
  assert.equal(s.estado, 'sinRespuesta')
})
test('fallos de red del GET nunca cancelan; corte y consulta manual no afectan otro carrito', async () => {
  let falla = true
  const { s, reloj, caja } = preparar({
    enviar,
    obtener: async () => {
      if (falla) throw new Error('red')
      return terminal('CONFIRMADA')
    },
  })
  await caja.cobrar()
  await reloj.avanzar(10000)
  assert.equal(s.estado, 'sinRespuesta')
  falla = false
  s.lineas.push({ productoId: 'p2', cantidad: 2, precio: '10.00' })
  await caja.consultarPendiente(s.pendientes[0])
  assert.equal(s.pendientes[0].detalle.estado, 'CONFIRMADA')
  assert.equal(s.lineas[0].productoId, 'p2')
})
test('POST ambiguo se archiva y no ofrece repetir el mismo carrito', async () => {
  const { s, caja } = preparar({
    enviar: async () => {
      throw new Error('conexión perdida')
    },
  })
  await caja.cobrar()
  assert.equal(s.estado, 'sinRespuesta')
  assert.equal(s.lineas.length, 0)
  assert.equal(s.pendientes.length, 1)
})
test('POST colgado vence a 10 segundos y aborta', async () => {
  let signal
  const { s, caja, reloj } = preparar({
    enviar: (_, __, o) => {
      signal = o.senal
      return new Promise(() => {})
    },
  })
  void caja.cobrar()
  await reloj.avanzar(10000)
  assert.equal(s.estado, 'sinRespuesta')
  assert.equal(signal.aborted, true)
})
test('rechazo HTTP de validación conserva el carrito y muestra detail', async () => {
  const { s, caja } = preparar({
    enviar: async () => {
      throw Object.assign(new Error('cliente no disponible'), { estado: 400 })
    },
  })
  await caja.cobrar()
  assert.equal(s.estado, 'inactivo')
  assert.equal(s.lineas.length, 1)
  assert.equal(s.error, 'cliente no disponible')
})
test('cantidades inválidas no llegan al servidor', async () => {
  const { s, caja } = preparar({ enviar: () => assert.fail('POST inesperado') })
  for (const n of [0, -1, 1.5, '', 2147483648]) {
    s.lineas[0].cantidad = n
    await caja.cobrar()
    assert.equal(s.estado, 'inactivo')
  }
})
test('desmontar aborta, guarda el folio y elimina temporizadores', async () => {
  let signal, responder
  const { s, caja, reloj } = preparar({
    enviar,
    obtener: (_, o) => {
      signal = o.senal
      return new Promise((r) => {
        responder = r
      })
    },
  })
  await caja.cobrar()
  caja.detener()
  assert.equal(signal.aborted, true)
  assert.equal(reloj.pendientes(), 0)
  assert.equal(s.pendientes[0].id, 'v1')
  responder(terminal('CANCELADA'))
  await flush()
  assert.equal(s.estado, 'sinRespuesta')
})

test('precios de tres decimales: suma exacta y redondeo solo del estimado', () => {
  assert.equal(centavos('41.155'), 4116n)
  assert.equal(estimado([{ precio: '41.155', cantidad: 3 }]), '123.47')
  assert.equal(dinero('41.155'), '$41.155')
  for (const p of [null, undefined, 'incorrecto', -1, Infinity]) {
    assert.equal(centavos(p), null)
    assert.equal(dinero(p), 'Importe no disponible')
    assert.equal(estimado([{ precio: p, cantidad: 1 }]), null)
  }
})

test('401 tras limpiar sesión conserva identidad y folio aun si el desmontaje ocurre primero', async () => {
  const { createPinia, setActivePinia } = await import('pinia')
  const { useCaja } = await import('../src/stores/caja.js')
  for (const desmontarPrimero of [false, true]) {
    setActivePinia(createPinia())
    const store = useCaja(),
      s = store.cobro
    s.lineas = [{ productoId: 'p1', precio: '41.15', cantidad: 3 }]
    let caja
    caja = crearCobro(
      s,
      {
        enviar,
        obtener: async () => {
          store.limpiarSesion()
          assert.equal(store.cobro, s)
          if (desmontarPrimero) caja.detener()
          throw Object.assign(new Error('Sesión terminada'), { estado: 401 })
        },
      },
      relojFalso(),
    )
    await caja.cobrar()
    await flush()
    assert.equal(store.cobro.pendientes.length, 1)
    assert.equal(store.cobro.pendientes[0].folio, 1042)
    assert.equal(store.cobro.pendientes[0].id, 'v1')
    caja.detener()
    store.limpiarSesion()
    assert.equal(store.cobro.pendientes.length, 1)
  }
})
