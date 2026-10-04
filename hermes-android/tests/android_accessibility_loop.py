from pathlib import Path
import sys,threading,time,json,re,xml.etree.ElementTree as ET,argparse
parser=argparse.ArgumentParser();parser.add_argument('--version',default='006');parser.add_argument('--policy',choices=['auto','ask'],default='ask');options=parser.parse_args()
from android_probe import invoke
from android_ui import AndroidUI
ui=AndroidUI('emulator-5554');nodes=ui.nodes();password=next(n for n in nodes if n.attrib.get('content-desc')=='Protected password field')
if password.attrib.get('password')!='true':raise RuntimeError('Separate fixture password transformation must be correct before test')
b=list(map(int,re.findall(r'\d+',password.attrib['bounds'])));script=(Path(__file__).parent/('android-probe/v'+options.version+'_accessibility.js')).read_text().replace('POLICY_MODE',options.policy).replace('PASSWORD_X',str((b[0]+b[2])//2)).replace('PASSWORD_Y',str((b[1]+b[3])//2));slot={};events=[];typed=False
out=Path('docs/android-v'+options.version);out.mkdir(exist_ok=True)
def work():
 try:slot['probe']=invoke(script,'emulator-5554')
 except Exception as e:slot['error']=str(e)
t=threading.Thread(target=work);t.start();time.sleep(5)
def nonsuppress_nodes():
 raw=ui.adb('shell','cat','/sdcard/Android/data/dev.chanho.hermes/files/probe-ui.xml');return list(ET.fromstring(raw).iter('node'))
ui.nodes=nonsuppress_nodes
ui.adb('shell','am','start','-a','android.settings.ACCESSIBILITY_SETTINGS');time.sleep(.8);ui.tap('Hermes Pocket');time.sleep(.4);ui.tap('Use Hermes Pocket');time.sleep(.4)
texts=[n.attrib.get('text','') for n in ui.nodes()]
if 'ALLOW' in texts:ui.tap('ALLOW')
else:
 label=next((x for x in ['Stop','STOP','Turn off','TURN OFF'] if x in texts),None)
 if label is None:raise RuntimeError('Unknown accessibility confirmation '+str(texts))
 ui.tap(label);time.sleep(.4);ui.tap('Use Hermes Pocket');time.sleep(.4);ui.tap('ALLOW')
print('Actual service re-enabled while probe remains active',flush=True)
while t.is_alive():
 try:
  ns=ui.nodes();typed=typed or any(n.attrib.get('content-desc')=='Normal text field' and n.attrib.get('text')=='PUBLIC_TYPED_FIXTURE' for n in ns)
  yes=next((n for n in ns if n.attrib.get('text')=='이번만 허용'),None)
  if yes is not None:
   b=list(map(int,re.findall(r'\d+',yes.attrib['bounds'])));ui.adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));events.append('approved_actual_native_gate');ui.adb('shell','cmd','statusbar','collapse');print('Approval',len(events),flush=True);time.sleep(.9)
  else:
   keys=ui.adb('shell','cmd','notification','list')
   if '|dev.chanho.hermes|4202|' in keys:ui.adb('shell','cmd','statusbar','expand-notifications');time.sleep(.6)
 except Exception as e:events.append(str(e))
 time.sleep(.2)
t.join();slot['actualTypedTextObserved']=typed;slot['approvalEvents']=events;(out/('accessibility-'+options.policy+'-loop.json' if options.version!='006' else 'accessibility-loop.json')).write_text(json.dumps(slot,ensure_ascii=False,indent=2));print(json.dumps(slot,ensure_ascii=False),flush=True)
