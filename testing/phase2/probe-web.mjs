import { readFile,writeFile,mkdir,readdir } from 'node:fs/promises';
import { resolve,dirname } from 'node:path';
import { fileURLToPath,pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';
const here=dirname(fileURLToPath(import.meta.url));
const web='C:/Indian Servers/Electronics Lab';
const output=resolve(here,'phase2-web-results');await mkdir(output,{recursive:true});
const {build}=await import(pathToFileURL(resolve(web,'node_modules/esbuild/lib/main.js')));
const bundle=await build({stdin:{contents:`export {parseCircuitProject} from './src/utils/validation'; export {componentRegistry} from './src/data/components/componentRegistry'; export {boardProfiles} from './src/data/components/boardProfiles';`,resolveDir:web,loader:'ts'},bundle:true,write:false,platform:'node',format:'esm',metafile:true});
const runtime=await import(`data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString('base64')}`);
const sources={};for(const input of Object.keys(bundle.metafile.inputs)){if(!input.startsWith('<')){const path=resolve(web,input);try{sources[path]=createHash('sha256').update(await readFile(path)).digest('hex')}catch{}}}
await writeFile(resolve(output,'web-registry.json'),JSON.stringify({boards:Object.keys(runtime.boardProfiles),components:runtime.componentRegistry.map(c=>({id:c.id,name:c.name,simulationModel:c.simulationModel,pins:c.pins}))},null,2));
const rows=[];
for(const folder of await readdir(resolve(here,'projects'))){
 const directory=resolve(here,'projects',folder);const request=JSON.parse(await readFile(resolve(directory,'design-request.json'),'utf8'));
 const row={project:folder,board:request.board,applicationNumber:request.applicationNumber,application:request.application};
 if(!request.hasExecutableDesign){rows.push({...row,status:'NOT_EXECUTED',reason:'Android application design has unresolved core-function blockers; no substitute web execution claimed.'});continue;}
 const raw=await readFile(resolve(directory,'circuit.json'));row.androidCircuitSha256=createHash('sha256').update(raw).digest('hex');
 try{runtime.parseCircuitProject(JSON.parse(raw));rows.push({...row,status:'IMPORTED_NOT_EXECUTED'});}
 catch(error){rows.push({...row,status:'BLOCKED_UNSUPPORTED_HARDWARE',stage:'PRODUCTION_JSON_IMPORT',reason:String(error.message),engineExecuted:false});}
}
await writeFile(resolve(output,'same-project-import-results.json'),JSON.stringify({recordedUtc:new Date().toISOString(),webRepository:web,sourceHashes:sources,scope:'Actual production importer and registry, not browser or solver parity',records:rows},null,2));
console.log(JSON.stringify({records:rows.length,counts:rows.reduce((a,r)=>(a[r.status]=(a[r.status]??0)+1,a),{}),webBoards:Object.keys(runtime.boardProfiles).length},null,2));
