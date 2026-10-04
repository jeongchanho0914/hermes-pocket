import json,time
from pathlib import Path
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
class Handler(BaseHTTPRequestHandler):
 def do_POST(self):
  raw=self.rfile.read(int(self.headers.get('Content-Length','0')));request=json.loads(raw);public=request.get('model')=='public-mimo-fixture'
  self.send_response(200);self.send_header('Content-Type','text/event-stream');self.end_headers()
  def send(delta,finish=None):
   self.wfile.write(('data: '+json.dumps({'choices':[{'delta':delta,'finish_reason':finish}]},ensure_ascii=False)+'\n\n').encode());self.wfile.flush()
  time.sleep(.3);Path('/tmp/hermes-v012-thought-stages.jsonl').open('a').write(json.dumps({'stage':'first_reasoning','public':public,'time':time.time()})+'\n');send({'reasoning_content':'PUBLIC_FIXTURE_THOUGHT_FIRST' if public else 'UNSUPPORTED_FIXTURE_REASONING_DROP'});time.sleep(1.3);send({'reasoning_content':' PUBLIC_FIXTURE_THOUGHT_SECOND' if public else ' UNSUPPORTED_FIXTURE_EXTRA'});time.sleep(1.3);send({'content':'PUBLIC_FIXTURE_FINAL_ANSWER'},'stop');self.wfile.write(b'data: [DONE]\n\n');self.wfile.flush()
 def log_message(self,*args):pass
ThreadingHTTPServer(('127.0.0.1',8894),Handler).serve_forever()
