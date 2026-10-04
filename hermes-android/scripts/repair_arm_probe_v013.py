"""Repair two reproduced test-runtime issues without changing production or upstream engine code."""
from pathlib import Path
import hashlib,json,shutil
R=Path(__file__).resolve().parents[1]
C=R/'engine-spike/candidates/full-webp'
fixture=C/'app/src/engine/python/engine_fixture.py'
s=fixture.read_text();old="'fixture.context_length': 32768"
if s.count(old)!=1:raise RuntimeError('Fixture metadata anchor changed')
# This bounded localhost fixture supports arbitrary synthetic messages; it is not a real-model capacity claim.
fixture.write_text(s.replace(old,"'fixture.context_length': 131072"),encoding='utf-8')
source=R/'engine-spike/termux-assets/extracted/data/data/com.termux/files/usr/lib/hermes-agent/tools/python/data/data/com.termux/files/usr/lib/python3.14/lib-dynload/math.cpython-314-aarch64-linux-android.so'
folder=C/'app/src/main/assets/standalone-extensions';folder.mkdir(parents=True,exist_ok=True)
shutil.copy2(source,folder/source.name)
(folder/'provenance.json').write_text(json.dumps({'purpose':'genuine missing stdlib math extension for the separately launched CPython; not an import stub','source':str(source.relative_to(R)),'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'bytes':source.stat().st_size,'license':'Python Software Foundation; retained Python runtime license/provenance applies'},indent=2),encoding='utf-8')
p=C/'app/src/main/python/standalone_probe.py';s=p.read_text()
old='                content.extractall(target)\n    # Clear credentials'
new='''                content.extractall(target)
        # The standalone Termux launcher has a different built-in module table
        # from the embedded interpreter. Supply the actual missing extension.
        name = "math.cpython-314-aarch64-linux-android.so"
        (extensions / name).write_bytes(apk.read("assets/standalone-extensions/" + name))
    # Clear credentials'''
if s.count(old)!=1:raise RuntimeError('Standalone extraction anchor changed')
p.write_text(s.replace(old,new),encoding='utf-8')
print('Fixture context metadata and genuine standalone math extension repaired in the isolated candidate only.')
