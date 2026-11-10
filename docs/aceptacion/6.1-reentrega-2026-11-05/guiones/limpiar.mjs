import assert from 'node:assert/strict'
import { chromium } from '/tmp/monpoint-browser/node_modules/playwright/index.mjs'
import { readFileSync,writeFileSync } from 'node:fs'
const out='/workspace/.monpoint/reentrega-2026-11-05',s=JSON.parse(readFileSync(out+'/estado.json'))
const b=await chromium.connectOverCDP('http://localhost:9223'),p=b.contexts()[0].pages()[0]
p.on('dialog',async d=>await d.accept())
s.limpieza=[]
for(const cola of s.colas){
 await p.goto('http://localhost:15672/#/queues/%2F/'+cola)
 await p.getByRole('heading',{name:'Queue '+cola,exact:true}).waitFor()
 if(!await p.locator('input[type=submit][value="Delete Queue"]').isVisible()) await p.getByRole('heading',{name:'Delete',exact:true}).click()
 const espera=p.waitForResponse(r=>r.request().method()==='DELETE' && r.url().includes('/'+cola))
 await p.locator('input[type=submit][value="Delete Queue"]').click()
 const r=await espera;assert.equal(r.status(),204)
 s.limpieza.push({cola,status:r.status(),fecha:new Date().toISOString()})
 writeFileSync(out+'/estado.json',JSON.stringify(s,null,2))
}
await p.goto('http://localhost:15672/#/queues')
await p.getByRole('heading',{name:'Queues',exact:true}).waitFor()
for(const cola of s.colas) assert.equal(await p.getByRole('link',{name:cola,exact:true}).count(),0)
await p.evaluate(()=>window.scrollTo(0,0))
await p.screenshot({path:out+'/11-colas-temporales-eliminadas.png',fullPage:true})
s.fin=new Date().toISOString()
writeFileSync(out+'/estado.json',JSON.stringify(s,null,2))
console.log(JSON.stringify(s.limpieza))
process.exit(0)
