"""Retain actual targeted callbacks, exact installed APKs and real-navigation screenshots."""
from pathlib import Path
from datetime import datetime,timezone
import os,subprocess,shutil,json,re,hashlib,tarfile
import xml.etree.ElementTree as ET
from lifecycle_reading_evidence import validate_reading_checkpoints
r=Path(__file__).resolve().parent
out=r/'phase2-android-results'/datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S-lifecycle');out.mkdir(parents=True)
adb=str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe');base=[adb,'-s','emulator-5580'];pkg='com.indianservers.circuitssimulator'
raw=subprocess.check_output(base+['exec-out','run-as',pkg,'cat','files/instrumented-device-results.xml'])
(out/'instrumented-device-results.xml').write_bytes(raw);(out/'TEST-device-callbacks.xml').write_bytes(raw)
shutil.copy2(r/'android-lifecycle-rerun.log',out/'android-lifecycle-rerun.log')
for name in ['app-debug.apk','app-debug-androidTest.apk','PhaseTwoLifecycleInstrumentedTest.kt','DomainProjectsInstrumentedTest.kt','build-provenance.json']:shutil.copy2(r/'lifecycle-device-build'/name,out/name)
shutil.copy2(r/'android-supplemental-run-start.json',out/'android-supplemental-run-start.json')
device=ET.fromstring(raw)
assert device.get('tests')=='11' and all(device.get(k)=='0' for k in ['failures','errors','skipped'])
assert device.find("./properties/property[@name='deviceRunComplete']").get('value')=='true'
identity=('com.indianservers.circuitssimulator.PhaseTwoLifecycleInstrumentedTest','tenSensorFirmwareSurvivesRotationRecreationAndBackground')
expected={identity}|{('com.indianservers.circuitssimulator.DomainProjectsInstrumentedTest',n) for n in ['airQuality','waterTreatment','heatAndCoDetection','solarAndBattery','robotics','vehicleCan','chemicalProcess','aquaculture','predictiveMaintenance','disasterDetection']}
assert {(t.get('classname'),t.get('name')) for t in device.findall('testcase')}==expected
log=(out/'android-lifecycle-rerun.log').read_text(encoding='utf-8-sig')
assert re.search(r'OK \(11 tests\)',log)
started=[];finished=[];block={}
for line in log.splitlines():
 f=re.match(r'INSTRUMENTATION_STATUS: (class|test)=(.+)',line)
 if f:block[f[1]]=f[2].strip()
 code=re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)',line)
 if code:
  assert code[1] in ['0','1'],line
  (started if code[1]=='1' else finished).append((block['class'],block['test']));block={}
assert started==finished and len(started)==len(set(started))==11 and set(started)==expected
data=subprocess.check_output(base+['exec-out','run-as',pkg,'cat','files/phase2-lifecycle-results.json'])
(out/'phase2-lifecycle-results.json').write_bytes(data);result=json.loads(data)
assert result['status']=='PASSED_MODELED_SCOPE' and all(result[k] for k in ['realAppNavigation','workspaceVisibleAfterEveryTransition','foregroundWindowVerified'])
reading_evidence=validate_reading_checkpoints(result)
(out/'lifecycle-reading-evidence.json').write_text(json.dumps(reading_evidence,indent=2),encoding='utf-8')
shutil.copy2(r/'lifecycle_reading_evidence.py',out/'lifecycle_reading_evidence.py')
domain_raw=subprocess.check_output(base+['exec-out','run-as',pkg,'cat','files/domain-project-results.json'])
(out/'domain-project-results.json').write_bytes(domain_raw);domain=json.loads(domain_raw)
boards=json.loads((r/'production-boards.json').read_text(encoding='utf-8'))
domain_ids={f"{b['id'].lower()}-{n:02d}" for b in boards for n in [7,8,9,10,12,13,16,18,19,20]}
assert len(domain)==len(domain_ids) and {d['project'] for d in domain}==domain_ids
assert all(d['status']=='PASSED_DOMAIN_MONITORING' and d['sensorCount']==10 and d['assertions']>0 for d in domain)
names=['files/domain-project-results.json']+[f'files/domain-project-{p}.json' for p in sorted(domain_ids)]
with (out/'domain-internal-files.tar').open('wb') as f:subprocess.run(base+['exec-out','run-as',pkg,'tar','-cf','-']+names,check=True,stdout=f)
captured=set();wanted={f'files/domain-project-{p}.json':p for p in domain_ids}
with tarfile.open(out/'domain-internal-files.tar') as tar:
 for member in tar:
  name=member.name.removeprefix('./')
  if member.isfile() and name in wanted:
   project=wanted[name];dest=out/'domain-circuits'/project;dest.mkdir(parents=True)
   (dest/'circuit.json').write_bytes(tar.extractfile(member).read());captured.add(project)
assert captured==domain_ids
(out/'legacy-domain-summary.json').write_text(json.dumps(dict(namespace='PHASE_1_DOMAIN',projects=len(domain),scenarios=sum(len(d['scenarios']) for d in domain),recordedSharedValidatorAssertions=sum(d['assertions'] for d in domain),scope='Additional legacy regression runs; not added to the 304 Phase 2 project/scenario/assertion totals'),indent=2),encoding='utf-8')
images=out/'visual-qa';images.mkdir()
for name in ['portrait','landscape','recreated','portrait-restored']:
 filename='phase2-ten-sensor-'+name+'.png'
 subprocess.run(base+['pull',f'/sdcard/Android/data/{pkg}/files/visual-qa/'+filename,str(images/filename)],check=True,capture_output=True)
subprocess.run(['python',str(r.parent/'engineering-validation/snapshot-installed-domain-apks.py'),'--archive',str(out),'--serial','emulator-5580'],check=True)
primary=Path((r/'android-archive-path.txt').read_text(encoding='utf-8-sig').strip())
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert digest(primary/'app-debug.apk')==digest(out/'app-debug.apk'),'Production APK changed'
(out/'run-method.json').write_text(json.dumps(dict(method='DIRECT_ANDROID_JUNIT_RUNNER_SUPPLEMENTAL',actualMethods=11,legacyDomainProjects=len(domain),supersedesOnly=list(identity),productionApkMatchesPrimary=True,visualReview='AWAITING_MANUAL_VISUAL_REVIEW',primaryArchive=str(primary)),indent=2),encoding='utf-8')
(r/'lifecycle-rerun-archive-path.txt').write_text(str(out),encoding='utf-8');print(out)
