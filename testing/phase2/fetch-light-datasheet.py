from pathlib import Path
from urllib.request import Request,urlopen
import hashlib,json
import pymupdf
root=Path(__file__).resolve().parent
attempts=[]
for url in ['https://fscdn.rohm.com/en/products/databook/datasheet/ic/sensor/light/bh1750fvi-e.pdf','https://rohmfs.rohm.com/en/products/databook/datasheet/ic/sensor/light/bh1750fvi-e.pdf','https://www.mouser.com/datasheet/2/348/bh1750fvi-e-186247.pdf','https://mm.digikey.com/Volume0/opasdata/d220001/medias/docus/6165/bh1750fvi-e.pdf']:
    try:
        data=urlopen(Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=20).read()
        assert data.startswith(b'%PDF')
        path=root/'BH1750FVI.pdf';path.write_bytes(data)
        with pymupdf.open(path) as doc:
            text='\n'.join(f'PAGE {i+1}\n'+p.get_text() for i,p in enumerate(doc))
            (root/'BH1750FVI.txt').write_text(text,encoding='utf-8')
        attempts.append(dict(url=url,status='SAVED',sha256=hashlib.sha256(data).hexdigest(),bytes=len(data)))
        break
    except Exception as e:attempts.append(dict(url=url,status='UNAVAILABLE',error=str(e)))
(root/'light-datasheet-provenance.json').write_text(json.dumps(attempts,indent=2),encoding='utf-8')
print(json.dumps(attempts,indent=2))
