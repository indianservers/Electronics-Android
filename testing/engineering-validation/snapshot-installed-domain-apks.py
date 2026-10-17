"""Read the actual installed binaries without stopping the running instrumentation."""
from pathlib import Path
from datetime import datetime, timezone
import argparse, hashlib, json, os, subprocess

root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('--archive');parser.add_argument('--serial',default='emulator-5556');args=parser.parse_args()
archive=Path(args.archive) if args.archive else Path((root/'domain-android-direct-archive-path.txt').read_text().strip())
adb=str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe')
rows=[]
for package,build_name,installed_name in [
    ('com.indianservers.circuitssimulator.test','app-debug-androidTest.apk','installed-test.apk'),
    ('com.indianservers.circuitssimulator','app-debug.apk','installed-app.apk')]:
    paths=subprocess.check_output([adb,'-s',args.serial,'shell','pm','path',package],text=True).strip().splitlines()
    assert len(paths)==1 and paths[0].startswith('package:'),paths
    remote=paths[0][len('package:'):]
    target=archive/(installed_name+'.capture')
    subprocess.run([adb,'-s',args.serial,'pull',remote,str(target)],check=True,capture_output=True)
    installed=hashlib.sha256(target.read_bytes()).hexdigest()
    built=hashlib.sha256((archive/build_name).read_bytes()).hexdigest()
    assert installed==built, f'Installed {package} differs from archived build'
    target.replace(archive/installed_name)
    rows.append(dict(package=package,remotePath=remote,file=installed_name,archivedBuild=build_name,
        sha256=installed,matchesArchivedBuild=True,capturedUtc=datetime.now(timezone.utc).isoformat()))
(archive/'device-installed-apk-hashes.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
print(json.dumps(rows,indent=2))
