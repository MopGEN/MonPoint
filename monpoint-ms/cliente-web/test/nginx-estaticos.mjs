/** nginx real ya iniciado con el build. No usa API ni credenciales.
 * BASE_URL=http://127.0.0.1:3000 node test/nginx-estaticos.mjs
 */
import assert from 'node:assert/strict'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { resolve } from 'node:path'

const BASE = process.env.BASE_URL ?? 'http://127.0.0.1:3000'
const SALIDA = resolve(process.env.SALIDA ?? '/tmp/monpoint-nginx-estaticos')
const resultados = []
const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex')
async function pedir (ruta, method = 'GET') {
  const respuesta = await fetch(new URL(ruta, BASE), { method, signal: AbortSignal.timeout(10000), redirect: 'error' })
  const bytes = Buffer.from(await respuesta.arrayBuffer())
  resultados.push({ ruta, metodo: method, estado: respuesta.status,
    tipo: respuesta.headers.get('content-type'), nosniff: respuesta.headers.get('x-content-type-options'),
    cache: respuesta.headers.get('cache-control'), bytes: bytes.length, sha256: sha256(bytes) })
  assert.equal(respuesta.headers.get('x-content-type-options'), 'nosniff')
  return { respuesta, bytes }
}
try {
  for (const licencia of ['Inter-OFL.txt', 'IBM-Plex-Mono-OFL.txt']) {
    const { respuesta, bytes } = await pedir(`/fuentes/${licencia}`)
    assert.equal(respuesta.status, 200)
    assert.match(respuesta.headers.get('content-type'), /^text\/plain\b/)
    const original = await readFile(new URL(`../public/fuentes/${licencia}`, import.meta.url))
    assert.deepEqual(bytes, original, `La licencia ${licencia} debe conservar sus bytes`)
  }
  for (const ruta of ['/fuentes/no-existe.txt', '/fuentes/subdirectorio/no-existe.txt', '/assets/no-existe.woff2']) {
    const { respuesta, bytes } = await pedir(ruta)
    assert.equal(respuesta.status, 404, `${ruta} no debe resolver a la SPA`)
    assert.doesNotMatch(bytes.toString(), /<div id="app"/)
    const head = await pedir(ruta, 'HEAD')
    assert.equal(head.respuesta.status, 404)
    assert.equal(head.bytes.length, 0)
  }
  for (const ruta of ['/login', '/caja', '/productos', '/clientes', '/reportes', '/usuarios', '/notificaciones']) {
    const { respuesta, bytes } = await pedir(ruta)
    assert.equal(respuesta.status, 200)
    assert.match(respuesta.headers.get('content-type'), /^text\/html\b/)
    assert.equal(respuesta.headers.get('cache-control'), 'no-cache')
    assert.match(bytes.toString(), /<div id="app"/)
  }
  console.log('PASS: licencias intactas, estaticos inexistentes 404 y siete enlaces profundos de la SPA')
} finally {
  await mkdir(SALIDA, { recursive: true })
  await writeFile(`${SALIDA}/http.json`, JSON.stringify(resultados, null, 2) + '\n')
}
