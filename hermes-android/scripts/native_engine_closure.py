#!/usr/bin/env python3
"""Read-only native dependency profile. Does not mutate a package or invent modules."""
import argparse, json, re, subprocess
from pathlib import Path

SYSTEM={'libc.so','libm.so','libdl.so','liblog.so','libz.so','libandroid.so'}
ROOTS=('jiter/','pydantic_core/','cryptography/','psutil/','_cffi_backend','_ruamel_yaml')

def needed(path):
    s=subprocess.run(['readelf','-d',str(path)],capture_output=True,text=True,check=True).stdout
    return re.findall(r'Shared library: \[([^\]]+)\]',s)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('termux_root',type=Path);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();site=args.termux_root/'venv/lib/python3.14/site-packages'
    extensions=[p for p in site.rglob('*.so') if any(x in str(p.relative_to(site)) for x in ROOTS)]
    available={p.name:p for p in (args.termux_root/'runtime-libs').rglob('*') if p.is_file() and '.so' in p.name}
    selected={};missing=set();queue=[p for p in extensions]
    while queue:
        p=queue.pop()
        if str(p) in selected:continue
        dependencies=needed(p);selected[str(p)]=dependencies
        for name in dependencies:
            if name in SYSTEM or name=='libpython3.14.so':continue
            if name not in available:missing.add(name)
            else:queue.append(available[name])
    result={'profile':'genuine OpenAI text/memory/skills imports; not all image/document/provider features',
            'extensions':[str(p.relative_to(site)) for p in extensions],
            'runtime_libraries':sorted(p.name for p in available.values() if str(p) in selected),
            'sdk_supplied':['libpython3.14.so','Chaquopy JNI runtime and stdlib libraries'],
            'system_supplied':sorted(SYSTEM),'missing':sorted(missing),'dependency_edges':selected,
            'warning':'Static profile only. Actual executed native imports and advertised capabilities must be checked before removing any file.'}
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k!='dependency_edges'},indent=2))

if __name__=='__main__':main()
