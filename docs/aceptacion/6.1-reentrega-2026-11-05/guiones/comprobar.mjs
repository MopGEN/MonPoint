import assert from 'node:assert/strict'
import { chromium } from '/tmp/monpoint-browser/node_modules/playwright/index.mjs'
import { execFileSync } from 'node:child_process'
import { readFileSync,writeFileSync } from 'node:fs'
const out='/workspace/.monpoint/reentrega-2026-11-05',s=JSON.parse(readFileSync(out+'/estado.json'))
const logs=execFileSync('docker',['logs','--since',s.republicacionInicio,'monpoint-inventario'],{encoding:'utf8',stdio:['ignore','pipe','pipe']})
s.logDescarte=logs.split('\n').filter(l=>l.includes(s.eventoId)&&l.includes('VENTA_CREADA_DESCARTADO')&&l.includes('motivo=ya_procesado'))
assert.equal(s.logDescarte.length,1)
writeFileSync(out+'/inventario-descarte.txt',s.logDescarte.join('\n')+'\n')
const login=await fetch('http://localhost:8080/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({correo:'admin@monpoint.com',password:process.env.ADMIN_DEFAULT_PASSWORD||'Admin123!'})})
assert.equal(login.status,200)
const {token}=await login.json()
async function api(path){const r=await fetch('http://localhost:8080'+path,{headers:{Authorization:`Bearer ${token}`}});assert.equal(r.status,200);return r.json()}
const b=await chromium.connectOverCDP('http://localhost:9223'),ctx=b.contexts()[0],p=ctx.pages()[0]
async function vacia(nombreFoto){
 await p.goto('http://localhost:15672/#/queues/%2F/captura.stock-reservado')
 await p.getByRole('heading',{name:'Queue captura.stock-reservado',exact:true}).waitFor()
 if(!await p.locator('select[name=ackmode]').isVisible()) await p.getByRole('heading',{name:'Get messages',exact:true}).click()
 await p.locator('select[name=ackmode]').selectOption('ack_requeue_false')
 await p.locator('input[name=count]').fill('100')
 const espera=p.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/captura.stock-reservado/get'))
 await p.locator('input[type=submit][value="Get Message(s)"]').click()
 const r=await espera;assert.equal(r.status(),200)
 const data=await r.json();assert.deepEqual(data,[])
 await p.screenshot({path:out+'/'+nombreFoto,fullPage:true})
 return {fecha:new Date().toISOString(),mensajes:data}
}
s.observacionesStock=[await vacia('08-sin-segundo-stock-reservado.png')]
await new Promise(r=>setTimeout(r,10000))
s.observacionesStock.push(await vacia('09-stock-vacio-segunda-consulta.png'))
s.productoTrasReentrega=await api('/inventario/productos/'+s.productoAntes.id)
s.ventaTrasReentrega=await api('/ventas/'+s.aceptada.id)
assert.equal(s.productoTrasReentrega.existencia,s.productoTrasVenta.existencia)
assert.deepEqual(s.ventaTrasReentrega,s.ventaAntes)
const caja=ctx.pages().find(p=>p.url().startsWith('http://localhost:3000'))
await caja.getByRole('button',{name:'Nueva venta',exact:true}).click()
await caja.getByRole('button',{name:'Actualizar catálogo',exact:true}).click()
await caja.getByLabel('Buscar por nombre').fill(s.productoAntes.nombre)
await caja.getByText('Existencia: '+s.productoTrasReentrega.existencia,{exact:true}).waitFor()
await caja.screenshot({path:out+'/10-existencia-conservada.png',fullPage:true})
s.finComprobacion=new Date().toISOString()
writeFileSync(out+'/estado.json',JSON.stringify(s,null,2))
console.log(JSON.stringify({folio:s.aceptada.folio,eventoId:s.eventoId,existencias:[s.productoAntes.existencia,s.productoTrasVenta.existencia,s.productoTrasReentrega.existencia],descarte:s.logDescarte,stockReservadoAdicional:0,ventaSinCambios:true}))
process.exit(0)
