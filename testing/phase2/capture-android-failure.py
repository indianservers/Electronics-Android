from pathlib import Path
from datetime import datetime
import os,subprocess,shutil,sys,hashlib,json
r=Path(__file__).resolve().parent;repo=r.parent.parent
out=r/'failures'/(sys.argv[1]+'-'+datetime.now().strftime('%Y%m%d-%H%M%S'));out.mkdir(parents=True)
adb=str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe');base=[adb,'-s','emulator-5580'];pkg='com.indianservers.circuitssimulator'
for name,args in [('device-callbacks.xml',['exec-out','run-as',pkg,'cat','files/instrumented-device-results.xml']),('internal-files.tar',['exec-out','run-as',pkg,'tar','-cf','-','files']),('system-logcat.txt',['logcat','-d','-b','system']),('events-logcat.txt',['logcat','-d','-b','events']),('window-state.txt',['shell','dumpsys','window']),('current-screen.png',['exec-out','screencap','-p'])]:
 with (out/name).open('wb') as f:subprocess.run(base+args,stdout=f,stderr=subprocess.STDOUT,check=False,timeout=60)
shutil.copy2(r/'android-direct-run.log',out/'android-direct-run.log')
for p in [repo/'app/build/outputs/apk/debug/app-debug.apk',repo/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',repo/'app/src/androidTest/java/com/indianservers/circuitssimulator/PhaseTwoLifecycleInstrumentedTest.kt',repo/'app/src/main/java/com/indianservers/circuitssimulator/ui/SimulatorViewModel.kt']:
 shutil.copy2(p,out/p.name)
(out/'artifact-hashes.json').write_text(json.dumps({p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.iterdir() if p.is_file()},indent=2),encoding='utf-8')
(out/'README.txt').write_text(sys.argv[2]+'\nINTERRUPTED; not accepted as a complete passing device run.\n',encoding='utf-8')
subprocess.run(base+['shell','am','force-stop',pkg],check=True)
(r/'last-android-failure-path.txt').write_text(str(out),encoding='utf-8');print(out)
