import assert from 'node:assert/strict'
import { chromium } from '/tmp/monpoint-browser/node_modules/playwright/index.mjs'
import { readFileSync,writeFileSync } from 'node:fs'
const out='/workspace/.monpoint/reentrega-2026-11-05'
const s=JSON.parse(readFileSync(out+'/estado.json'))
const b=await chromium.connectOverCDP('http://localhost:9223'), p=b.contexts()[0].pages()[0]
async function get(queue,ack,imagen) {
 await p.goto('http://localhost:15672/#/queues/%2F/'+queue)
 await p.getByRole('heading',{name:'Queue '+queue,exact:true}).waitFor()
 if(!await p.locator('select[name=ackmode]').isVisible()) await p.getByText('Get messages',{exact:true}).click()
 await p.locator('select[name=ackmode]').selectOption(ack)
 await p.locator('input[name=count]').fill('100')
 const espera=p.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/'+queue+'/get'))
 await p.locator('input[type=submit][value="Get Message(s)"]').click()
 const r=await espera;assert.equal(r.status(),200)
 const data=await r.json()
 await p.screenshot({path:out+'/'+imagen,fullPage:true})
 return data
}
s.ventaCreadaCapturada=await get('captura.venta-creada','ack_requeue_true','04-venta-creada-original.png')
const originales=s.ventaCreadaCapturada.filter(m=>JSON.parse(m.payload).datos.ventaId===s.aceptada.id)
assert.equal(originales.length,1)
const m=originales[0], evento=JSON.parse(m.payload)
assert.equal(evento.tipo,'VentaCreada');assert.equal(m.properties.message_id,evento.eventoId)
writeFileSync(out+'/venta-creada.payload.json',m.payload)
s.stockReservadoInicial=await get('captura.stock-reservado','ack_requeue_false','05-stock-reservado-original.png')
assert.equal(s.stockReservadoInicial.filter(m=>JSON.parse(m.payload).datos.ventaId===s.aceptada.id).length,1)
s.eventoId=evento.eventoId
writeFileSync(out+'/estado.json',JSON.stringify(s,null,2))
await p.getByRole('link',{name:'Exchanges',exact:true}).click()
await p.getByRole('link',{name:'ventas.eventos',exact:true}).click()
if(!await p.locator('textarea[name=payload]').isVisible()) await p.getByText('Publish message',{exact:true}).click()
console.log(JSON.stringify({eventoId:evento.eventoId,bytes:Buffer.byteLength(m.payload),stockReservadoInicial:s.stockReservadoInicial.length}))
console.log(JSON.stringify(await p.locator('input,select,textarea').evaluateAll(es=>es.filter(e=>e.type!=='hidden').map(e=>({tag:e.tagName,name:e.name,type:e.type,value:e.type==='submit'?e.value:undefined}))),null,2))
process.exit(0)
