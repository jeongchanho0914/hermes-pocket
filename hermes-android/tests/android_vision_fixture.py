"""Local API-only responder: requires actual native screenshot pixels before completing.
No provider intelligence is simulated. Native capture/tool flow and image transport are verified.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from io import BytesIO
import argparse,base64,hashlib,json,time,subprocess,xml.etree.ElementTree as ET
from PIL import Image

COLORS=((231,35,61),(32,190,105),(38,99,224))
def inspect_images(payload, directory):
    found=[]
    for message in payload.get('messages',[]):
        content=message.get('content')
        if not isinstance(content,list):continue
        for part in content:
            if part.get('type')!='image_url':continue
            url=part.get('image_url',{}).get('url','')
            if not url.startswith(('data:image/png;base64,','data:image/jpeg;base64,','data:image/webp;base64,')):
                raise ValueError('Vision test requires actual inline bitmap bytes')
            raw=base64.b64decode(url.split(',',1)[1],validate=True)
            image=Image.open(BytesIO(raw));image.load();rgb=image.convert('RGB')
            if image.width<200 or image.height<200:raise ValueError('Unexpected tiny native screenshot')
            counts=[]
            histogram={}
            for pixel in rgb.getdata():histogram[pixel]=histogram.get(pixel,0)+1
            for color in COLORS:counts.append(sum(n for p,n in histogram.items() if max(abs(p[i]-color[i]) for i in range(3))<=8))
            if min(counts)<1000:raise ValueError('Native visual fixture RGB pixels absent')
            digest=hashlib.sha256(raw).hexdigest();directory.mkdir(parents=True,exist_ok=True)
            (directory/(digest+'.'+image.format.lower())).write_bytes(raw)
            found.append({'sha256':digest,'bytes':len(raw),'width':image.width,'height':image.height,'format':image.format,'fixtureColorPixelCounts':counts,'detail':part.get('image_url',{}).get('detail')})
    return found

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def do_GET(self):
        self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers()
        self.wfile.write(json.dumps({'data':[{'id':'native-vision-fixture','object':'model'}]}).encode())
    def do_POST(self):
        payload=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        evidence={'path':self.path,'stream':bool(payload.get('stream')),'messageRoles':[m.get('role') for m in payload.get('messages',[])],'hasBearer':self.headers.get('Authorization','').startswith('Bearer ')}
        try:
            evidence['images']=inspect_images(payload,self.server.images)
            evidence['visionValidated']=bool(evidence['images'])
        except Exception as e:evidence['visionValidated']=False;evidence['imageError']=str(e)
        tool_results=[]
        for m in payload.get('messages',[]):
            if m.get('role')=='tool':
                try:tool_results.append((m.get('tool_call_id'),json.loads(m.get('content','{}'))))
                except (ValueError,TypeError):pass
        evidence['nativeToolStates']=[{'id':i,'ok':r.get('ok'),'package':r.get('result',{}).get('package'),'postCounterTexts':[e.get('text') for e in r.get('result',{}).get('postState',{}).get('elements',[]) if str(e.get('text','')).startswith('Visual counter:')]} for i,r in tool_results]
        if evidence.get('visionValidated') and self.server.observe_adb:
            try:
                shot=subprocess.check_output([self.server.observe_adb,'-s','emulator-5554','exec-out','screencap','-p'])
                self.server.images.mkdir(parents=True,exist_ok=True);(self.server.images/'simultaneous-visible-overlay.png').write_bytes(shot)
                evidence['simultaneousWindowState']=subprocess.check_output([self.server.observe_adb,'-s','emulator-5554','shell','dumpsys','window','windows'],text=True)
            except Exception as e:evidence['observationError']=str(e)
        completed_actual_tap=next((r for i,r in tool_results if i=='native_tap_008'),None)
        if completed_actual_tap is not None and self.server.observe_adb:
            evidence['independentDelayedCounterSamples']=[]
            for delay in [0,.2,.3]:
                time.sleep(delay)
                try:
                    xml=subprocess.check_output([self.server.observe_adb,'-s','emulator-5554','shell','cat','/sdcard/Android/data/dev.chanho.hermes/files/probe-ui.xml'],text=True)
                    texts=[n.get('text') for n in ET.fromstring(xml).iter('node') if str(n.get('text','')).startswith('Visual counter:')]
                    evidence['independentDelayedCounterSamples'].append({'additionalDelaySeconds':delay,'actualUiAutomationTexts':texts})
                except Exception as e:evidence['independentDelayedCounterSamples'].append({'error':str(e)})
            try:
                shot=subprocess.check_output([self.server.observe_adb,'-s','emulator-5554','exec-out','screencap','-p']);self.server.images.mkdir(parents=True,exist_ok=True);(self.server.images/'independent-after-tap.png').write_bytes(shot)
            except Exception as e:evidence['afterTapScreenshotError']=str(e)
        with self.server.records.open('a') as out:out.write(json.dumps(evidence)+'\n')
        self.send_response(200);self.send_header('Content-Type','text/event-stream' if payload.get('stream') else 'application/json');self.end_headers()
        message={'role':'assistant'}
        completed_tap=next((r for i,r in tool_results if i=='native_tap_008'),None)
        if self.server.action and completed_tap is not None:
            texts=[e.get('text') for e in completed_tap.get('result',{}).get('postState',{}).get('elements',[])]
            message['content']='ACTUAL_NATIVE_TAP_008_COUNTER_ONE' if completed_tap.get('ok') and 'Visual counter: 1' in texts else 'NATIVE_TAP_FAILED_REAL_POSTSTATE: '+str(completed_tap)
        elif self.server.action and evidence.get('visionValidated'):
            capture=next((r.get('result',{}) for i,r in reversed(tool_results) if i=='native_capture_008'),{})
            button=next((e for e in capture.get('elements',[]) if e.get('description')=='Visual increment'),None)
            if not button:message['content']='NATIVE_VISUAL_TARGET_MISSING'
            else:
                b=button['bounds'];args={'snapshot':capture['snapshot'],'x':(b[0]+b[2])//2,'y':(b[1]+b[3])//2}
                message['tool_calls']=[{'id':'native_tap_008','type':'function','function':{'name':'tap_screen','arguments':json.dumps(args)}}]
        elif evidence.get('visionValidated'):
            message['content']='ACTUAL_NATIVE_VISION_008: Native inline image decoded; red, green and blue canvas regions are present. This local responder verifies image transport, not real model intelligence.'
        elif any(m.get('role')=='tool' for m in payload.get('messages',[])):
            message['content']='VISION_IMAGE_MISSING_OR_INVALID: '+evidence.get('imageError','No structured screenshot bitmap reached the API.')
        else:
            message['tool_calls']=[{'id':'native_capture_008','type':'function','function':{'name':'capture_screen','arguments':'{}'}}]
        if not payload.get('stream'):
            self.wfile.write(json.dumps({'choices':[{'message':message,'finish_reason':'tool_calls' if 'tool_calls' in message else 'stop'}]}).encode());return
        delta={k:v for k,v in message.items() if k!='role'}
        if 'tool_calls' in delta:delta['tool_calls'][0]['index']=0
        try:
            for frame in [{'choices':[{'index':0,'delta':delta,'finish_reason':None}]},{'choices':[{'index':0,'delta':{},'finish_reason':'tool_calls' if 'tool_calls' in delta else 'stop'}]}]:
                self.wfile.write(('data: '+json.dumps(frame)+'\n\n').encode());self.wfile.flush()
            self.wfile.write(b'data: [DONE]\n\n');self.wfile.flush()
        except (BrokenPipeError,ConnectionResetError):pass

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--port',type=int,default=8888);p.add_argument('--records',type=Path,required=True);p.add_argument('--images',type=Path,required=True);p.add_argument('--action',action='store_true');p.add_argument('--observe-adb');args=p.parse_args()
    server=ThreadingHTTPServer(('127.0.0.1',args.port),Handler);server.records=args.records;server.images=args.images;server.action=args.action;server.observe_adb=args.observe_adb
    print('Native vision responder listening on localhost:'+str(args.port),flush=True);server.serve_forever()
