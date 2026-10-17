"""Archive actual direct Android instrumentation callbacks, logs, APKs and project files."""
from pathlib import Path
from datetime import datetime,timezone
import argparse,hashlib,json,os,re,shutil,subprocess,tarfile
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent;repo=root.parent.parent
parser=argparse.ArgumentParser();parser.add_argument('--serial',default='emulator-5580');parser.add_argument('--allow-known-pixelcopy-failure',action='store_true');args=parser.parse_args()
out=root/'phase2-android-results'/datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S');out.mkdir(parents=True)
adb=str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe');package='com.indianservers.circuitssimulator'
def run(command,**kwargs):return subprocess.run([adb,'-s',args.serial]+command,check=True,**kwargs)
xml=subprocess.check_output([adb,'-s',args.serial,'exec-out','run-as',package,'cat','files/instrumented-device-results.xml'])
device=ET.fromstring(xml)
assert device.find("./properties/property[@name='deviceRunComplete']").attrib['value']=='true'
motion_identity=('com.indianservers.circuitssimulator.MotionSensorInstrumentedTest','gyroPropertiesEditRealMotionAndPreserveWiringWithUndo')
assert int(device.attrib['tests'])==57 and all(int(device.attrib[k])==0 for k in ['errors','skipped'])
failed_cases=[t for t in device.findall('testcase') if t.find('failure') is not None]
known_failure=bool(args.allow_known_pixelcopy_failure)
if known_failure:
    assert int(device.attrib['failures'])==len(failed_cases)==1
    failure=failed_cases[0]
    assert (failure.get('classname'),failure.get('name'))==motion_identity and 'Failed waiting for PixelCopy!' in ''.join(failure.find('failure').itertext())
    (out/'FAILED.txt').write_text('Primary run failed one PixelCopy capture test. Raw failure retained; a separate actual passing recovery is mandatory.\n',encoding='utf-8')
else:assert int(device.attrib['failures'])==0 and not failed_cases
(out/'instrumented-device-results.xml').write_bytes(xml)
# Exact raw byte copy of the on-device JUnit callbacks, not synthesized host XML.
(out/'TEST-device-callbacks.xml').write_bytes(xml)
shutil.copy2(root/'android-direct-run.log',out/'android-direct-run.log')
log=(out/'android-direct-run.log').read_text(encoding='utf-8-sig')
if known_failure:assert 'FAILURES!!!' in log and re.search(r'Tests run:\s*57,\s*Failures:\s*1',log)
else:assert re.search(r'OK \('+device.attrib['tests']+r' tests?\)',log), 'Direct runner did not report successful completion'
started=[];finished=[];block={}
for line in log.splitlines():
    field=re.match(r'INSTRUMENTATION_STATUS: (class|test)=(.+)',line)
    if field:block[field[1]]=field[2].strip()
    code=re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)',line)
    if code:
        if code[1] in ['0','1'] or (known_failure and code[1]=='-2' and (block.get('class'),block.get('test'))==motion_identity):
            assert 'class' in block and 'test' in block
            (started if code[1]=='1' else finished).append((block['class'],block['test']))
        else:raise AssertionError('Nonpassing direct runner status '+line)
        block={}
identities={(t.attrib['classname'],t.attrib['name']) for t in device.findall('testcase')}
assert len(started)==len(finished)==len(identities)==int(device.attrib['tests']) and set(started)==set(finished)==identities
with (out/'internal-files.tar').open('wb') as stream:run(['exec-out','run-as',package,'tar','-cf','-','files'],stdout=stream)
manifest=json.loads((root/'phase2-asset-manifest.json').read_text(encoding='utf-8'))
expected={f"phase2-{r['project']}-results.json" for r in manifest};captured=set()
dest=out/'project-results';dest.mkdir()
with tarfile.open(out/'internal-files.tar') as tar:
    for member in tar:
        name=Path(member.name).name
        if member.isfile() and name in expected:
            data=tar.extractfile(member).read();row=json.loads(data)
            assert row['status']=='PASSED_MODELED_SCOPE',(name,row['status'])
            (dest/name).write_bytes(data);captured.add(name)
        elif member.isfile() and name=='phase2-lifecycle-results.json':(out/name).write_bytes(tar.extractfile(member).read())
assert captured==expected,('Missing direct project records',expected-captured)
for name in ['app-debug.apk','app-debug-androidTest.apk']:
    shutil.copy2(root/'primary-device-build'/name,out/name)
shutil.copy2(repo/'app/src/androidTest/java/com/indianservers/circuitssimulator/MotionSensorInstrumentedTest.kt',out/'MotionSensorInstrumentedTest.kt')
shutil.copy2(root/'primary-device-build/device-profile.json',out/'device-profile.json')
shutil.copy2(root/'runtime-memory-profile.json',out/'runtime-memory-profile.json')
for name in ['android-aot-attempt.json','android-aot-app.log','android-aot-test.log','android-aot-dexopt-state.txt']:
    if (root/name).exists():shutil.copy2(root/name,out/name)
if (root/'lifecycle-visual-review.json').exists():shutil.copy2(root/'lifecycle-visual-review.json',out/'lifecycle-visual-review.json')
run(['pull',f'/sdcard/Android/data/{package}/files/visual-qa',str(out/'visual-qa')],capture_output=True)
subprocess.run(['python',str(root.parent/'engineering-validation/snapshot-installed-domain-apks.py'),'--archive',str(out),'--serial',args.serial],check=True)
(out/'run-method.json').write_text(json.dumps(dict(method='DIRECT_ANDROID_JUNIT_RUNNER',hostUtp='NOT_USED',
    status='COMPLETED_WITH_RECORDED_PIXELCOPY_FAILURE' if known_failure else 'PASSED_DIRECT_RUN',
    recoveryRequiredFor=list(motion_identity) if known_failure else None,
    callbackXml='Actual listener bytes; TEST-device-callbacks.xml is an identical copy, not an independent second run',
    independentlyCheckedConsoleIdentities=len(identities),projectFiles=len(captured),serial=args.serial,
    callbackSha256=hashlib.sha256(xml).hexdigest()),indent=2),encoding='utf-8')
(root/'android-archive-path.txt').write_text(str(out),encoding='utf-8');print(out)
