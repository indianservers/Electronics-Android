"""Archive actual one-method retry; never replace primary raw failure XML."""
from pathlib import Path
from datetime import datetime,timezone
import os,subprocess,shutil,json,re,hashlib
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parent
primary=Path((r/'android-archive-path.txt').read_text(encoding='utf-8-sig').strip())
out=r/'phase2-android-results'/datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S-motion');out.mkdir(parents=True)
base=[str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'),'-s','emulator-5580'];pkg='com.indianservers.circuitssimulator'
raw=subprocess.check_output(base+['exec-out','run-as',pkg,'cat','files/instrumented-device-results.xml'])
(out/'instrumented-device-results.xml').write_bytes(raw);(out/'TEST-device-callbacks.xml').write_bytes(raw)
for name in ['android-motion-recovery.log','android-motion-recovery-start.json','android-motion-recovery-command.json']:shutil.copy2(r/name,out/name)
for name in ['app-debug.apk','app-debug-androidTest.apk','MotionSensorInstrumentedTest.kt']:shutil.copy2(primary/name,out/name)
device=ET.fromstring(raw);identity=('com.indianservers.circuitssimulator.MotionSensorInstrumentedTest','gyroPropertiesEditRealMotionAndPreserveWiringWithUndo')
assert device.get('tests')=='1' and device.find("./properties/property[@name='deviceRunComplete']").get('value')=='true'
assert {(t.get('classname'),t.get('name')) for t in device.findall('testcase')}=={identity}
if any(device.get(k)!='0' for k in ['failures','errors','skipped']):
 (out/'FAILED.txt').write_text('Actual focused retry failed; raw callbacks/log retained.\n',encoding='utf-8')
 print(out);raise SystemExit('Focused motion retry failed')
log=(out/'android-motion-recovery.log').read_text(encoding='utf-8-sig');assert re.search(r'OK \(1 tests?\)',log)
started=[];finished=[];block={}
for line in log.splitlines():
 f=re.match(r'INSTRUMENTATION_STATUS: (class|test)=(.+)',line)
 if f:block[f[1]]=f[2].strip()
 code=re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)',line)
 if code:
  assert code[1] in ['0','1'],line
  (started if code[1]=='1' else finished).append((block['class'],block['test']));block={}
assert started==finished==[identity]
start=json.loads((out/'android-motion-recovery-start.json').read_text(encoding='utf-8-sig'))
assert not start['apkInstallPerformed'] and Path(start['primaryArchive']).resolve()==primary.resolve()
remote=f'/sdcard/Android/data/{pkg}/files/motion-sensor-pins.png'
stamp=int(subprocess.check_output(base+['shell','stat','-c','%Y',remote],text=True).strip());assert stamp>=start['deviceEpoch']
images=out/'visual-qa';images.mkdir();subprocess.run(base+['pull',remote,str(images/'motion-sensor-pins.png')],check=True,capture_output=True)
assert (images/'motion-sensor-pins.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
subprocess.run(['python',str(r.parent/'engineering-validation/snapshot-installed-domain-apks.py'),'--archive',str(out),'--serial','emulator-5580'],check=True)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert all(sha(primary/n)==sha(out/n) for n in ['app-debug.apk','app-debug-androidTest.apk','MotionSensorInstrumentedTest.kt'])
(out/'run-method.json').write_text(json.dumps(dict(status='PASSED_UNCHANGED_PIXELCOPY_RECOVERY',actualMethods=1,identity=list(identity),primaryArchive=str(primary),apkInstallPerformed=False,scope='Unchanged test and APK retry. Original PixelCopy failure remains raw in primary archive; cause is not established.'),indent=2),encoding='utf-8')
(r/'motion-archive-path.txt').write_text(str(out),encoding='utf-8')
(primary/'motion-recovery.json').write_text(json.dumps(dict(archive=str(out),identity=list(identity),rawPrimaryFailureRetained=True),indent=2),encoding='utf-8')
print(out)
