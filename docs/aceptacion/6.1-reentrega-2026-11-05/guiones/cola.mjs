import { chromium } from '/tmp/monpoint-browser/node_modules/playwright/index.mjs'
const browser=await chromium.connectOverCDP('http://localhost:9223')
const page=browser.contexts()[0].pages()[0]
await page.locator('input[name=name]').fill('captura.venta-creada')
await page.locator('select[name=durable]').selectOption({label:'Transient'})
const form=page.locator('form').filter({has:page.locator('input[name=name]')})
await form.locator('input[type=submit]').click()
await page.getByRole('link',{name:'captura.venta-creada',exact:true}).click()
console.log((await page.locator('body').innerText()).slice(-6000))
console.log(JSON.stringify(await page.locator('input,select,textarea').evaluateAll(es=>es.map(e=>({tag:e.tagName,name:e.name,type:e.type,value:e.type==='submit'?e.value:undefined,options:e.tagName==='SELECT'?[...e.options].map(o=>[o.value,o.text]):undefined}))),null,2))
process.exit(0)
