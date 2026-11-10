import assert from 'node:assert/strict'
import { chromium } from '/tmp/monpoint-browser/node_modules/playwright/index.mjs'
import { readFileSync,writeFileSync,statSync } from 'node:fs'
const out='/workspace/.monpoint/reentrega-2026-11-05',s=JSON.parse(readFileSync(out+'/estado.json'))
const login=await fetch('http://localhost:8080/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({correo:'admin@monpoint.com',password:process.env.ADMIN_DEFAULT_PASSWORD||'Admin123!'})})
assert.equal(login.status,200)
const {token}=await login.json()
async function api(path){const r=await fetch('http://localhost:8080'+path,{headers:{Authorization:`Bearer ${token}`}});assert.equal(r.status,200);return r.json()}
s.productoTrasReentrega=await api('/inventario/productos/'+s.productoAntes.id)
s.ventaTrasReentrega=await api('/ventas/'+s.aceptada.id)
assert.equal(s.productoTrasReentrega.existencia,s.productoTrasVenta.existencia)
assert.deepEqual(s.ventaTrasReentrega,s.ventaAntes)
s.logDescarte=readFileSync(out+'/inventario-descarte.txt','utf8').trim().split('\n')
assert.equal(s.logDescarte.length,1)
assert.ok(s.logDescarte[0].includes(s.eventoId)&&s.logDescarte[0].includes('VENTA_CREADA_DESCARTADO')&&s.logDescarte[0].includes('motivo=ya_procesado'))
s.stockPosteriorEvidencia={imagenes:['08-sin-segundo-stock-reservado.png','09-stock-vacio-segunda-consulta.png'].map(archivo=>({archivo,fechaArchivo:statSync(out+'/'+archivo).mtime.toISOString()})),nota:'Ambas consultas Get Message(s) devolvieron [] y se capturó Queue is empty. El guion comprobar.mjs falló después al buscar la pestaña de Caja, antes de persistir esas respuestas JSON. Se conservan las capturas, no se presentan como respuestas JSON archivadas.'}
s.incidencia='Tras comprobar el descarte, dos lecturas vacías y las APIs, comprobar.mjs no encontró Caja porque los pasos anteriores seleccionaban pestañas por índice. Se completó la captura en un contexto nuevo y se repitieron únicamente GET de producto/venta. No hubo otra venta ni republicación.'
writeFileSync(out+'/estado.json',JSON.stringify(s,null,2))
const b=await chromium.connectOverCDP('http://localhost:9223'),ctx=await b.newContext({viewport:{width:1440,height:1050}}),p=await ctx.newPage()
await p.goto('http://localhost:3000/login')
await p.getByLabel('Correo',{exact:true}).fill('admin@monpoint.com')
await p.getByLabel('Contrasena',{exact:true}).fill(process.env.ADMIN_DEFAULT_PASSWORD||'Admin123!')
await p.getByRole('button',{name:'Entrar',exact:true}).click()
await p.getByRole('heading',{name:'Caja',exact:true}).waitFor()
await p.getByLabel('Buscar por nombre').fill(s.productoAntes.nombre)
await p.getByText('Existencia: '+s.productoTrasReentrega.existencia,{exact:true}).waitFor()
await p.screenshot({path:out+'/10-existencia-conservada.png',fullPage:true})
s.finComprobacion=new Date().toISOString()
writeFileSync(out+'/estado.json',JSON.stringify(s,null,2))
console.log(JSON.stringify({folio:s.aceptada.folio,existencias:[s.productoAntes.existencia,s.productoTrasVenta.existencia,s.productoTrasReentrega.existencia],estado:s.ventaTrasReentrega.estado,log:s.logDescarte,limpieza:s.limpieza}))
await ctx.close()
process.exit(0)
