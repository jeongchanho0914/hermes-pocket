from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];S=ROOT/'app/src/main/java/dev/chanho/hermes'
def patch(file,old,new,count=1):
 p=S/file;s=p.read_text();assert s.count(old)==count,(file,s.count(old),old[:100]);p.write_text(s.replace(old,new),encoding='utf-8')
patch('TaskLedger.java','if(!directory.getAbsoluteFile().equals(directory.getCanonicalFile()))throw new SecurityException("작업 기록 경로가 올바르지 않습니다.");\n        file=new File(directory,"jobs.json");','if(Files.isSymbolicLink(directory.toPath()))throw new SecurityException("작업 기록 폴더 자체의 심볼릭 링크는 허용하지 않습니다.");\n        file=new File(directory.getCanonicalFile(),"jobs.json");')
patch('TaskLedger.java','        while(rows.size()>=64){','        LinkedHashMap<String,JSONObject> original=new LinkedHashMap<>(rows);\n        while(rows.size()>=64){')
patch('TaskLedger.java','catch(Exception e){rows.remove(id);throw e;}return id;','catch(Exception e){rows.clear();rows.putAll(original);throw e;}return id;')
patch('TaskLedger.java','        if("cancelling".equals(before)&&"completed".equals(status))status="cancelled";','        if("cancelling".equals(before)&&("running".equals(status)||"queued".equals(status)))return;\n        if("cancelling".equals(before)&&"completed".equals(status))status="cancelled";')
patch('TaskPool.java','try{ledger.update(id,"cancelled","중단됨",null,"사용자가 중단했습니다.",control.metrics());}catch(Exception ignored){}','try{ledger.update(id,"cancelling","중단 처리 중",null,"사용자가 중단했습니다.",control.metrics());}catch(Exception ignored){}')
patch('TaskPool.java','finally{active.remove(id);futures.remove(id);listener.changed();}\n            }','''finally{
                    if(isCancelled())try{ledger.update(id,"cancelled","중단 완료",null,"사용자가 중단했습니다.",control.metrics());}catch(Exception ignored){}
                    active.remove(id);futures.remove(id);listener.changed();
                }
            }''')
patch('WorkerAgent.java','ModelLimits.estimate(history.toString())','ModelLimits.estimate(history,available)')
patch('AgentRuntime.java','    private Object activeForegroundService;','''    private Object activeForegroundService;
    private boolean serviceAcknowledged;
    private volatile int pendingWorkerStarts;
    private long workerStopGeneration;
''')
patch('AgentRuntime.java','synchronized void foregroundServiceCreated(Object service){activeForegroundService=service;}','synchronized void foregroundServiceCreated(Object service){activeForegroundService=service;serviceAcknowledged=false;}')
patch('AgentRuntime.java','if(activeForegroundService==service)activeForegroundService=null;','if(activeForegroundService==service){activeForegroundService=null;serviceAcknowledged=false;notifyAll();}')
patch('AgentRuntime.java','        CountDownLatch ready=foregroundReady;if(ready!=null)ready.countDown();','        serviceAcknowledged=true;notifyAll();\n        CountDownLatch ready=foregroundReady;if(ready!=null)ready.countDown();')
patch('AgentRuntime.java','boolean anyWork(){return busy()||terminalHasJobs()||jobs.hasActive();}\n    void startWorkerService(){context.startForegroundService(new Intent(context,AgentForegroundService.class));}','''boolean anyWork(){return busy()||terminalHasJobs()||jobs.hasActive()||pendingWorkerStarts>0;}
    synchronized long beginWorkerStart(){pendingWorkerStarts++;return workerStopGeneration;}
    synchronized void checkWorkerStart(long generation) throws InterruptedException {
        if(generation!=workerStopGeneration)throw new InterruptedException("작업 시작 중 사용자가 중단했습니다.");
    }
    void startWorkerService(long generation) throws Exception {
        synchronized(this){checkWorkerStart(generation);}
        context.startForegroundService(new Intent(context,AgentForegroundService.class));
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        synchronized(this){
            while(activeForegroundService==null||!serviceAcknowledged||expectedForegroundStops.contains(activeForegroundService)){
                checkWorkerStart(generation);long remaining=deadline-System.nanoTime();
                if(remaining<=0)throw new IllegalStateException("독립 작업 알림 서비스를 시작하지 못했습니다.");
                TimeUnit.NANOSECONDS.timedWait(this,remaining);
            }
            checkWorkerStart(generation);
        }
    }
    synchronized void endWorkerStart(){pendingWorkerStarts=Math.max(0,pendingWorkerStarts-1);releaseForegroundIfIdle();}''')
patch('AgentRuntime.java','    void stopAll(){\n        jobs.cancelAll();stop.set(true);','    synchronized void stopAll(){\n        stop.set(true);workerStopGeneration++;notifyAll();jobs.cancelAll();')
patch('RuntimeJobs.java','''        runtime.startWorkerService();
        try{return target.submit(task,session,"worker:read-only",15*60*1000,control->WorkerAgent.run(control,task,suppliedContext,config,token,readTools(control)));}
        catch(Exception e){runtime.privateJobsChanged();throw e;}''','''        long generation=runtime.beginWorkerStart();
        try{
            runtime.startWorkerService(generation);
            synchronized(runtime){
                runtime.checkWorkerStart(generation);
                return target.submit(task,session,"worker:read-only",15*60*1000,control->WorkerAgent.run(control,task,suppliedContext,config,token,readTools(control)));
            }
        }finally{runtime.endWorkerStart();}''')
p=ROOT/'tests/jvm/DirectAgentHarness.java';s=p.read_text();assert 'length()==41,' in s;s=s.replace('LocalCapabilities.schemas(new JSONObject()).length()==41,','LocalCapabilities.schemas(new JSONObject()).length()==41+ExtensionSchemas.all().length(),');p.write_text(s)
print('Worker foreground ownership, Stop ordering, cancellation unwind, token budget and durable-write recovery hardened.')
