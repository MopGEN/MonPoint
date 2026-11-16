import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { sembrarAutotachi, cuentas, productos } from '/workspace/MonPoint/monpoint-ms/scripts/semilla-autotachi.mjs'
const dir = '/workspace/.monpoint/semilla-nominal/'
const env = Object.fromEntries(readFileSync(dir + 'prueba.env', 'utf8').trim().split('\n').map(l => { const i=l.indexOf('='); return [l.slice(0,i),l.slice(i+1)] }))
env.SEMILLA_GATEWAY_URL = 'http://127.0.0.1:18090'
const resultado = { fecha: new Date().toISOString(), origen: env.SEMILLA_GATEWAY_URL, comprobaciones: [] }
async function api(metodo,ruta,cuerpo,token,esperado=200) {
 const res=await fetch(new URL(ruta,env.SEMILLA_GATEWAY_URL),{method:metodo,headers:{...(cuerpo ? {'Content-Type':'application/json'} : {}),...(token ? {Authorization:'Bearer '+token} : {})},...(cuerpo?{body:JSON.stringify(cuerpo)}:{}),signal:AbortSignal.timeout(10000)})
 assert.equal(res.status,esperado,`${metodo} ${ruta}: HTTP ${res.status}`)
 return esperado===204?null:await res.json()
}
async function login(cuenta=cuentas[0],password=env[cuenta.variable]) { return await api('POST','/auth/login',{correo:cuenta.correo,password}) }
// Esperar disponibilidad por la ruta real, sin usar un 200 de health como prueba del contrato.
for (let i=0;;i++) {
 try { const a=await login(); await api('GET','/inventario/productos',null,a.token);break }
 catch(e) { if(i===40)throw e; await new Promise(r=>setTimeout(r,500)) }
}
const peticiones=[]
const fetchObservado=(url,opts)=>{peticiones.push({metodo:opts.method,ruta:new URL(url).pathname});return fetch(url,opts)}
const primera=await sembrarAutotachi({env,fetchImpl:fetchObservado})
writeFileSync(dir+'primera.json',JSON.stringify(primera,null,2)+'\n')
assert.equal(primera.productos.length,23)
assert.ok(primera.productos.every(p=>p.accion==='CREADO'))
assert.equal(peticiones.filter(p=>p.metodo==='POST'&&p.ruta==='/inventario/productos').length,23)
resultado.comprobaciones.push({caso:'Importación por Gateway',altas:23,roles:primera.usuarios.map(u=>u.rol),stockCero:primera.productos.at(-1).existencia})
const admin=await login()
const vendedor=await login(cuentas[3])
await api('POST','/inventario/productos',productos[0],vendedor.token,403)
const usuariosAntes=await api('GET','/auth/usuarios',null,admin.token)
assert.equal(usuariosAntes.length,4)
// Añadir un registro genérico para verificar su conservación; no procede de la semilla nominal.
const generico=await api('POST','/auth/register',{nombre:'Administrador histórico de prueba',correo:'admin@monpoint.com',rol:'ADMIN',password:env.AUTOTACHI_VICTOR_PASSWORD},admin.token,201)
const lista=await api('GET','/inventario/productos',null,admin.token)
assert.equal(lista.length,23)
for (let i=0;i<productos.length;i++) {
 const p=lista.find(p=>p.id===primera.productos[i].id)
 for (const k of ['nombre','precio','existencia','umbralStock','descripcion'])assert.equal(p[k],productos[i][k])
}
const historico=await api('POST','/inventario/productos',{nombre:'Semilla 3.1 - Tinta',descripcion:'Registro histórico aislado',precio:41.15,existencia:5,umbralStock:5},admin.token,201)
await api('PUT','/inventario/productos/'+primera.productos[0].id,{...productos[0],existencia:11},admin.token)
peticiones.length=0
const segunda=await sembrarAutotachi({env,fetchImpl:fetchObservado})
writeFileSync(dir+'segunda.json',JSON.stringify(segunda,null,2)+'\n')
assert.deepEqual(segunda.productos.map(p=>p.id),primera.productos.map(p=>p.id))
assert.ok(segunda.productos.every(p=>p.accion==='REUTILIZADO'))
assert.equal(segunda.productos[0].existencia,11)
assert.equal(peticiones.filter(p=>p.metodo==='POST'&&p.ruta!=='/auth/login').length,0)
assert.equal((await api('GET','/inventario/productos/'+historico.id,null,admin.token)).existencia,5)
resultado.comprobaciones.push({caso:'Repetición tras ajuste de stock',reutilizados:23,altas:0,stockBalatas:11,productoHistoricoConservado:historico.id,genericoConservado:generico.id})
// Cambios deliberados en esta base desechable: un rol, una contraseña y una baja.
const alejandro=primera.usuarios[1],hugo=primera.usuarios[2]
const claveNueva=env.AUTOTACHI_VENDEDOR_PASSWORD+'X'
await api('PUT','/auth/usuarios/'+alejandro.id,{nombre:'Alejandro editado',correo:cuentas[1].correo,rol:'VENDEDOR',password:claveNueva},admin.token)
await api('PATCH','/auth/usuarios/'+hugo.id+'/desactivar',null,admin.token,204)
function snapshot() {
 return execFileSync('docker',['exec','monpoint-nominal-mongo','mongosh','--quiet','auth_nominal_prueba','--eval','JSON.stringify(db.usuarios.find().sort({correo:1}).toArray())'],{encoding:'utf8'}).trim()
}
const antes=snapshot()
execFileSync('docker',['restart','monpoint-nominal-auth'],{stdio:'pipe'})
for(let i=0;;i++) {
 try { await login();break }
 catch(e) { if(i===40)throw e;await new Promise(r=>setTimeout(r,500)) }
}
assert.equal(snapshot(),antes,'El reinicio modificó cuentas persistidas')
const accesoNuevo=await login(cuentas[1],claveNueva)
assert.equal(accesoNuevo.rol,'VENDEDOR')
await api('POST','/auth/login',{correo:cuentas[1].correo,password:env.AUTOTACHI_ALEJANDRO_PASSWORD},null,401)
await api('POST','/auth/login',{correo:cuentas[2].correo,password:env.AUTOTACHI_HUGO_PASSWORD},null,401)
const usuarios=await api('GET','/auth/usuarios',null,admin.token)
assert.ok(usuarios.some(u=>u.id===generico.id))
peticiones.length=0
await assert.rejects(sembrarAutotachi({env,fetchImpl:fetchObservado}),/Login discordante|HTTP 401/)
assert.equal(peticiones.filter(p=>p.metodo==='POST'&&p.ruta!=='/auth/login').length,0)
resultado.comprobaciones.push({caso:'Reinicio de auth',documentosSinCambios:true,claveCambiadaConservada:true,rolCambiadoConservado:true,bajaConservada:true,genericoConservado:true,importacionPosterior:'INTERRUMPIDA sin altas'})
resultado.estado='COMPLETADA'
writeFileSync(dir+'resultado-real.json',JSON.stringify(resultado,null,2)+'\n')
console.log(JSON.stringify(resultado,null,2))
