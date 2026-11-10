import { chromium } from '/tmp/monpoint-browser/node_modules/playwright/index.mjs'
const browser=await chromium.connectOverCDP('http://localhost:9223')
const page=browser.contexts()[0].pages()[0]
await page.locator('input[name=username]').fill(process.env.RABBITMQ_USER)
await page.locator('input[name=password]').fill(process.env.RABBITMQ_PASSWORD)
await page.locator('input[type=submit]').click()
await page.getByRole('link',{name:'Queues and Streams',exact:true}).waitFor()
await page.getByRole('link',{name:'Queues and Streams',exact:true}).click()
await page.getByText('Add a new queue',{exact:true}).click()
console.log((await page.locator('body').innerText()).slice(-6500))
console.log(await page.locator('input,select,button').evaluateAll(es=>es.map(e=>({tag:e.tagName,name:e.name,type:e.type,options:e.tagName==='SELECT'?[...e.options].map(o=>[o.value,o.text]):undefined}))))
process.exit(0)
