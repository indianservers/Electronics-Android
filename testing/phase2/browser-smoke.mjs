import {pathToFileURL,fileURLToPath} from 'node:url';
import {resolve,dirname} from 'node:path';
import {mkdir,writeFile} from 'node:fs/promises';
const {chromium,firefox,webkit,expect}=await import(pathToFileURL('C:/Indian Servers/Electronics Lab/node_modules/@playwright/test/index.mjs'));
const directory=resolve(dirname(fileURLToPath(import.meta.url)),'phase2-web-results');await mkdir(directory,{recursive:true});
const results=[];const url='http://127.0.0.1:6067/simulator';
for(const [name,engine,channel] of [['Chrome',chromium,'chrome'],['Edge',chromium,'msedge'],['Firefox',firefox],['WebKit',webkit]]){
 let browser;
 try{browser=await engine.launch({headless:true,...(channel?{channel}:{})});}
 catch(error){results.push({name,status:'NOT_EXECUTED',reason:error.message.split('\n')[0]});continue;}
 for(const viewport of [{width:1440,height:1000},{width:820,height:1180},{width:390,height:844}]){
  const context=await browser.newContext({viewport});const page=await context.newPage();const exceptions=[];const consoleErrors=[];
  page.on('pageerror',e=>exceptions.push(e.message));page.on('console',m=>{if(m.type()==='error')consoleErrors.push(m.text())});
  const start=performance.now();
  try{
   const response=await page.goto(url,{waitUntil:'domcontentloaded',timeout:60000});expect(response?.ok()).toBe(true);
   await expect(page.getByRole('button',{name:'Schematic View',exact:true})).toBeVisible({timeout:60000});
   await page.getByRole('button',{name:'Wire tool',exact:true}).click();await page.getByRole('button',{name:'Select tool',exact:true}).click();
   await page.reload({waitUntil:'domcontentloaded'});await expect(page.getByRole('button',{name:'Schematic View',exact:true})).toBeVisible();
   expect(exceptions).toEqual([]);expect(consoleErrors).toEqual([]);
   await page.screenshot({path:resolve(directory,`${name.toLowerCase()}-${viewport.width}.png`)});
   results.push({name,version:browser.version(),viewport,status:'PASS',elapsedMs:performance.now()-start,exceptions,consoleErrors});
  }catch(error){results.push({name,version:browser.version(),viewport,status:'FAILED_VERIFICATION',reason:error.message,exceptions,consoleErrors});await page.screenshot({path:resolve(directory,`${name.toLowerCase()}-${viewport.width}-failure.png`)}).catch(()=>{});}
  finally{await context.close();}
 }
 await browser.close();
}
await writeFile(resolve(directory,'browser-results.json'),JSON.stringify({recordedUtc:new Date().toISOString(),url,scope:'App loading, tool selection, reload and uncaught/console errors. No Android circuit execution or solver parity claim.',results},null,2));
console.log(JSON.stringify(results,null,2));if(results.some(r=>r.status==='FAILED_VERIFICATION'))process.exitCode=1;
