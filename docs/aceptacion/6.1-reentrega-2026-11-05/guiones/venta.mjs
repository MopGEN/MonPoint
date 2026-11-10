import assert from 'node:assert/strict'
import { chromium } from '/tmp/monpoint-browser/node_modules/playwright/index.mjs'
import { writeFileSync } from 'node:fs'
const out='/workspace/.monpoint/reentrega-2026-11-05'
const browser=await chromium.connectOverCDP('http://localhost:9223')
const ctx=browser.contexts()[0], rabbit=ctx.pages()[0]
rabbit.setDefaultTimeout(15000)
async function bind(exchange,key) {
 if (!await rabbit.locator('input[name=source]').isVisible()) await rabbit.getByText(/^Bindings \(/).click()
 await rabbit.locator('input[name=source]').fill(exchange)
 await rabbit.locator('input[name=routing_key][type=text]').fill(key)
 const r=rabbit.waitForResponse(r=>r.request().method()==='POST' && r.url().includes('/api/bindings/'))
 await rabbit.locator('input[type=submit][value=Bind]').click()
 assert.equal((await r).status(),201)
}
// Ambas colas ya existen; el refresco de RabbitMQ se desactivó durante la captura.
await bind('inventario.eventos','stock.reservado')
await rabbit.screenshot({path:out+'/02-cola-stock.png',fullPage:true})
const caja=await ctx.newPage()
await caja.setViewportSize({width:1440,height:1050})
await caja.goto('http://localhost:3000/login')
const login=caja.waitForResponse(r=>new URL(r.url()).pathname==='/auth/login')
await caja.getByLabel('Correo',{exact:true}).fill('admin@monpoint.com')
await caja.getByLabel('Contrasena',{exact:true}).fill(process.env.ADMIN_DEFAULT_PASSWORD || 'Admin123!')
await caja.getByRole('button',{name:'Entrar',exact:true}).click()
const lr=await login;assert.equal(lr.status(),200)
const token=(await lr.json()).token
async function api(path) {
 const r=await fetch('http://localhost:8080'+path,{headers:{Authorization:`Bearer ${token}`}})
 assert.equal(r.status,200)
 return r.json()
}
await caja.getByRole('heading',{name:'Caja',exact:true}).waitFor()
const all=await api('/inventario/productos')
const encontrados=all.filter(p=>p.nombre==='Semilla 3.1 - Tinta')
assert.equal(encontrados.length,1)
const producto=encontrados[0];assert.ok(producto.existencia>=1)
const estado={inicio:new Date().toISOString(),productoAntes:producto,modo:'Consola web RabbitMQ operada con Playwright; venta desde Caja',colas:['captura.venta-creada','captura.stock-reservado']}
writeFileSync(out+'/estado.json',JSON.stringify(estado,null,2))
await caja.getByLabel('Buscar por nombre').fill(producto.nombre)
await caja.getByRole('button',{name:'Agregar '+producto.nombre,exact:true}).click()
await caja.getByLabel('Cantidad',{exact:true}).fill('1')
const aceptacion=caja.waitForResponse(r=>r.request().method()==='POST' && new URL(r.url()).pathname==='/ventas')
await caja.getByRole('button',{name:'Cobrar',exact:true}).click()
const r=await aceptacion;assert.equal(r.status(),202)
estado.aceptada=await r.json()
writeFileSync(out+'/estado.json',JSON.stringify(estado,null,2))
await caja.getByRole('heading',{name:'Venta confirmada',exact:true}).waitFor()
estado.ventaAntes=await api('/ventas/'+estado.aceptada.id)
estado.productoTrasVenta=await api('/inventario/productos/'+producto.id)
assert.equal(estado.productoTrasVenta.existencia,producto.existencia-1)
assert.equal(estado.ventaAntes.estado,'CONFIRMADA')
writeFileSync(out+'/estado.json',JSON.stringify(estado,null,2))
await caja.screenshot({path:out+'/03-venta-confirmada.png',fullPage:true})
console.log(JSON.stringify({ventaId:estado.aceptada.id,folio:estado.aceptada.folio,stockAntes:producto.existencia,stockTrasVenta:estado.productoTrasVenta.existencia}))
process.exit(0)
