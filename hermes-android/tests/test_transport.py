"""Deterministic transport tests. FixtureEngine is not a real-model test."""
import json,sys,threading,time,unittest,uuid,urllib.request,urllib.error
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'server'))
from core import Manager, Run, ProtocolError, make_server, checked_uuid
from phone_tools import validate,SCHEMAS

def payload(text='hello'):
    return {'request_id':str(uuid.uuid4()),'session':str(uuid.uuid4()),'input':text,'memory':''}
class FixtureEngine:
    name='fixture-NOT-A-MODEL';ready=True;error=''
    def run(self,run):
        result=run.phone('get_device_state',{})
        run.delta('fixture: ')
        return 'fixture: '+str(result.get('result',{}).get('battery','missing'))
    def cancel(self,run):run.cancel()
class Tests(unittest.TestCase):
    def test_uuid_rejects_paths(self):
        for bad in ['../../etc','A'*36,'0'*36,None]:
            with self.assertRaises(ProtocolError):checked_uuid(bad)
    def test_tools_are_phone_only(self):
        self.assertEqual(set(SCHEMAS), {'get_device_state','list_apps','launch_app','open_settings','set_volume','set_brightness','root_processes','set_wifi','force_stop_app','read_screen','click_element','type_text','scroll_element'})
        with self.assertRaises(ValueError):validate('exec',{'command':'anything'})
    def test_schema_fails_closed(self):
        for name,args in [('get_device_state',{'extra':1}),('set_volume',{'percent':True}),('set_volume',{'percent':101}),('type_text',{'snapshot':'a','element':'b','text':'x'*2001})]:
            with self.assertRaises(ValueError):validate(name,args)
        validate('set_wifi',{'enabled':False})
    def test_result_idempotency_and_conflict(self):
        run=Run(payload());ident=str(uuid.uuid4());run.pending.add(ident)
        run.accept(ident,{'ok':True});run.accept(ident,{'ok':True})
        with self.assertRaises(ProtocolError):run.accept(ident,{'ok':False})
        with self.assertRaises(ProtocolError):run.accept(str(uuid.uuid4()),{'ok':True})
    def test_cancel_wakes_waiting_tool(self):
        run=Run(payload());ended=threading.Event()
        def worker():
            try:run.phone('get_device_state',{})
            except InterruptedError:ended.set()
        thread=threading.Thread(target=worker);thread.start()
        for _ in range(50):
            if run.pending:break
            time.sleep(.01)
        run.cancel();thread.join(2)
        self.assertTrue(ended.is_set())
    def test_unconfigured_engine_refuses_run(self):
        engine=FixtureEngine();engine.ready=False;engine.error='not configured';m=Manager(engine)
        with self.assertRaises(ProtocolError) as caught:m.create(payload())
        self.assertEqual(caught.exception.status,503)
    def test_session_and_request_isolation(self):
        m=Manager(FixtureEngine());data=payload();run=m.create(data)
        self.assertIs(m.create(data),run)
        changed={**data,'input':'different'}
        with self.assertRaises(ProtocolError):m.create(changed)
        with self.assertRaises(ProtocolError):m.create(payload())
        m.stop()
    def test_event_cursor_monotonic(self):
        run=Run(payload());run.delta('a');run.delta('b')
        self.assertEqual([e['seq'] for e in run.snapshot(1)['events']],[2])
        self.assertEqual(run.snapshot(2)['events'],[])
    def test_authenticated_e2e_phone_roundtrip(self):
        token='unit-test-token-not-a-secret-12345';manager=Manager(FixtureEngine())
        server=make_server(('127.0.0.1',0),token,manager)
        threading.Thread(target=server.serve_forever,daemon=True).start()
        base='http://127.0.0.1:'+str(server.server_port)
        def call(path,body=None,authorized=True,origin=None):
            headers={'Content-Type':'application/json'}
            if authorized:headers['Authorization']='Bearer '+token
            if origin:headers['Origin']=origin
            req=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
            try:
                with urllib.request.urlopen(req,timeout=5) as r:return r.status,json.load(r)
            except urllib.error.HTTPError as e:return e.code,json.load(e)
        try:
            self.assertEqual(call('/api/health',authorized=False)[0],401)
            self.assertEqual(call('/api/health',origin='https://untrusted.example')[0],403)
            self.assertEqual(call('/api/health')[1]['engine'],'fixture-NOT-A-MODEL')
            status,created=call('/api/runs',payload());self.assertEqual(status,200)
            rid=created['id'];events=[]
            for _ in range(50):
                snapshot=call('/api/runs/'+rid+'?after=0')[1];events=snapshot['events']
                if events:break
                time.sleep(.01)
            request=next(e['data'] for e in events if e['type']=='tool_request')
            self.assertEqual(request['name'],'get_device_state')
            result={'result':{'ok':True,'result':{'battery':73}}}
            self.assertEqual(call('/api/runs/'+rid+'/results/'+request['id'],result)[0],200)
            for _ in range(50):
                snapshot=call('/api/runs/'+rid+'?after=0')[1]
                if snapshot['status']=='completed':break
                time.sleep(.01)
            self.assertEqual(snapshot['output'],'fixture: 73')
            self.assertEqual(call('/api/runs/'+rid+'/results/'+request['id'],result)[0],200)
            self.assertEqual(call('/api/runs/'+rid+'/results/'+request['id'],{'result':{'ok':False}})[0],409)
        finally:manager.stop();server.shutdown();server.server_close()
    def test_weak_token_rejected(self):
        with self.assertRaises(ValueError):make_server(('127.0.0.1',0),'short',Manager(FixtureEngine()))
if __name__=='__main__':unittest.main(verbosity=2)
