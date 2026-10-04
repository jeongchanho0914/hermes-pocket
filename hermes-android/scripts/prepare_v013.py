#!/usr/bin/env python3
"""One-time source-only checkpoint. Never copies credentials or signing material."""
from pathlib import Path
import hashlib, json, shutil
root = Path(__file__).resolve().parents[1]
backup = root / '.snapshots' / 'pre-v013'
if backup.exists():
    raise SystemExit('Checkpoint already exists; refusing to overwrite it.')
backup.mkdir(parents=True, mode=0o700)
manifest = {}
for relative in ['app', 'scripts', 'tests', 'version.json', 'README.md', 'build.gradle', 'settings.gradle']:
    source = root / relative
    if source.is_dir():
        for path in source.rglob('*'):
            if not path.is_file() or '__pycache__' in path.parts or 'build' in path.relative_to(source).parts:
                continue
            rel = path.relative_to(root)
            target = backup / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
            manifest[str(rel)] = hashlib.sha256(path.read_bytes()).hexdigest()
    elif source.is_file():
        shutil.copy2(source, backup / relative)
        manifest[relative] = hashlib.sha256(source.read_bytes()).hexdigest()
(backup / 'source-hashes.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
print(json.dumps({'checkpoint': str(backup), 'files': len(manifest), 'credentials_copied': False}))
