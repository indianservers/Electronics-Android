"""Archive actual host/device rerun evidence, without changing the original run."""
import argparse, os, shutil, subprocess
from pathlib import Path
from datetime import datetime, timezone
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('--serial',default='emulator-5580');args=parser.parse_args()
repo=root.parent.parent
out=root/('domain-android-focused-results-'+datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S'))
out.mkdir()
source=repo/'app/build/outputs/androidTest-results/connected/debug'
files=list(source.rglob('TEST-*.xml'))
assert files
for p in files:shutil.copy2(p,out/p.name)
assert sum(int(ET.parse(p).getroot().attrib['tests']) for p in out.glob('*.xml'))==14
shutil.copy2(root/'domain-android-focused.log',out/'host-gradle.log')
for relative in ['apk/debug/app-debug.apk','apk/androidTest/debug/app-debug-androidTest.apk']:
    p=repo/'app/build/outputs'/relative;shutil.copy2(p,out/p.name)
adb=str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe')
device=subprocess.check_output([adb,'-s',args.serial,'shell','run-as','com.indianservers.circuitssimulator','cat','files/instrumented-device-results.xml'])
(out/'instrumented-device-results.xml').write_bytes(device)
subprocess.run([adb,'-s',args.serial,'pull','/sdcard/Android/data/com.indianservers.circuitssimulator/files/visual-qa',str(out/'visual-qa')],check=True)
subprocess.run(['python',str(root/'snapshot-installed-domain-apks.py'),'--archive',str(out),'--serial',args.serial],check=True)
(root/'domain-android-focused-archive-path.txt').write_text(str(out),encoding='utf-8')
print(out)
