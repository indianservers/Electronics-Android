"""Compile explicitly adapted native AVR sketches; never label interpreter code as native."""
from pathlib import Path
import argparse,hashlib,json,re,subprocess,time
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('--resume',action='store_true');args=parser.parse_args()
cli=root/'toolchains/arduino-cli.exe';config=root/'toolchains/arduino-cli.yaml'
targets={'ARDUINO_UNO':'arduino:avr:uno','ARDUINO_NANO':'arduino:avr:nano:cpu=atmega328','ARDUINO_MEGA':'arduino:avr:mega:cpu=atmega2560',
         'ARDUINO_LEONARDO':'arduino:avr:leonardo','ARDUINO_MICRO':'arduino:avr:micro'}
def digest(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def adapt(source):
    # The interpreter explicitly persists setup variables across loop calls. Native C++
    # needs file-scope state. Preserve initialization at its original setup position.
    # Subset integers are mathematically wide; AVR int is 16 bits. Keep byte-word
    # assembly unsigned-positive until the explicit two's-complement conversion.
    source=re.sub(r'\bint\b','int32_t',source)
    match=re.search(r'void\s+setup\s*\(\s*\)\s*\{',source);assert match
    begin=match.end();depth=1;end=begin;quote=None;escaped=False
    while end<len(source) and depth:
        ch=source[end]
        if quote:
            if escaped:escaped=False
            elif ch=='\\':escaped=True
            elif ch==quote:quote=None
        elif ch in ['"',"'"]:quote=ch
        elif ch=='{':depth+=1
        elif ch=='}':depth-=1
        end+=1
    body=source[begin:end-1];depth=0;at_depth=[];quote=None;escaped=False
    for ch in body:
        at_depth.append(depth)
        if quote:
            if escaped:escaped=False
            elif ch=='\\':escaped=True
            elif ch==quote:quote=None
        elif ch in ['"',"'"]:quote=ch
        elif ch=='{':depth+=1
        elif ch=='}':depth-=1
    declarations=[];edits=[]
    for m in re.finditer(r'\b(int32_t|int|double|float|long)\s+([A-Za-z_]\w*)\s*=\s*([^;]+);',body):
        if at_depth[m.start()]!=0:continue
        declarations.append(f'{m[1]} {m[2]};');edits.append((m.start(),m.end(),f'{m[2]}={m[3]};'))
    for start,stop,replacement in reversed(edits):body=body[:start]+replacement+body[stop:]
    converted=source[:begin]+body+source[end-1:]
    converted=re.sub(r'SPI\.beginTransaction\((\d+),\s*([0-3])\)',r'SPI.beginTransaction(SPISettings(\1,MSBFIRST,SPI_MODE\2))',converted)
    aliases='\n'.join(f'#ifndef D{i}\n#define D{i} {i}\n#endif' for i in range(70))
    return '// Separate native AVR adaptation: persistent state, int32_t arithmetic, header aliases and SPISettings API.\n#include <Wire.h>\n#include <SPI.h>\n#include <math.h>\n#include <stdint.h>\nstatic_assert(sizeof(int)==2, "Expected AVR int ABI");\nstatic_assert(sizeof(int32_t)==4, "Register arithmetic must use 32-bit state");\n'+aliases+'\n'+'\n'.join(declarations)+'\n'+converted
rows=[];previous={}
if args.resume and (root/'native-compilation-results.json').exists():
    previous={(r['project'],r.get('controller')):r for r in json.loads((root/'native-compilation-results.json').read_text(encoding='utf-8'))}
for folder in sorted((root/'projects').iterdir()):
    request=json.loads((folder/'design-request.json').read_text(encoding='utf-8'))
    if not request['hasExecutableDesign'] or request['board'] not in targets:continue
    for attempt in range(3):
        try:circuit=json.loads((folder/'circuit.json').read_text(encoding='utf-8'));break
        except json.JSONDecodeError:
            if attempt==2:raise
            time.sleep(.1)
    for attachment in circuit['firmware']:
        board_id=attachment.get('boardId')
        part=next(p for p in circuit['components'] if p['id']==board_id)
        if part['kind'] not in targets:
            rows.append(dict(project=folder.name,board=part['kind'],status='NOT_EXECUTED',reason='Target SDK not provisioned'));continue
        name='Phase2_'+folder.name.replace('-','_')+'_'+board_id
        sketch=root/'native-sketches'/name;sketch.mkdir(parents=True,exist_ok=True)
        native=sketch/(name+'.ino');payload=adapt(attachment['source'])
        old=previous.get((folder.name,board_id))
        output=root/'native-builds'/name
        source_hash=hashlib.sha256(attachment['source'].encode()).hexdigest()
        payload_hash=hashlib.sha256(payload.encode()).hexdigest()
        if old and old['status']=='COMPILED_NATIVE' and old.get('registerIntegerType')=='int32_t' and old['fqbn']==targets[part['kind']] and old['interpretedSourceSha256']==source_hash and old['nativeSketchSha256']==payload_hash and native.exists() and digest(native)==payload_hash and old.get('outputs') and all((output/n).exists() and digest(output/n)==h for n,h in old['outputs'].items()):
            rows.append(old);print(folder.name,board_id,'REUSED_VERIFIED_COMPILATION',flush=True);continue
        native.write_text(payload,encoding='utf-8')
        output=root/'native-builds'/name;output.mkdir(parents=True,exist_ok=True)
        build_dir=root/'native-work'/part['kind'];build_dir.mkdir(parents=True,exist_ok=True)
        command=[str(cli),'--config-file',str(config),'compile','--fqbn',targets[part['kind']],'--format','json','--warnings','all',
            '--jobs','1','--build-path',str(build_dir),'--output-dir',str(output),str(sketch)]
        start=time.monotonic()
        run=subprocess.run(command,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
        (output/'compiler-stdout.json').write_text(run.stdout,encoding='utf-8');(output/'compiler-stderr.txt').write_text(run.stderr,encoding='utf-8')
        try:compiler=json.loads(run.stdout)
        except json.JSONDecodeError:compiler={}
        warnings=[line.strip() for line in compiler.get('compiler_err','').splitlines() if 'warning:' in line]
        semantic=[w for w in warnings if any(flag in w for flag in ['-Wtype-limits','-Woverflow','-Wshift-count','-Wconversion'])]
        status='FAILED_NATIVE_COMPILATION' if run.returncode else 'NATIVE_SEMANTIC_WARNING' if semantic else 'COMPILED_NATIVE'
        row=dict(project=folder.name,board=part['kind'],controller=board_id,fqbn=targets[part['kind']],status=status,
            exitCode=run.returncode,elapsedSeconds=time.monotonic()-start,nativeSketch=str(native.relative_to(root)),nativeSketchSha256=digest(native),
            interpretedSourceSha256=hashlib.sha256(attachment['source'].encode()).hexdigest(),cpuExecution='NOT_EXECUTED',
            registerIntegerType='int32_t',floatingPointParity='NOT_EXECUTED',warnings=warnings,semanticWarnings=semantic,
            outputs={p.name:digest(p) for p in output.iterdir() if p.suffix in ['.hex','.elf','.bin']},command=command)
        rows.append(row);(root/'native-compilation-results.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
        print(folder.name,board_id,row['status'],flush=True)
print('Compiled native targets:',sum(r['status']=='COMPILED_NATIVE' for r in rows),'failed:',sum(r['status']=='FAILED_NATIVE_COMPILATION' for r in rows))
