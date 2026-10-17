"""Independent raw-record integrity review; does not execute or invent tests."""
from pathlib import Path
import json,hashlib,zipfile,tarfile,sys,runpy
r=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
pointer=lambda name:Path((r/name).read_text(encoding='utf-8-sig').strip())
a=pointer('android-archive-path.txt');manifest=read(r/'phase2-asset-manifest.json');expected={x['project']:x for x in manifest}
files=list((a/'project-results').glob('*.json'));assert len(files)==len(expected)
assertions=0;scenarios=0
for p in files:
 v=read(p);x=expected[v['project']]
 assert v['status']=='PASSED_MODELED_SCOPE' and v['circuitAssetSha256']==x['sha256'] and v['assertions']>0
 assertions+=v['assertions'];scenarios+=len(v.get('scenarios',v.get('scenarioRecords',v.get('legacyHarnessScenarioRecords',[]))))
for zpath in [a/'app-debug-androidTest.apk',r/'lifecycle-device-build/app-debug-androidTest.apk']:
 with zipfile.ZipFile(zpath) as z:
  for x in manifest:assert hashlib.sha256(z.read('assets/phase2-projects/'+x['project']+'/circuit.json')).hexdigest()==x['sha256']
with tarfile.open(a/'internal-files.tar') as t:
 member=next(v for v in t if v.name.removeprefix('./')=='files/advanced-project-results.json')
 raw=t.extractfile(member).read();old=json.loads(raw)
 assert len(old)==380 and len({v['project'] for v in old})==380 and all(v['status']=='PASSED_MODELED_SCOPE' for v in old)
 (a/'earlier-380-project-regression.json').write_bytes(raw)
sys.path.insert(0,str(r));h=runpy.run_path(str(r/'phase2-evidence.py'),run_name='evidence_helpers')
latest=h['case_map'](pointer('initial-jvm-archive-path.txt'));latest.update(h['case_map'](pointer('affected-jvm-archive-path.txt')))
cases=[v for (c,n),v in latest.items() if c.endswith('.PhaseTwoProjectsTest')]
assert len(cases)==len(expected) and all(v.find('failure') is None and v.find('error') is None and v.find('skipped') is None for v in cases)
result=dict(primaryArchive=str(a),phase2AndroidProjects=len(files),latestJvmUniqueProjectTests=len(cases),
 phase2AndroidScenarios=scenarios,phase2RecordedAndroidAssertions=assertions,assetHashComparisons=len(manifest)*2,
 earlierAndroidRegressionProjects=len(old),earlierAndroidRegressionScenarios=sum(v['scenarios'] for v in old),
 scope='Actual raw records/APK assets checked before packaging; earlier380 runs do not inflate304project totals. Finalizer independently verifies again after supplemental completion.')
(r/'primary-project-integrity-review.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
