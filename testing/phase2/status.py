from pathlib import Path
from collections import Counter
import json
root=Path(__file__).resolve().parent
records=[];writing=[]
for p in (root/'projects').glob('*/jvm-results.json'):
    try:records.append(json.loads(p.read_text(encoding='utf-8')))
    except (json.JSONDecodeError,OSError):writing.append(p.parent.name)
print('JVM project records:',dict(Counter(r['status'] for r in records)),'being written:',writing)
for r in records:
    if r['status']=='FAILED_VERIFICATION':print('FAILED',r['board'],r['number'],r.get('error','').splitlines()[0])
for name in ['native-compilation-results.json','phase2-web-results/web-unit-results.json']:
    p=root/name
    if p.exists():
        try:
            data=json.loads(p.read_text(encoding='utf-8'))
            print(name,dict(Counter(r['status'] for r in data)) if isinstance(data,list) else {k:data.get(k) for k in ['success','numTotalTests','numPassedTests','numFailedTests']})
        except json.JSONDecodeError:print(name,'being written')
