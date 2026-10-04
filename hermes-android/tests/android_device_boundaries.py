"""Actual native permission/approval checks on an emulator only."""
from pathlib import Path
import threading,time,json,re,argparse
from android_probe import invoke
from android_ui import AndroidUI
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('--serial',default='emulator-5554');a.add_argument('--output',type=Path,required=True);a.add_argument('--script',type=Path,default=Path(__file__).parent/'android-probe/device-boundaries.js');opt=a.parse_args();ui=AndroidUI(opt.serial);slot={};events=[]
 def work():
  try:slot['result']=invoke(opt.script.read_text(),opt.serial)
  except Exception as e:slot['error']=str(e)
 t=threading.Thread(target=work);t.start()
 while t.is_alive():
  try:
   nodes=ui.nodes();texts=[n.attrib.get('text','') for n in nodes];detail=' '.join(texts)
   if '이번만 허용' in texts:
    deny=any(x in detail for x in ['앱 열기:','Android 설정 페이지 열기:','미디어 볼륨을','DENY_PUBLIC_TEST'])
    label='거부' if deny else '이번만 허용';n=next(n for n in nodes if n.attrib.get('text')==label);bounds=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));ui.adb('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2));events.append({'decision':label,'detail':detail});time.sleep(.4)
  except Exception as e:events.append({'uiError':str(e)})
  time.sleep(.3)
 t.join();opt.output.parent.mkdir(parents=True,exist_ok=True);opt.output.write_text(json.dumps({'probe':slot,'approvalUiEvents':events},ensure_ascii=False,indent=2));print(json.dumps(slot,ensure_ascii=False))
