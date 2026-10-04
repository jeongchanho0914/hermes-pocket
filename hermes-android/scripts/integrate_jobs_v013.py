from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];SRC=ROOT/'app/src/main/java/dev/chanho/hermes'
def change(file,old,new,count=1):
 p=SRC/file;s=p.read_text();n=s.count(old)
 if n!=count:raise RuntimeError(f'{file}: expected {count} anchors, got {n}: {old[:80]}')
 p.write_text(s.replace(old,new),encoding='utf-8')
# Share schema data without coupling host contract tests to Android runtime classes.
p=SRC/'RuntimeJobs.java';s=p.read_text();begin=s.index('    static JSONArray schemas(){');end=s.index('    private TaskPool requirePool()',begin)
method=s[begin:end].replace('static JSONArray schemas()','static JSONArray jobs()')
(SRC/'ExtensionSchemas.java').write_text('package dev.chanho.hermes;\nimport org.json.*;\nimport java.util.*;\nfinal class ExtensionSchemas {\n    static boolean handles(String name){return Arrays.asList("delegate_task","task_list","task_result","task_cancel","todo").contains(name);}\n'+method+'    static JSONArray all(){return jobs();}\n}\n')
p.write_text(s[:begin]+'    static JSONArray schemas(){return ExtensionSchemas.jobs();}\n'+s[end:])
change('RuntimeJobs.java','runtime.getCurrentState().sessionId','runtime.currentState().sessionId',2)
change('WorkerAgent.java','ModelLimits.Limits limits=ModelLimits.resolve(config);','ModelLimits limits=ModelLimits.resolve(config);')
change('WorkerAgent.java','Math.min(8192,limits.output)','limits.responseReserve()')
change('DirectAgent.java','private static void outputBudget(','static void outputBudget(')
# A cancelled future stays a foreground-service owner until its runnable has actually unwound.
change('TaskPool.java','''                    active.remove(id);futures.remove(id);listener.changed();
                }
            }
        };''','''                    listener.changed();
                }
            }
            @Override public void run(){
                try{super.run();}
                finally{active.remove(id);futures.remove(id);listener.changed();}
            }
        };''')
change('AgentRuntime.java','    final PhoneFiles phoneFiles;','    final PhoneFiles phoneFiles;\n    final RuntimeJobs jobs;')
change('AgentRuntime.java','phoneFiles=new PhoneFiles(this);','phoneFiles=new PhoneFiles(this);jobs=new RuntimeJobs(this);')
change('AgentRuntime.java','    boolean terminalHasJobs(){return terminalTools.hasRunningJobs();}', '''    boolean terminalHasJobs(){return terminalTools.hasRunningJobs();}
    boolean anyWork(){return busy()||terminalHasJobs()||jobs.hasActive();}
    void startWorkerService(){context.startForegroundService(new Intent(context,AgentForegroundService.class));}
    void privateJobsChanged(){emit("jobs",jobs.state());releaseForegroundIfIdle();}
''')
change('AgentRuntime.java','if(busy()||terminalHasJobs()||activeForegroundService==null)return;','if(anyWork()||activeForegroundService==null)return;')
change('AgentRuntime.java','    void stopAll(){stop.set(true);','    void stopAll(){jobs.cancelAll();stop.set(true);')
change('AgentRuntime.java','        if(BrowserSearch.handles(name))','        if(RuntimeJobs.handles(name))return jobs.execute(name,args);\n        if(BrowserSearch.handles(name))')
change('AgentRuntime.java','"terminal",terminalTools.state(),"shizuku"','"terminal",terminalTools.state(),"jobs",jobs.state(),"shizuku"')
change('Store.java','    void put(String key,String value){prefs.edit().putString(key,value).apply();}', '''    void put(String key,String value){prefs.edit().putString(key,value).apply();}
    synchronized void putDurable(String key,String value){if(!prefs.edit().putString(key,value).commit())throw new IllegalStateException("로컬 상태를 저장하지 못했습니다.");}''')
change('Store.java','prefs.edit().remove("compact:"+sid).apply();','prefs.edit().remove("compact:"+sid).remove("plan:"+sid).apply();')
change('LocalCapabilities.java','    static String pluginFor(String tool){','    static String pluginFor(String tool){\n        if(ExtensionSchemas.handles(tool))return "agent";')
change('LocalCapabilities.java','        for(int i=0;i<all.length();i++){JSONObject schema=all.getJSONObject(i);','        JSONArray extra=ExtensionSchemas.all();for(int i=0;i<extra.length();i++)all.put(extra.get(i));\n        for(int i=0;i<all.length();i++){JSONObject schema=all.getJSONObject(i);')
change('AgentForegroundService.java','if("terminal".equals(type)||','if("jobs".equals(type)||"terminal".equals(type)||')
change('AgentForegroundService.java','String summary=runtime.terminalHasJobs()?runtime.terminalStatus():"사용자가 시작한 작업을 실행 중입니다. 언제든 중단할 수 있습니다.";','String summary=runtime.jobs.hasActive()?"독립 작업 "+runtime.jobs.activeCount()+"개 · 알림에서 모두 중단할 수 있습니다.":runtime.terminalHasJobs()?runtime.terminalStatus():"사용자가 시작한 작업을 실행 중입니다. 언제든 중단할 수 있습니다.";')
change('AgentForegroundService.java','if(!expected&&(runtime.busy()||runtime.terminalHasJobs()))','if(!expected&&runtime.anyWork())')
# Mutating settings while a detached worker is active must not redirect its authorization/config.
p=SRC/'MainActivity.java';s=p.read_text();s=s.replace('if(runtime.busy())throw new IllegalStateException','if(runtime.anyWork())throw new IllegalStateException')
s=s.replace('case "startChat":','''case "jobs":result=runtime.jobs.state();break;
                        case "startBackgroundTask":result=J.obj("job_id",runtime.jobs.startUserTask(args.getString("task"),args.optString("context",""),args.optString("session","")),"jobs",runtime.jobs.state());break;
                        case "jobResult":result=runtime.jobs.ownerResult(args.getString("job_id"));break;
                        case "cancelJob":result=runtime.jobs.ownerCancel(args.getString("job_id"));break;
                        case "plan":result=new JSONArray(runtime.store.get("plan:"+args.optString("session"),"[]"));break;
                        case "startChat":''')
p.write_text(s)
p=ROOT/'tests/run_jvm_tests.py';s=p.read_text();old="'WebTools.java', 'J.java'";assert old in s;s=s.replace(old,"'WebTools.java', 'J.java', 'ToolArgs.java', 'ExtensionSchemas.java', 'TaskLedger.java', 'TaskPool.java', 'WorkerAgent.java'");p.write_text(s)
print('Independent worker runtime, cancellation, durable ledger and service ownership integrated.')
