#!/usr/bin/env python3
"""Owner-facing setup GUI. Run with a prepared Hermes Python environment."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import secrets
import threading
import subprocess
import sys
import time
import urllib.request
from urllib.parse import urlparse
from core import HermesEngine, Manager, make_server

ROOT=Path(__file__).resolve().parents[1]
STATE=ROOT/"state"

def read(path:Path,default:dict) -> dict:
    return json.loads(path.read_text()) if path.exists() else dict(default)

def private_write(path:Path,data:dict) -> None:
    path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    temporary=path.with_suffix(".tmp")
    fd=os.open(temporary,os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
    with os.fdopen(fd,"w") as out:json.dump(data,out,ensure_ascii=False,indent=2)
    os.replace(temporary,path);os.chmod(path,0o600)

def configuration():
    config=read(STATE/"settings.json",{"hermes_source":"","base_url":"","model":"","provider":"openai","host":"127.0.0.1","port":8765})
    credentials=read(STATE/"credentials.json",{"api_key":"","phone_token":secrets.token_urlsafe(32)})
    return config,credentials

def prepare(config,credentials):
    STATE.mkdir(parents=True,exist_ok=True,mode=0o700)
    engine=HermesEngine(config,credentials,STATE)
    if not engine.ready:raise RuntimeError(engine.error)
    manager=Manager(engine)
    server=make_server((config.get("host","127.0.0.1"),int(config.get("port",8765))),credentials["phone_token"],manager)
    return server,manager

def gui():
    import tkinter as tk
    from tkinter import ttk,messagebox,filedialog
    config,credentials=configuration()
    root=tk.Tk();root.title("Hermes Pocket · Android 연결");root.geometry("690x660");root.minsize(590,620)
    root.configure(bg="#101314")
    style=ttk.Style();style.theme_use("clam")
    style.configure("TFrame",background="#101314");style.configure("TLabel",background="#101314",foreground="#edf1ec",font=("sans",11))
    style.configure("TButton",font=("sans",11),padding=9)
    style.configure("TEntry",padding=7,fieldbackground="#202727",foreground="#edf1ec",insertcolor="#edf1ec")
    frame=ttk.Frame(root,padding=24);frame.pack(fill="both",expand=True)
    ttk.Label(frame,text="HERMES / POCKET",font=("sans",22,"bold"),foreground="#f4b860").pack(anchor="w")
    ttk.Label(frame,text="기존 Hermes 연결·프로필을 건드리지 않는 별도 모바일 서버",wraplength=620).pack(anchor="w",pady=(7,18))
    values={}
    for key,label,secret in [("hermes_source","준비된 Hermes 소스 폴더",False),("base_url","모델 API Base URL (보통 /v1로 끝남)",False),("model","모델 ID",False),("api_key","모델 API 키 (로컬 파일 권한 600으로 저장)",True),("phone_token","휴대폰 연결 토큰 (APK 설정에 입력)",True)]:
        ttk.Label(frame,text=label).pack(anchor="w",pady=(8,4))
        var=tk.StringVar(value=credentials.get(key,config.get(key,"")));values[key]=var
        row=ttk.Frame(frame);row.pack(fill="x")
        entry=ttk.Entry(row,textvariable=var,show="•" if secret else "");entry.pack(side="left",fill="x",expand=True)
        if key=="hermes_source":ttk.Button(row,text="선택",command=lambda:values["hermes_source"].set(filedialog.askdirectory() or values["hermes_source"].get())).pack(side="right",padx=(5,0))
        if key=="phone_token":
            def copy_token():
                root.clipboard_clear();root.clipboard_append(values["phone_token"].get());status.set("연결 토큰을 클립보드에 복사했습니다. 안전한 경로로 휴대폰에 입력하세요.")
            ttk.Button(row,text="복사",command=copy_token).pack(side="right",padx=(5,0))
    lan=tk.BooleanVar(value=config.get("host")=="0.0.0.0")
    tk.Checkbutton(frame,text="사설 LAN 연결 허용 (HTTP는 암호화되지 않음, 신뢰하는 네트워크 전용)",variable=lan,bg="#101314",fg="#edf1ec",selectcolor="#283330",activebackground="#101314",activeforeground="#edf1ec").pack(anchor="w",pady=(15,5))
    status=tk.StringVar(value="아직 실행하지 않았습니다. 폰은 API 키 또는 서버 연결 설정이 필요합니다.")
    ttk.Label(frame,textvariable=status,wraplength=620,foreground="#9cbbb0").pack(anchor="w",pady=10)
    active={"process":None,"starting":False}
    def stop():
        process=active.get("process")
        if process is not None and process.poll() is None:
            process.terminate()
            def reap():
                try:process.wait(timeout=5)
                except subprocess.TimeoutExpired:process.kill();process.wait()
            threading.Thread(target=reap,daemon=True).start()
        active["process"]=None
        status.set("서버를 중단했습니다. 진행 중인 폰 작업은 APK의 중단 버튼으로도 취소하세요.")
    def start():
        if active["starting"] or active.get("process") is not None and active["process"].poll() is None:return
        endpoint=values["base_url"].get().strip();parsed=urlparse(endpoint)
        if parsed.scheme not in ("https","http") or not parsed.hostname or parsed.username or parsed.query or parsed.fragment:
            messagebox.showerror("주소 확인","모델 API의 유효한 Base URL을 입력하세요.");return
        if parsed.scheme=="http" and parsed.hostname not in ("localhost","127.0.0.1","::1"):
            if not messagebox.askyesno("암호화되지 않은 모델 연결","이 모델 주소는 HTTP입니다. 입력 내용과 API 키가 암호화되지 않습니다. 신뢰하는 사설 서버인가요?"):return
        if not values["model"].get().strip():messagebox.showerror("모델 ID","모델 ID를 입력하세요.");return
        if lan.get() and not messagebox.askyesno("사설 LAN 연결","8765 포트를 같은 네트워크에 엽니다. 공유기 포트 포워딩은 설정하지 마세요. 계속할까요?"):return
        config.update({k:values[k].get().strip() for k in ("hermes_source","base_url","model")});config["host"]="0.0.0.0" if lan.get() else "127.0.0.1"
        credentials.update({k:values[k].get() for k in ("api_key","phone_token")})
        if len(credentials["phone_token"])<24:messagebox.showerror("토큰","연결 토큰은 24자 이상이어야 합니다.");return
        private_write(STATE/"settings.json",config);private_write(STATE/"credentials.json",credentials)
        active["starting"]=True;status.set("Hermes 라이브러리와 도구 범위를 확인하는 중…")
        def boot():
            try:
                log_path=STATE/"server.log"
                fd=os.open(log_path,os.O_CREAT|os.O_APPEND|os.O_WRONLY,0o600)
                with os.fdopen(fd,"w") as log:
                    process=subprocess.Popen([sys.executable,str(Path(__file__).resolve()),"--headless"],cwd=ROOT,stdout=log,stderr=log)
                active["process"]=process
                for _ in range(100):
                    if process.poll() is not None:raise RuntimeError("서버가 종료되었습니다. state/server.log를 확인하세요.")
                    request=urllib.request.Request("http://127.0.0.1:8765/api/health",headers={"Authorization":"Bearer "+credentials["phone_token"]})
                    try:
                        with urllib.request.urlopen(request,timeout=1) as response:
                            health=json.load(response)
                        if health.get("service")=="hermes-pocket" and health.get("engine_ready"):break
                    except (OSError,ValueError):time.sleep(.3)
                else:
                    process.terminate();raise RuntimeError("서버 시작을 확인하지 못했습니다.")
                root.after(0,lambda:status.set("실행 중 · 포트 8765 · APK에서 Hermes 모드 + 서버 주소 + 연결 토큰을 설정하세요."))
            except Exception as exc:
                message=str(exc)
                root.after(0,lambda m=message:status.set("시작 실패: "+m))
            finally:active["starting"]=False
        threading.Thread(target=boot,daemon=True).start()
    row=ttk.Frame(frame);row.pack(fill="x",pady=8)
    ttk.Button(row,text="저장하고 서버 시작",command=start).pack(side="left")
    ttk.Button(row,text="서버 중단",command=stop).pack(side="left",padx=8)
    ttk.Label(frame,text="이 서버는 일반 Hermes /mcp 또는 기본 API 서버와 다른 모바일 어댑터입니다.\n터미널·임의 파일 접근은 모델 도구로 노출하지 않습니다.",wraplength=620,font=("sans",9),foreground="#87958f").pack(anchor="w",pady=8)
    root.protocol("WM_DELETE_WINDOW",lambda:(stop(),root.destroy()))
    root.mainloop()

if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--headless",action="store_true",help="Run saved configuration, without GUI")
    opts=parser.parse_args()
    if opts.headless:
        config,credentials=configuration();server,manager=prepare(config,credentials)
        print("Hermes Pocket listening on",server.server_address,flush=True)
        try:server.serve_forever()
        except KeyboardInterrupt:pass
        finally:manager.stop();server.server_close()
    else:gui()
