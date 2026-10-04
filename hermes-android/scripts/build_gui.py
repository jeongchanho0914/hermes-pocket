#!/usr/bin/env python3
"""Owner-operated APK build GUI. Does not run unless the owner clicks Build."""
from pathlib import Path
import os,queue,signal,subprocess,sys,threading,tkinter as tk
from tkinter import ttk,messagebox
from versioning import load_version
ROOT=Path(__file__).resolve().parents[1]

def main():
    root=tk.Tk();root.title('Hermes Pocket · APK 빌드');root.geometry('780x590')
    f=ttk.Frame(root,padding=18);f.pack(fill='both',expand=True)
    ttk.Label(f,text='Hermes Pocket / APK Builder',font=('sans',20,'bold')).pack(anchor='w')
    ttk.Label(f,text='Linux x86_64 · JDK 17+ · 서명 키는 내 PC에만 보관',padding=(0,8)).pack(anchor='w')
    ttk.Label(f,text='version.json의 버전으로 APK를 만들고 서명·패키지 정보를 검증합니다.\n기기별 동작 검증 결과는 docs/TEST_REPORT.md를 확인하세요.',wraplength=740).pack(anchor='w',pady=8)
    consent=tk.BooleanVar(value=False)
    ttk.Checkbutton(f,text='Android SDK 약관을 검토했고 필요한 빌드 도구 다운로드에 동의합니다.',variable=consent).pack(anchor='w')
    ttk.Label(f,text='약관: https://developer.android.com/studio/terms').pack(anchor='w',pady=(0,8))
    log=tk.Text(f,wrap='word',font=('monospace',10));log.pack(fill='both',expand=True,pady=8)
    events=queue.Queue();active={'process':None,'busy':False,'stop':False,'closed':False}
    def append(text):
        log.insert('end',text);log.see('end')
    def worker():
        path=ROOT/'build-gui.log'
        try:
            with path.open('w') as output:
                stages=[]
                if not (ROOT/'.toolchain/paths.json').exists():stages.append('bootstrap_build.py')
                stages.append('build.py')
                for name in stages:
                    if active['stop']:raise InterruptedError('빌드를 중단했습니다.')
                    process=subprocess.Popen([sys.executable,str(ROOT/'scripts'/name)],cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,start_new_session=True)
                    active['process']=process
                    for line in process.stdout:output.write(line);output.flush();events.put(('line',line))
                    if process.wait()!=0:raise RuntimeError(name+' 단계가 실패했습니다. 위 오류와 build-gui.log를 확인하세요.')
                artifact=ROOT/'dist'/load_version(ROOT).apk_name
                if not artifact.is_file():raise RuntimeError('빌드 명령 종료 후에도 APK가 없습니다.')
                events.put(('success','서명 검증 완료. APK: '+str(artifact)+'\n설치 및 모델·Root 실제 동작 검증은 별도로 필요합니다.'))
        except Exception as exc:events.put(('error',str(exc)))
        finally:active['process']=None;active['busy']=False;events.put(('done',''))
    def start():
        if active['busy']:return
        if not consent.get():messagebox.showwarning('다운로드 확인','약관과 다운로드 내용을 먼저 확인해 주세요.');return
        active['busy']=True;active['stop']=False;build.config(state='disabled');append('\n빌드를 시작합니다.\n')
        threading.Thread(target=worker,daemon=True).start()
    def stop():
        active['stop']=True
        process=active['process']
        if process is not None and process.poll() is None:
            try:os.killpg(process.pid,signal.SIGTERM)
            except ProcessLookupError:pass
    def poll():
        if active['closed']:return
        while not events.empty():
            kind,text=events.get()
            if kind=='line':append(text)
            elif kind=='success':append(text+'\n');messagebox.showinfo('APK 생성',text)
            elif kind=='error':append('오류: '+text+'\n')
            elif kind=='done':build.config(state='normal')
        root.after(100,poll)
    row=ttk.Frame(f);row.pack(fill='x')
    build=ttk.Button(row,text='APK 빌드 시작',command=start);build.pack(side='left')
    ttk.Button(row,text='중단',command=stop).pack(side='left',padx=8)
    def close():
        if active['busy'] and not messagebox.askyesno('중단','진행 중인 빌드를 중단하고 닫을까요?'):return
        stop();active['closed']=True;root.destroy()
    root.protocol('WM_DELETE_WINDOW',close);poll();root.mainloop()
if __name__=='__main__':main()
