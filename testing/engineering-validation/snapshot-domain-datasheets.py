"""Retain public manufacturer PDFs and download provenance, without inferring revision matches."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from urllib.request import Request, urlopen
from datetime import datetime, timezone
import hashlib, json, re
import pymupdf

root=Path(__file__).resolve().parent
sources=json.loads((root/'domain-datasheet-audit.json').read_text(encoding='utf-8'))['sources']
dest=root/'datasheets';dest.mkdir(exist_ok=True)
existing={'EZO_PH':'pH_EZO_Datasheet.pdf','EZO_EC':'EC_EZO_Datasheet.pdf','EZO_DO':'DO_EZO_Datasheet.pdf'}
jobs=[(kind,source['url'],source['revision']) for kind,source in sources.items()]
jobs.append(('MCP2562FD',sources['MCP2515_CAN']['transceiverUrl'],'DS20005284A 2014'))
def fetch(job):
    kind,url,expected=job
    row=dict(kind=kind,url=url,modelReferenceRevision=expected,capturedUtc=datetime.now(timezone.utc).isoformat())
    try:
        path=dest/existing.get(kind,kind+'.pdf')
        if not path.exists():
            with urlopen(Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=30) as response:data=response.read()
            if not data.startswith(b'%PDF'):raise ValueError('Response is not a PDF')
            path.write_bytes(data)
        data=path.read_bytes()
        with pymupdf.open(path) as doc:
            identifiers=re.findall(r'Version[:\s]+\d+\.\d+|SBOS\d+[A-Z]?|SNIS\d+[A-Z]?|DS\d+[A-Z]?|V\s*\d+\.\d+',doc[0].get_text())
            row.update(status='SAVED',file=str(path.relative_to(root)),sha256=hashlib.sha256(data).hexdigest(),bytes=len(data),pages=len(doc),firstPageRevisionIdentifiers=list(dict.fromkeys(identifiers)))
    except Exception as e:row.update(status='DOWNLOAD_UNAVAILABLE',error=str(e))
    return row
with ThreadPoolExecutor(max_workers=4) as workers:rows=list(workers.map(fetch,jobs))
(root/'datasheet-download-manifest.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
for row in rows:print(row['kind'],row['status'],row.get('error',''))
