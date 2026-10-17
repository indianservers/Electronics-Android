"""Read actual supplemental callbacks and fresh legacy-domain results only."""
from pathlib import Path
from collections import Counter
import os,json,re,subprocess
from lifecycle_reading_evidence import validate_reading_checkpoints
r=Path(__file__).resolve().parent
log=r/'android-lifecycle-rerun.log';bound=r/'android-supplemental-run-start.json'
if not log.exists() or not bound.exists():
 print(json.dumps(dict(status='SUPPLEMENTAL_NOT_STARTED')));raise SystemExit()
console=log.read_text(encoding='utf-8-sig',errors='replace')
started=[];passed=[];failed=[];block={}
for line in console.splitlines():
 f=re.match(r'INSTRUMENTATION_STATUS: (class|test)=(.+)',line)
 if f:block[f[1]]=f[2].strip()
 code=re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)',line)
 if code:
  pair=(block.get('class'),block.get('test'))
  (started if code[1]=='1' else passed if code[1]=='0' else failed).append(pair);block={}
out=dict(passedNumericMethods=len(passed),failedMethods=failed,
 currentMethod=started[-1] if started and started[-1] not in passed else None,
 directRunOutcome='PASSED_DIRECT_RUN' if 'OK (11 tests)' in console else 'FAILED_DIRECT_RUN' if any(s in console for s in ['FAILURES!!!','Process crashed','INSTRUMENTATION_FAILED']) else 'NO_FINAL_RESULT_YET',
 lifecycleVisualAcceptance='Requires manual review of actual four screenshots and final archive hashes')
epoch=json.loads(bound.read_text(encoding='utf-8-sig'))['deviceEpoch']
base=[str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'),'-s','emulator-5580'];pkg='com.indianservers.circuitssimulator'
def fresh_payload(name):
 stamp=subprocess.run(base+['shell','run-as',pkg,'stat','-c','%Y',name],capture_output=True,text=True,timeout=20)
 if stamp.returncode!=0 or int(stamp.stdout.strip())<epoch:return None
 return subprocess.check_output(base+['exec-out','run-as',pkg,'cat',name],timeout=20)
payload=fresh_payload('files/domain-project-results.json')
if payload:
 try:
  rows=json.loads(payload)
  out['legacyDomainRegression']=dict(recordedProjects=len(rows),statuses=dict(Counter(x['status'] for x in rows)),
   failures=[dict(project=x['project'],error=x.get('error','')[:300]) for x in rows if x['status']!='PASSED_DOMAIN_MONITORING'][:3],
   scope='Additional legacy-domain executions; not added to the 304 Phase 2 project totals')
 except json.JSONDecodeError:out['legacyDomainRegression']='Concurrent progress rewrite; retry next snapshot'
else:out['legacyDomainRegression']='No fresh legacy-domain report yet'
life=('com.indianservers.circuitssimulator.PhaseTwoLifecycleInstrumentedTest','tenSensorFirmwareSurvivesRotationRecreationAndBackground')
if life in passed:
 payload=fresh_payload('files/phase2-lifecycle-results.json')
 if payload:out['lifecycleNumericReadingEvidence']=validate_reading_checkpoints(json.loads(payload))
out['hostFreeGiB']=round(__import__('shutil').disk_usage(r).free/1024**3,2)
print(json.dumps(out,indent=2))
