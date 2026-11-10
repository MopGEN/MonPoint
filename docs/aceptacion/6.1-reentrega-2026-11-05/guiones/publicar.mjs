import assert from 'node:assert/strict'
import { chromium } from '/tmp/monpoint-browser/node_modules/playwright/index.mjs'
import { readFileSync,writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
const out='/workspace/.monpoint/reentrega-2026-11-05',s=JSON.parse(readFileSync(out+'/estado.json'))
const payload=readFileSync(out+'/venta-creada.payload.json','utf8')
const b=await chromium.connectOverCDP('http://localhost:9223'),p=b.contexts()[0].pages()[0]
const form=p.locator('form').filter({has:p.locator('textarea[name=payload]')})
await form.locator('input[name=routing_key]').fill('venta.creada')
await form.locator('input[name=props_1_mfkey]').fill('message_id')
await form.locator('input[name=props_1_mfkey]').press('Tab')
await form.locator('input[name=props_1_mfvalue]').fill(s.eventoId)
await form.locator('input[name=props_2_mfkey]').fill('content_type')
await form.locator('input[name=props_2_mfkey]').press('Tab')
await form.locator('input[name=props_2_mfvalue]').fill('application/json')
await form.locator('textarea[name=payload]').fill(payload)
assert.equal(await form.locator('textarea[name=payload]').inputValue(),payload)
await p.screenshot({path:out+'/06-republicar-mismo-evento.png',fullPage:true})
const espera=p.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/ventas.eventos/publish'))
s.republicacionInicio=new Date().toISOString()
writeFileSync(out+'/estado.json',JSON.stringify(s,null,2))
await form.locator('input[type=submit][value="Publish message"]').click()
const r=await espera;assert.equal(r.status(),200)
const enviado=r.request().postDataJSON()
assert.equal(enviado.payload,payload)
assert.equal(enviado.properties.message_id,s.eventoId)
assert.equal(enviado.properties.content_type,'application/json')
s.republicacion={instante:new Date().toISOString(),respuesta:await r.json(),enviado,sha256Payload:createHash('sha256').update(payload).digest('hex')}
assert.equal(s.republicacion.respuesta.routed,true)
writeFileSync(out+'/estado.json',JSON.stringify(s,null,2))
await p.screenshot({path:out+'/07-publicacion-aceptada.png',fullPage:true})
console.log(JSON.stringify({eventoId:s.eventoId,routed:true,sha256Payload:s.republicacion.sha256Payload}))
process.exit(0)
