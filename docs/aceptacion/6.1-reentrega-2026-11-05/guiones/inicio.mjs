import { chromium } from '/tmp/monpoint-browser/node_modules/playwright/index.mjs'
const browser=await chromium.connectOverCDP('http://localhost:9223')
const context=browser.contexts()[0]
const page=context.pages()[0]
await page.setViewportSize({width:1440,height:1050})
await page.goto('http://localhost:15672')
console.log(await page.locator('body').innerText())
console.log(await page.locator('input,select,button').evaluateAll(es=>es.map(e=>({tag:e.tagName,name:e.name,type:e.type,text:e.tagName==='BUTTON'?e.innerText:undefined}))))
process.exit(0)
