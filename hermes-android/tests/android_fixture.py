"""Local model-API fixture for real Android APK smoke tests; not a real provider."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json, time, argparse
from pathlib import Path

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def do_GET(self):
        self.send_response(200); self.send_header('Content-Type','application/json'); self.end_headers()
        models=[{'id':'android-fixture','name':'Android fixture','object':'model','supported_parameters':['reasoning_effort']}]+[{'id':f'fixture-model-{i:02}','name':f'Fixture model {i:02}','object':'model','capabilities':{'reasoning':i%2==0}} for i in range(1,16)]
        self.wfile.write(json.dumps({'data':models}).encode())
    def do_POST(self):
        payload=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        with self.server.records.open('a') as out:
            out.write(json.dumps({'path':self.path,'hasBearer':self.headers.get('Authorization','').startswith('Bearer '),'body':payload})+'\n')
        if not payload.get('stream'):
            self.send_response(200); self.send_header('Content-Type','application/json'); self.end_headers()
            self.wfile.write(json.dumps({'choices':[{'message':{'role':'assistant','content':'OK'},'finish_reason':'stop'}]}).encode());return
        messages=payload['messages']; last=messages[-1]
        user=next((m.get('content','') for m in reversed(messages) if m['role']=='user'),'')
        self.send_response(200); self.send_header('Content-Type','text/event-stream'); self.end_headers()
        def event(delta=None, finish=None):
            frame={'choices':[{'index':0,'delta':delta or {},'finish_reason':finish}]}
            self.wfile.write(('data: '+json.dumps(frame)+'\n\n').encode()); self.wfile.flush()
        try:
            if last['role']=='tool':
                event({'content':'Android local tool completed. Battery and memory came from this emulator.'})
                event(finish='stop')
            elif 'volume' in user.lower():
                event({'tool_calls':[{'index':0,'id':'android_volume_1','type':'function','function':{'name':'set_volume','arguments':'{"percent":50}'}}]})
                event(finish='tool_calls')
            elif 'tool' in user.lower():
                event({'tool_calls':[{'index':0,'id':'android_tool_1','type':'function','function':{'name':'get_device_state','arguments':'{}'}}]})
                event(finish='tool_calls')
            elif 'cancel' in user.lower():
                event({'content':'Started cancellation fixture. '}); time.sleep(30)
                event({'content':'This must not arrive after cancellation.'}); event(finish='stop')
            else:
                event({'content':'Hello from the Android API fixture. '})
                event({'content':'This response passed through the installed APK native agent.'})
                event(finish='stop')
            self.wfile.write(b'data: [DONE]\n\n'); self.wfile.flush()
        except (BrokenPipeError,ConnectionResetError): pass

if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('--port',type=int,default=8877); p.add_argument('--records',type=Path,required=True); args=p.parse_args()
    server=ThreadingHTTPServer(('127.0.0.1',args.port),Handler); server.records=args.records
    print('Android model fixture listening on 127.0.0.1:'+str(args.port),flush=True); server.serve_forever()
