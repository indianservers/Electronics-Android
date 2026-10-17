"""Verify compiler records against current sources and retained binaries, without compilation."""
import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parent
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
rows=read(root/'native-compilation-results.json')
assert len(rows)==85
for r in rows:
    assert r['status']=='COMPILED_NATIVE' and not r['semanticWarnings']
    assert sha(root/r['nativeSketch'])==r['nativeSketchSha256']
    circuit=read(root/'projects'/r['project']/'circuit.json')
    source=next(f['source'] for f in circuit['firmware'] if f['boardId']==r['controller'])
    assert hashlib.sha256(source.encode()).hexdigest()==r['interpretedSourceSha256']
    assert r['outputs']
    for name,digest in r['outputs'].items():
        assert sha(root/'native-builds'/Path(r['nativeSketch']).parent.name/name)==digest
print('Verified 85 native source/sketch/binary record chains; CPU execution NOT_EXECUTED')
