import assert from 'node:assert/strict'
import { chromium } from '/tmp/monpoint-browser/node_modules/playwright/index.mjs'
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
const out = '/workspace/.monpoint/nginx/caja'
const browser = await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']})
const page = await browser.newPage({viewport:{width:1360,height:1000}})
page.setDefaultTimeout(20000)
const evidence={commit:execFileSync('git',['rev-parse','HEAD'],{cwd:'/workspace/MonPoint',encoding:'utf8'}).trim(),origin:'http://localhost:3000',api:'http://localhost:8080',startedAt:new Date().toISOString(),cases:[],cors:[],errors:[],consoleErrors:[],httpErrors:[]}
let token, producto, stopped=false, postCount=0, activeGets=0, maxGets=0, recovering=false
const inFlight=new Set()
page.on('pageerror',e=>evidence.errors.push(e.message))
page.on('console',m=>{if(m.type()==='error'){
 const entry={text:m.text(),url:m.location().url,recovering}
 evidence.consoleErrors.push(entry)
 const favicon=entry.url==='http://localhost:3000/favicon.ico' && entry.text==='Failed to load resource: the server responded with a status of 404 (Not Found)'
 const unavailable=recovering && entry.url==='http://localhost:8080/inventario/productos/'+producto?.id && entry.text==='Failed to load resource: the server responded with a status of 503 (Service Unavailable)'
 if(!favicon && !unavailable)evidence.errors.push(entry)
}})
page.on('response',r=>{if(r.status()>=400)evidence.httpErrors.push({url:r.url(),status:r.status(),recovering})})
page.on('request',r=>{if(r.method()==='POST' && new URL(r.url()).pathname==='/ventas')postCount++;if(r.method()==='GET' && /\/ventas\/[a-f0-9]{24}$/.test(r.url())) {inFlight.add(r);activeGets++;maxGets=Math.max(maxGets,activeGets)}})
const finish=r=>{if(inFlight.delete(r))activeGets--}
page.on('requestfinished',finish); page.on('requestfailed',finish)
page.on('response',async r=>{if(r.url().startsWith('http://localhost:8080')){const headers=await r.allHeaders();if(headers['access-control-allow-origin'])evidence.cors.push({method:r.request().method(),path:new URL(r.url()).pathname,status:r.status(),origin:headers['access-control-allow-origin']})}})
async function api(path,method='GET',body) {
 return page.evaluate(async ({path,method,body,token})=>{
  const r=await fetch('http://localhost:8080'+path,{method,headers:{Authorization:'Bearer '+token,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,credentials:'omit'})
  const text=await r.text(); return {status:r.status,data:text?JSON.parse(text):null}
 },{path,method,body,token})
}
async function until(fn,ms=30000) {const end=Date.now()+ms;while(Date.now()<end){if(await fn())return;await new Promise(r=>setTimeout(r,500))}throw new Error('Readiness timeout')}
function mongoRead(js){return execFileSync('docker',['exec','-i','monpoint-mongodb','bash','-c','mongosh --quiet -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --file /dev/stdin'],{input:js,encoding:'utf8'}).trim()}
async function cobrar(cantidad) {
 await page.getByLabel('Cantidad',{exact:true}).fill(String(cantidad))
 await page.evaluate(()=>{
  window.__proceso=[]
  window.__observer?.disconnect()
  window.__observer=new MutationObserver(()=>{const n=document.querySelector('.proceso');if(n)window.__proceso.push({at:Date.now(),text:n.innerText})})
  window.__observer.observe(document.body,{childList:true,subtree:true,characterData:true})
 })
 const response=page.waitForResponse(r=>r.request().method()==='POST' && new URL(r.url()).pathname==='/ventas')
 await page.getByRole('button',{name:'Cobrar',exact:true}).evaluate(b=>{b.click();b.click()})
 const r=await response
 assert.equal(r.status(),202,await r.text())
 return {...await r.json(),acceptedAt:Date.now()}
}
try {
 await page.goto('http://localhost:3000/login')
 const login=page.waitForResponse(r=>new URL(r.url()).pathname==='/auth/login')
 await page.getByLabel('Correo',{exact:true}).fill('admin@monpoint.com')
 await page.getByLabel('Contrasena',{exact:true}).fill(process.env.ADMIN_DEFAULT_PASSWORD || 'Admin123!')
 await page.getByRole('button',{name:'Entrar',exact:true}).click()
 const lr=await login;assert.equal(lr.status(),200,await lr.text());token=(await lr.json()).token
 await page.getByRole('heading',{name:'Caja',exact:true}).waitFor()
 const nombre='Aceptación 5.5 '+Date.now()
 const creada=await api('/inventario/productos','POST',{nombre,descripcion:'Dato de aceptación de main; conservar folios como evidencia',precio:41.15,existencia:10,umbralStock:0})
 assert.equal(creada.status,201,JSON.stringify(creada.data));producto=creada.data
 evidence.productoId=producto.id
 await until(()=>mongoRead(`print(db.getSiblingDB('ventas_db').productos_replica.countDocuments({_id:ObjectId('${producto.id}')}))`)==='1')
 await page.getByRole('button',{name:'Actualizar catálogo'}).click()
 await page.getByLabel('Buscar por nombre').fill(nombre)
 await page.getByRole('button',{name:'Agregar '+nombre,exact:true}).click()
 const c=await cobrar(3)
 await page.getByRole('heading',{name:'Venta confirmada',exact:true}).waitFor()
 const cv=await api('/ventas/'+c.id)
 assert.equal(cv.data.estado,'CONFIRMADA');assert.equal(String(cv.data.total),'123.45')
 assert.equal(await page.locator('.definitivo').innerText(),'$123.45');assert.equal(await page.locator('.lineas .linea').count(),0)
 const proceso=await page.evaluate(()=>window.__proceso)
 assert.ok(proceso.some(x=>x.text.includes('Venta en proceso')&&x.text.includes(String(c.folio))))
 assert.equal((await api('/inventario/productos/'+producto.id)).data.existencia,7)
 evidence.cases.push({case:'CONFIRMADA',id:c.id,folio:c.folio,total:cv.data.total,ms:Date.now()-c.acceptedAt,stock:7,proceso})
 await page.screenshot({path:out+'/confirmada.png',fullPage:true})
 await page.getByRole('button',{name:'Nueva venta'}).click()
 await page.getByRole('button',{name:'Agregar '+nombre,exact:true}).click()
 const x=await cobrar(20)
 await page.getByRole('heading',{name:'Venta cancelada',exact:true}).waitFor()
 const xv=await api('/ventas/'+x.id)
 assert.equal(xv.data.estado,'CANCELADA');assert.equal(xv.data.productoIdRechazado,producto.id)
 assert.ok(xv.data.motivo.includes('existencia insuficiente'))
 assert.equal(await page.getByLabel('Cantidad',{exact:true}).inputValue(),'20');assert.ok(await page.getByLabel('Cantidad',{exact:true}).isEnabled())
 assert.equal(await page.locator('.linea.rechazada').count(),1)
 assert.ok((await page.locator('.cancelada').innerText()).includes(xv.data.motivo))
 assert.equal((await api('/inventario/productos/'+producto.id)).data.existencia,7)
 evidence.cases.push({case:'CANCELADA',id:x.id,folio:x.folio,productoIdRechazado:xv.data.productoIdRechazado,motivo:xv.data.motivo,stock:7,ms:Date.now()-x.acceptedAt})
 await page.screenshot({path:out+'/cancelada.png',fullPage:true})
 stopped=true
 execFileSync('docker',['stop','monpoint-inventario'],{stdio:'pipe',timeout:30000})
 const p=await cobrar(2)
 await page.getByRole('heading',{name:'Seguimiento de operaciones'}).waitFor()
 const elapsed=Date.now()-p.acceptedAt
 assert.ok(elapsed>=9500 && elapsed<=12500,`Corte tardó ${elapsed} ms`)
 assert.equal(await page.locator('.lineas .linea').count(),0)
 assert.equal(await page.locator('.operacion strong').innerText(),'Folio '+p.folio)
 const pv=await api('/ventas/'+p.id);assert.equal(pv.data.estado,'PENDIENTE')
 assert.ok(await page.getByRole('button',{name:'Agregar '+nombre,exact:true}).isEnabled())
 evidence.cases.push({case:'SIN_RESPUESTA',id:p.id,folio:p.folio,estadoServidor:pv.data.estado,ms:elapsed})
 await page.screenshot({path:out+'/sin-respuesta.png',fullPage:true})
 recovering=true
 execFileSync('docker',['start','monpoint-inventario'],{stdio:'pipe'});stopped=false
 await until(async()=> (await api('/ventas/'+p.id)).data.estado==='CONFIRMADA',90000)
 await page.getByRole('button',{name:'Consultar de nuevo'}).click()
 await page.getByText('✓ Venta confirmada · Total cobrado: $82.30',{exact:true}).waitFor()
 await until(async()=> (await api('/inventario/productos/'+producto.id)).data.existencia===5,30000)
 evidence.recovery={id:p.id,estado:'CONFIRMADA',stock:5}
 recovering=false
 await page.screenshot({path:out+'/recuperada.png',fullPage:true})
 evidence.postCount=postCount;evidence.maxConcurrentSaleQueries=maxGets
 assert.deepEqual(evidence.httpErrors.filter(x=> !(x.status===404 && x.url==='http://localhost:3000/favicon.ico') && !(x.status===503 && x.recovering && x.url==='http://localhost:8080/inventario/productos/'+producto.id)),[])
 assert.equal(postCount,3);assert.equal(maxGets,1);assert.deepEqual(evidence.errors,[])
 assert.ok(evidence.cors.some(x=>x.path==='/auth/login'&&x.origin==='http://localhost:3000'))
 evidence.postCount=postCount;evidence.maxConcurrentSaleQueries=maxGets;evidence.result='PASS'
 console.log(JSON.stringify({result:'PASS',cases:evidence.cases,recovery:evidence.recovery,postCount,maxGets},null,2))
} catch(e) {
 evidence.result='FAIL';evidence.failure=e.stack
 console.error(e.stack)
 console.error('Browser errors:',JSON.stringify(evidence.errors))
 console.error('Visible page:',(await page.locator('body').innerText().catch(()=>'' )).slice(0,3000))
 await page.screenshot({path:out+'/failure.png',fullPage:true}).catch(()=>{})
 process.exitCode=1
} finally {
 if(stopped)execFileSync('docker',['start','monpoint-inventario'],{stdio:'pipe'})
 evidence.finishedAt=new Date().toISOString()
 writeFileSync(out+'/real-results.json',JSON.stringify(evidence,null,2))
 await browser.close()
}
