"""Pinned, resource-free Shizuku dependencies for the no-Gradle APK builder."""
from pathlib import Path
import hashlib
import io
import json
import urllib.request
import zipfile

SHIZUKU_VERSION = '13.1.5'
ARTIFACTS = [
    ('api', 'aar', '4def9bde498ef8626614c2fc5db9af4749c86f16f6c33e3f5658d35e70bab59b'),
    ('provider', 'aar', 'b0f18cd9812464ec171c53cac93a819fe411718a3965c311f01eb4de265381b3'),
    ('aidl', 'aar', '33fe7191cdd69fcb66d649264f3b0c47acb2f3d6343afc05b98dbbff6f221963'),
    ('shared', 'aar', '4659642c9339be0a26e9c65bb8648f7ad6d8f4a465f557993ccbc78802381635'),
]
ANNOTATION_SHA256 = '97dc45afefe3a1e421da42b8b6e9f90491477c45fc6178203e3a5e8a05ee8553'


def verified_download(path, url, expected):
    if path.exists():
        data = path.read_bytes()
    else:
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read(8 * 1024 * 1024 + 1)
        if len(data) > 8 * 1024 * 1024:
            raise ValueError('Dependency exceeds pinned download size limit: ' + url)
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected:
        raise ValueError('Dependency SHA-256 mismatch: ' + str(path))
    if not path.exists():
        temporary = path.with_suffix(path.suffix + '.tmp')
        temporary.write_bytes(data)
        temporary.replace(path)
    return data


def resolve_android_dependencies(root):
    cache = root / '.toolchain' / ('shizuku-' + SHIZUKU_VERSION)
    cache.mkdir(parents=True, exist_ok=True)
    jars, records = [], []
    for module, extension, checksum in ARTIFACTS:
        url = ('https://repo.maven.apache.org/maven2/dev/rikka/shizuku/' +
               module + '/' + SHIZUKU_VERSION + '/' + module + '-' +
               SHIZUKU_VERSION + '.' + extension)
        path = cache / (module + '.' + extension)
        data = verified_download(path, url, checksum)
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            if archive.testzip() is not None:
                raise ValueError('Corrupt dependency archive: ' + module)
            if len(archive.namelist()) != len(set(archive.namelist())):
                raise ValueError('Duplicate dependency ZIP entries: ' + module)
            if any(name.startswith(('res/', 'jni/', 'assets/', 'libs/'))
                   for name in archive.namelist()):
                raise ValueError('Dependency requires unsupported resource/native merging: ' + module)
            if archive.read('R.txt').strip():
                raise ValueError('Dependency contains resources: ' + module)
            classes = archive.read('classes.jar')
        jar = cache / (module + '.jar')
        # Always regenerate from verified AAR, rather than trusting an extracted cache.
        jar.write_bytes(classes)
        jars.append(jar)
        records.append({'coordinate': 'dev.rikka.shizuku:' + module + ':' + SHIZUKU_VERSION,
                        'url': url, 'sha256': checksum,
                        'classesJarSha256': hashlib.sha256(classes).hexdigest(),
                        'license': 'MIT', 'nativeLibraries': False, 'resources': False})
    annotation_url = ('https://dl.google.com/dl/android/maven2/androidx/annotation/'
                      'annotation/1.3.0/annotation-1.3.0.jar')
    annotation = cache / 'androidx-annotation-1.3.0.jar'
    verified_download(annotation, annotation_url, ANNOTATION_SHA256)
    jars.append(annotation)
    records.append({'coordinate': 'androidx.annotation:annotation:1.3.0',
                    'url': annotation_url, 'sha256': ANNOTATION_SHA256,
                    'license': 'Apache-2.0', 'nativeLibraries': False})
    # Licenses are source-controlled and are included as APK assets.
    notice = root / 'app/src/main/assets/third-party-licenses.txt'
    if not notice.is_file() or 'Copyright (c) 2021 RikkaW' not in notice.read_text():
        raise ValueError('Missing bundled Shizuku MIT notice')
    if 'Apache License' not in notice.read_text():
        raise ValueError('Missing bundled AndroidX Apache license')
    (cache / 'verified-dependencies.json').write_text(json.dumps(records, indent=2) + '\n')
    return jars, records
