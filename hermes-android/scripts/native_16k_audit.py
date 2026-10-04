#!/usr/bin/env python3
"""Read-only ELF/ZIP 16 KiB audit, including Python extensions in Chaquopy IMY ZIPs."""
import argparse, hashlib, io, json, struct, zipfile
from pathlib import Path

PAGE = 16384

def elf(data):
    if data[:4] != b'\x7fELF': return None
    bits, encoding = data[4], data[5]
    order = '<' if encoding == 1 else '>'
    if bits == 2:
        header = struct.unpack_from(order+'HHIQQQIHHHHHH',data,16)
        offset, entrysize, count = header[4],header[8],header[9]
        fmt = order+'IIQQQQQQ'
    else:
        header = struct.unpack_from(order+'HHIIIIIHHHHHH',data,16)
        offset, entrysize, count = header[4],header[8],header[9]
        fmt = order+'IIIIIIII'
    loads=[];relro=[]
    for i in range(count):
        h=struct.unpack_from(fmt,data,offset+i*entrysize)
        if bits == 2:kind,flags,off,virt,phys,filesz,memsz,align=h
        else:kind,off,virt,phys,filesz,memsz,flags,align=h
        if kind==1:loads.append({'offset':off,'vaddr':virt,'memsz':memsz,'align':align,
                                'page_congruent':off%PAGE==virt%PAGE})
        elif kind==0x6474e552:relro.append({'vaddr':virt,'memsz':memsz,'end_remainder':(virt+memsz)%PAGE})
    return {'bits':64 if bits==2 else 32,'machine':header[1],
            'sha256':hashlib.sha256(data).hexdigest(),'load_segments':loads,'relro':relro,
            'load_16k':bool(loads) and all(x['align']>=PAGE and x['page_congruent'] for x in loads),
            'relro_16k':all(x['end_remainder']==0 for x in relro),
            'elf_16k':bool(loads) and all(x['align']>=PAGE and x['page_congruent'] for x in loads)
                      and all(x['end_remainder']==0 for x in relro)}

def scan(data,label,result,depth=0):
    info=elf(data)
    if info:
        result.append(dict(path=label,**info));return
    if depth>3 or not zipfile.is_zipfile(io.BytesIO(data)):return
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for member in archive.infolist():
            if not(member.filename.endswith(('.so','.imy','.zip')) or '.so.' in member.filename):continue
            child=archive.read(member);previous=len(result)
            scan(child,label+'!'+member.filename,result,depth+1)
            if len(result)>previous and child[:4]==b'\x7fELF':
                local=struct.unpack_from('<IHHHHHIIIHH',data,member.header_offset)
                payload=member.header_offset+30+local[-2]+local[-1]
                result[-1]['zip_compression']=member.compress_type
                result[-1]['zip_data_offset']=payload
                result[-1]['zip_16k_aligned']=payload%PAGE==0 if member.compress_type==0 else None

def main():
    parser=argparse.ArgumentParser();parser.add_argument('input',type=Path);parser.add_argument('--output',type=Path)
    args=parser.parse_args();rows=[]
    if args.input.is_dir():
        for path in sorted(args.input.rglob('*')):
            if path.is_file() and (path.name.endswith('.so') or '.so.' in path.name):scan(path.read_bytes(),str(path),rows)
    else:scan(args.input.read_bytes(),str(args.input),rows)
    report={'input':str(args.input),'native_count':len(rows),
            'elf_16k_pass':all(x['elf_16k'] for x in rows),
            'load_16k_failures':sum(not x['load_16k'] for x in rows),
            'relro_16k_failures':sum(not x['relro_16k'] for x in rows),
            'bad_elf_paths':[x['path'] for x in rows if not x['elf_16k']],
            'libraries':rows}
    text=json.dumps(report,indent=2)
    if args.output:args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(text+'\n')
    print(json.dumps({k:v for k,v in report.items() if k not in {'libraries','bad_elf_paths'}},indent=2))
    return 0 if report['elf_16k_pass'] else 1

if __name__=='__main__':raise SystemExit(main())
