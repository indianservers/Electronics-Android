"""Read actual fresh regression files and primary device callbacks; never infer completion from coverage."""
from pathlib import Path
from datetime import datetime
from collections import Counter
import json,re,sys,os,subprocess,io,tarfile,shutil,hashlib
r=Path(__file__).resolve().parent
start=(r/'phase1-regression-command.json').stat().st_mtime
counts={'legacyProjectResultsPassed':0,'legacyProjectResultsRunning':0,'legacyProjectResultsFailed':0,'freshDomainAssertionCounters':0}
gradle=(r/'phase1-regression-run.log').read_text(encoding='utf-8-sig',errors='replace')
counts['failedCasesReportedByGradle']=len(re.findall(r'^.* > .* FAILED\r?$',gradle,re.MULTILINE))
counts['diskWriteErrorsReportedByGradle']=gradle.count('There is not enough space on the disk')
binary=r.parent.parent/'app/build/test-results/testDebugUnitTest/binary/in-progress-results-generic.bin'
counts['diskWriteErrorsInLiveResultStore']=binary.read_bytes().count(b'There is not enough space on the disk') if binary.exists() else 0
counts['hostFreeGiB']=round(shutil.disk_usage(r).free/1024**3,2)
frozen=None;verification=r/'current-regression-verification.json';pointer=r/'regression-archive-path.txt'
if verification.exists() and verification.stat().st_mtime>=start and pointer.exists():
 try:
  candidate=json.loads(verification.read_text(encoding='utf-8'))
  archive=Path(candidate['archive'])
  if archive.resolve()==Path(pointer.read_text(encoding='utf-8-sig').strip()).resolve() and not (archive/'FAILED.txt').exists() and all(candidate['totals'][k]==0 for k in ['failures','errors','skipped']):frozen=candidate
 except (json.JSONDecodeError,KeyError):pass
if frozen:
 counts.update(legacyProjectResultsPassed=frozen['advancedProjectResults'],freshDomainAssertionCounters=frozen['freshDomainCounters'],
  resultsSource='Frozen independently verified completed regression; not a running JVM test',completedJunitTotals=frozen['totals'])
else:
 for p in (r.parent/'engineering-validation/projects').glob('*/result.txt'):
  if p.stat().st_mtime<start:continue
  lines=p.read_text(encoding='utf-8-sig',errors='replace').splitlines();status=lines[0] if lines else ''
  counts['legacyProjectResultsPassed' if status=='PASSED_MODELED_SCOPE' else 'legacyProjectResultsRunning' if status=='RUNNING' else 'legacyProjectResultsFailed']+=1
 for p in (r.parent/'engineering-validation/domain-projects').glob('*/assertion-count.json'):
  if p.stat().st_mtime>=start:counts['freshDomainAssertionCounters']+=1
started=[];passed=[];failed=[];block={}
direct_log=(r/'android-direct-run.log').read_text(encoding='utf-8-sig',errors='replace')
for line in direct_log.splitlines():
 f=re.match(r'INSTRUMENTATION_STATUS: (class|test)=(.+)',line)
 if f:block[f[1]]=f[2].strip()
 code=re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)',line)
 if code:
  pair=(block.get('class'),block.get('test'))
  (started if code[1]=='1' else passed if code[1]=='0' else failed).append(pair);block={}
boards=len(json.loads((r/'production-boards.json').read_text(encoding='utf-8')))
device=dict(passedNumericMethods=len(passed),failedMethods=failed,currentMethod=started[-1] if started and started[-1] not in passed else None,
 phase2ProjectExecutionsInCompletedPassingGroups=sum(c is not None and c.endswith('.PhaseTwoProjectsInstrumentedTest') for c,n in passed)*boards,
 lifecycleVisualAcceptance='Pending independently recorded real-navigation rerun')
device['directRunOutcome']='PASSED_DIRECT_RUN' if re.search(r'OK \(57 tests\)',direct_log) else 'FAILED_DIRECT_RUN' if any(s in direct_log for s in ['FAILURES!!!','Process crashed','INSTRUMENTATION_FAILED']) else 'NO_FINAL_RESULT_YET'
if '--device' in sys.argv and device['currentMethod'] and device['currentMethod'][0].endswith('.PhaseTwoProjectsInstrumentedTest'):
 adb=str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe');base=[adb,'-s','emulator-5580'];pkg='com.indianservers.circuitssimulator'
 def call(args):return subprocess.check_output(base+args,text=True,encoding='utf-8',errors='replace',timeout=20)
 bound=r/'primary-device-build/run-start-lower-bound.json'
 if not bound.exists():
  info=call(['shell','dumpsys','package',pkg+'.test']);updated=re.search(r'lastUpdateTime=(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})',info)[1]
  offset=call(['shell','date','+%z']).strip();epoch=int(datetime.strptime(updated+offset,'%Y-%m-%d %H:%M:%S%z').timestamp())
  bound.write_text(json.dumps(dict(source='Actual PackageManager lastUpdateTime for the frozen installed primary test APK; all project runs follow installation',localTime=updated,offset=offset,epoch=epoch),indent=2),encoding='utf-8')
 epoch=json.loads(bound.read_text(encoding='utf-8'))['epoch']
 method_number={'industrialEnvironment':1,'greenhouse':3,'motorCondition':5,'homeSecurity':6,'buildingAutomation':7,'medicalEnvironment':8,'waterQuality':9,'energyMonitoring':10,'gasMonitoring':12,'parking':13,'heatAndCo':15,'streetlight':16,'vehicleDiagnostics':17,'coldChain':18,'disasterWarning':19,'robotics':20}
 number=method_number[device['currentMethod'][1]];fresh=[];metadata={}
 lines=call(['shell',"run-as "+pkg+" sh -c 'stat -c \"%n|%Y|%s\" files/phase2-*-results.json'"])
 for line in lines.splitlines():
  m=re.fullmatch(r'(files/phase2-[a-z0-9_]+-'+f'{number:02d}'+r'-results\.json)\|(\d+)\|(\d+)',line)
  if m and int(m[2])>=epoch:
   fresh.append(m[1]);metadata[m[1]]=[int(m[2]),int(m[3])]
 cache_file=r/'live-completed-device-report-cache.json'
 cache_key=f"{epoch}:{(r/'android-direct-command.json').stat().st_mtime_ns}"
 try:cache=json.loads(cache_file.read_text(encoding='utf-8'))
 except (FileNotFoundError,json.JSONDecodeError):cache={}
 if cache.get('runKey')!=cache_key:cache=dict(runKey=cache_key,completed={})
 summaries={};to_read=[];cached_records=0
 for name in fresh:
  old=cache['completed'].get(name)
  if old and old['metadata']==metadata[name]:summaries[name]=old['summary'];cached_records+=1
  else:to_read.append(name)
 group={};failures=[];recorded_scenarios=0
 # Read at most two files per transfer. Completed reports can exceed 450 KiB;
 # repeatedly transferring every completed file overloaded the progress reader.
 for offset in range(0,len(to_read),2):
  try:
   capture=subprocess.run(base+['exec-out','run-as',pkg,'tar','-cf','-']+to_read[offset:offset+2],capture_output=True,timeout=20,check=True)
   with tarfile.open(fileobj=io.BytesIO(capture.stdout)) as tar:
    for member in tar:
     if not member.isfile():continue
     name=member.name.removeprefix('./');payload=tar.extractfile(member).read()
     try:row=json.loads(payload)
     except json.JSONDecodeError:continue # An actual progress rewrite can be in flight.
     summary=dict(status=row['status'],project=row['project'],error=row.get('error'),
      recordedScenarios=len(row.get('scenarios',row.get('scenarioRecords',row.get('legacyHarnessScenarioRecords',[])))))
     summaries[name]=summary
     if row['status']=='PASSED_MODELED_SCOPE':
      cache['completed'][name]=dict(metadata=metadata[name],sha256=hashlib.sha256(payload).hexdigest(),summary=summary)
  except (tarfile.TarError,subprocess.TimeoutExpired,subprocess.CalledProcessError):
   device['progressRead']='Progress transfer unavailable; only successfully read records counted; no test outcome inferred'
   break
 cache_file.write_text(json.dumps(cache),encoding='utf-8')
 for summary in summaries.values():
  status=summary['status'];group[status]=group.get(status,0)+1
  recorded_scenarios+=summary['recordedScenarios']
  if status=='FAILED_VERIFICATION':failures.append(dict(project=summary['project'],error=summary.get('error')))
 device['currentGroupFreshProjectRecords']=group;device['currentGroupFailures']=failures
 device['currentGroupRecordedScenarios']=recorded_scenarios
 device['unchangedCompletedReportsReadFromProgressCache']=cached_records
 device['progressCacheScope']='Same-run metadata-unchanged completed reports only. Acceptance archivers reread all raw files; this cache is not acceptance evidence.'
 circuit_lines=call(['shell',"run-as "+pkg+" sh -c 'stat -c \"%n|%Y\" files/phase2-*-"+f'{number:02d}'+"-circuit.json'"])
 now=int(call(['shell','date','+%s']).strip());pending=[]
 for line in circuit_lines.splitlines():
  m=re.fullmatch(r'(files/phase2-([a-z0-9_]+-'+f'{number:02d}'+r')-circuit\.json)\|(\d+)',line)
  if m and int(m[3])>=epoch and ('files/phase2-'+m[2]+'-results.json') not in fresh:
   pending.append(dict(project=m[2],secondsSinceSavedAsset=now-int(m[3]),status='Saved circuit; no fresh completed result yet'))
 device['savedCircuitsAwaitingFreshResults']=pending
if '--device' in sys.argv and device['currentMethod'] and device['currentMethod'][0].endswith('.AdvancedProjectsInstrumentedTest'):
 adb=str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe');base=[adb,'-s','emulator-5580'];pkg='com.indianservers.circuitssimulator'
 bound=r/'primary-device-build/run-start-lower-bound.json'
 stamp=subprocess.run(base+['shell','run-as',pkg,'stat','-c','%Y','files/advanced-project-results.json'],capture_output=True,text=True,timeout=20)
 if bound.exists() and stamp.returncode==0 and int(stamp.stdout.strip())>=json.loads(bound.read_text(encoding='utf-8'))['epoch']:
  payload=subprocess.check_output(base+['exec-out','run-as',pkg,'cat','files/advanced-project-results.json'],timeout=20)
  try:
   rows=json.loads(payload)
   device['earlierAggregateRegression']=dict(freshRecordedProjects=len(rows),statuses=dict(Counter(x['status'] for x in rows)),
    failures=[dict(project=x['project'],error=x.get('error','')[:300]) for x in rows if x['status']!='PASSED_MODELED_SCOPE'][:3],
    scope='Actual earlier aggregate records, separate from the 304 Phase 2 project totals')
  except json.JSONDecodeError:device['earlierAggregateRegression']='Concurrent progress rewrite; retry next snapshot'
 else:device['earlierAggregateRegression']='No fresh aggregate result file yet'
print(json.dumps(dict(currentJvmRegression=counts,primaryAndroid=device),indent=2))
