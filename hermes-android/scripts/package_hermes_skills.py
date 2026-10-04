#!/usr/bin/env python3
"""Reproducibly package the exact audited upstream skill inventory, without execution."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MAX_FILE = 1024 * 1024
MAX_PACKAGE = 8 * MAX_FILE
EXCLUDED_DIRS = {'.git', 'node_modules', '__pycache__', '.venv', 'venv'}
EXCLUDED_SUFFIXES = {'.pyc', '.pyo', '.so', '.dll', '.dylib', '.class', '.apk', '.dex', '.keystore', '.jks', '.pem', '.key'}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def scalar(lines, key):
    """Read the scalar/block fields used by the upstream frontmatter."""
    for i, line in enumerate(lines):
        if not line.startswith(key + ':'):
            continue
        value = line[len(key) + 1:].strip()
        if re.fullmatch(r'[>|][+-]?', value):
            parts = []
            for line in lines[i + 1:]:
                if line and not line.startswith(' '):
                    break
                parts.append(line.strip())
            return (' ' if value[0] == '>' else '\n').join(parts).strip()
        if value.startswith('"'):
            try:
                return json.loads(value)
            except ValueError:
                return value.strip('"')
        if value.startswith("'") and value.endswith("'"):
            return value[1:-1].replace("''", "'")
        return value.split(' #', 1)[0].strip()
    return ''


def package(inventory, destination):
    source = ROOT / inventory['source']['path']
    docs = inventory['skill_documents']
    expected = {item['source']['path'] for item in docs}
    discovered = {p.relative_to(source).as_posix() for folder in ('skills', 'optional-skills')
                  for p in (source / folder).rglob('SKILL.md')}
    if expected != discovered:
        raise ValueError('Audited inventory differs from the complete source skill tree')
    destination.mkdir(parents=True, exist_ok=True)
    archive_dir = destination / 'packages'
    archive_dir.mkdir(exist_ok=True)
    catalog = []
    for item in sorted(docs, key=lambda item: item['source']['path']):
        document = source / item['source']['path']
        raw = document.read_bytes()
        if sha(raw) != item['source']['sha256']:
            raise ValueError('Audited SKILL.md hash changed: ' + str(document))
        package_root = document.parent
        skill_id = package_root.relative_to(source).as_posix()
        frontmatter = raw.decode('utf-8-sig').replace('\r\n', '\n').split('\n')
        end = frontmatter.index('---', 1)
        header = frontmatter[1:end]
        entries = []
        blocked = []
        excluded = []
        for path in sorted(package_root.rglob('*')):
            relative = path.relative_to(package_root).as_posix()
            if path.is_symlink():
                # Never resolve symlinks, including directory links, into the payload.
                excluded.append({'path': relative, 'reason': 'symbolic link is not packaged'})
                continue
            if not path.is_file():
                continue
            if any(part in EXCLUDED_DIRS for part in path.relative_to(package_root).parts) or path.suffix.lower() in EXCLUDED_SUFFIXES or path.name in {'.env', 'id_rsa', 'id_ed25519'}:
                excluded.append({'path': relative, 'reason': 'dependency, compiled artifact, or secret file is not packaged'})
                continue
            data = path.read_bytes()
            entries.append((relative, data, path.stat().st_mode))
            if len(data) > MAX_FILE:
                blocked.append(relative + ' exceeds 1 MiB resource limit (' + str(len(data)) + ' bytes)')
        if excluded:
            blocked.append('Source package contains excluded files; complete native import is unavailable')
        total = sum(len(data) for _, data, _ in entries)
        if total > MAX_PACKAGE:
            blocked.append('Package exceeds 8 MiB native import limit')
        if len(entries) > 256:
            blocked.append('Package exceeds 256 native file limit')
        if len(raw.decode('utf-8')) > 100000:
            blocked.append('SKILL.md exceeds 100,000 character native document limit')
        if not re.fullmatch(r'[a-z0-9][a-z0-9_-]{0,63}', package_root.name):
            blocked.append('Directory name is outside native skill name format')
        description = scalar(header, 'description')
        if not description or len(description) > 1024:
            blocked.append('Description is outside native 1–1,024 character limit')
        if not scalar(header, 'name') or not '\n'.join(frontmatter[end + 1:]).strip():
            blocked.append('Native frontmatter/body validation failed')
        key = skill_id.replace('/', '__')
        zip_path = archive_dir / (key + '.zip')
        files = []
        with zipfile.ZipFile(zip_path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for relative, data, mode in entries:
                info = zipfile.ZipInfo(relative, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = (0o100755 if mode & 0o111 else 0o100644) << 16
                archive.writestr(info, data)
                files.append({'path': relative, 'bytes': len(data), 'sha256': sha(data)})
        catalog.append({'id': skill_id, 'name': package_root.name, 'declared_name': scalar(header, 'name'),
                        'category': '/'.join(skill_id.split('/')[1:-1]), 'scope': item['scope'],
                        'description': description, 'source_path': item['source']['path'], 'sha256': sha(raw),
                        'license': scalar(header, 'license'), 'resource_count': len(entries) - 1,
                        'file_count': len(entries), 'payload_bytes': total, 'archive_bytes': zip_path.stat().st_size,
                        'package_asset': 'hermes-skills/packages/' + zip_path.name,
                        'package_sha256': sha(zip_path.read_bytes()), 'installable': not blocked,
                        'install_blockers': blocked, 'excluded_files': excluded, 'files': files,
                        'runtime_note': 'Instructions and resources only; original host tools, runtimes, and account dependencies are not granted by installation.'})
    manifest = {'schema_version': 1, 'source': inventory['source'], 'package_count': len(catalog),
                'installable_count': sum(entry['installable'] for entry in catalog),
                'payload_bytes': sum(entry['payload_bytes'] for entry in catalog),
                'archive_bytes': sum(entry['archive_bytes'] for entry in catalog), 'skills': catalog}
    (destination / 'catalog.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    shutil.copyfile(source / 'LICENSE', destination / 'UPSTREAM-LICENSE.txt')
    (destination / 'UPSTREAM.txt').write_text('Hermes Agent skill source packages\n' + inventory['source']['repository'] + '\nCommit: ' + inventory['source']['commit'] + '\nCopyright (c) 2025 Nous Research\nThe repository MIT license is preserved in UPSTREAM-LICENSE.txt.\nPer-package license files and frontmatter are preserved unchanged in each archive.\nPackaging does not grant host tools, credentials, runtime access, activation, or execution.\n', encoding='utf-8')
    print(json.dumps({key: manifest[key] for key in ('package_count', 'installable_count', 'payload_bytes', 'archive_bytes')}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', type=Path, default=ROOT / 'docs/hermes-feature-matrix.json')
    parser.add_argument('--output', type=Path, default=ROOT / 'app/src/main/assets/hermes-skills')
    args = parser.parse_args()
    package(json.loads(args.inventory.read_text(encoding='utf-8')), args.output)
