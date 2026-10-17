"""Provision a repository-local official AVR compiler; never upload to a board."""
from pathlib import Path
from urllib.request import Request,urlopen
import hashlib,json,subprocess,zipfile
root=Path(__file__).resolve().parent
dest=root/'toolchains';dest.mkdir(exist_ok=True)
def fetch(url):
    with urlopen(Request(url,headers={'User-Agent':'CircuitsSimulator-Phase2'}),timeout=60) as r:return r.read()
release=json.loads(fetch('https://api.github.com/repos/arduino/arduino-cli/releases/latest'))
asset=next(a for a in release['assets'] if a['name'].endswith('Windows_64bit.zip'))
checks=next(a for a in release['assets'] if a['name'].endswith('checksums.txt'))
checksum_text=fetch(checks['browser_download_url']).decode()
expected=next(line.split()[0] for line in checksum_text.splitlines() if line.split()[-1].lstrip('*')==asset['name'])
archive=dest/asset['name']
if not archive.exists():archive.write_bytes(fetch(asset['browser_download_url']))
assert hashlib.sha256(archive.read_bytes()).hexdigest()==expected
with zipfile.ZipFile(archive) as z:
    for name in z.namelist():
        if name=='arduino-cli.exe':z.extract(name,dest)
config=dest/'arduino-cli.yaml'
config.write_text('directories:\n'+''.join(f"  {k}: '{(dest/v).as_posix()}'\n" for k,v in [('data','data'),('downloads','downloads'),('user','user')]),encoding='utf-8')
proof=dict(release=release['tag_name'],url=asset['browser_download_url'],sha256=expected,checksumUrl=checks['browser_download_url'],scope='Native AVR compilation only; no upload/CPU execution')
(dest/'provenance.json').write_text(json.dumps(proof,indent=2))
cli=[str(dest/'arduino-cli.exe'),'--config-file',str(config)]
with (root/'native-provision.log').open('w',encoding='utf-8') as log:
    for command in [['version'],['core','update-index'],['core','install','arduino:avr'],['core','list','--format','json']]:
        log.write('COMMAND '+repr(command)+'\n');log.flush()
        subprocess.run(cli+command,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=300)
print(json.dumps(proof,indent=2))
