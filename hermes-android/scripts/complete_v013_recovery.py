"""Apply reviewed v0.13 recovery fixes once; checks all anchors before changing files."""
from pathlib import Path
R=Path(__file__).resolve().parents[1]
S=R/'app/src/main/java/dev/chanho/hermes'
A=R/'app/src/main/assets'
patches=[
(S/'PhoneAccessibilityService.java','if(snapshotId.isEmpty()||!args.optString("snapshot").equals(snapshotId)||SystemClock.elapsedRealtime()-capturedAt>45000)throw new IllegalStateException("화면 정보가 만료되었습니다. 다시 조회하세요.");','ObservationRequired.checkSnapshot(args.optString("snapshot"),snapshotId,SystemClock.elapsedRealtime()-capturedAt);'),
(S/'PhoneAccessibilityService.java','throw new IllegalStateException("대상 화면이 바뀌었습니다. 화면을 다시 조회하세요.");','throw ObservationRequired.changed();'),
(S/'DeviceTools.java','}catch(Exception e){runtime.store.audit(name,"failed",e.getClass().getSimpleName());return J.obj("ok",false,"error",J.error(e));}','}catch(ObservationRequired changed){runtime.store.audit(name,"failed","새 화면 관찰 필요 · 이전 좌표 재사용 금지");return changed.result();}\n        catch(Exception e){runtime.store.audit(name,"failed",e.getClass().getSimpleName());return J.obj("ok",false,"error",J.error(e));}'),
(S/'AgentRuntime.java','}catch(Exception e){if(!cancelled()&&unexpectedFailure(e))Diagnostics.record(context,"tool",e);','}catch(ObservationRequired changed){recordToolActivity(active,"failed","화면 정보 갱신 필요 · 이전 대상 자동 재시도 안 함");return changed.result();}\n        catch(Exception e){if(!cancelled()&&unexpectedFailure(e))Diagnostics.record(context,"tool",e);'),
(S/'MainActivity.java','"retained",runtime.busy()||runtime.terminalHasJobs()','"retained",runtime.anyWork()'),
(A/'cli.js',"const jobUi={state:{active:0,jobs:[]},view:'',selected:'',refresh:0};","const jobUi={state:{active:0,jobs:[]},view:'',selected:'',refresh:0,revision:0};"),
(A/'cli.js',"jobUi.state=data;const button=$('privateJobsButton');","jobUi.state=JSON.parse(JSON.stringify(data));jobUi.revision++;const button=$('privateJobsButton');"),
(A/'cli.js',"try{await native('cancelJob',{job_id:job.id});await refreshPrivateJobs();}","try{const response=await native('cancelJob',{job_id:job.id});if(response.job?.id===job.id)updatePrivateJobs({...jobUi.state,jobs:jobUi.state.jobs.map(item=>item.id===job.id?response.job:item)});await refreshPrivateJobs();}"),
(A/'cli.js',"async function refreshPrivateJobs(){const sequence=++jobUi.refresh;const data=await native('jobs');if(sequence===jobUi.refresh)updatePrivateJobs(data);}","async function refreshPrivateJobs(){const sequence=++jobUi.refresh,revision=jobUi.revision;const data=await native('jobs');if(sequence===jobUi.refresh&&revision===jobUi.revision)updatePrivateJobs(data);}"),
(A/'app.css','body[data-transcript="cli"] .message-label{font-family:','body[data-transcript="cli"] .message-label{display:flex;align-items:center;gap:7px;margin-bottom:7px;font-family:'),
(A/'app.js',"label.append(node('span','','Hermes'));card.append(label,node('div','message-content'));","label.append(node('span','','hermes ›'));card.append(label,node('div','message-content'));"),
(R/'tests/run_jvm_tests.py',"'ToolArgs.java', 'ExtensionSchemas.java'","'ToolArgs.java', 'ObservationRequired.java', 'ExtensionSchemas.java'")
]
content={}
for path,old,new in patches:
 text=content.get(path,path.read_text())
 if text.count(old)!=1:raise RuntimeError(f'{path.name}: expected one anchor, got {text.count(old)}: {old[:65]}')
 content[path]=text.replace(old,new)
for path,text in content.items():path.write_text(text,encoding='utf-8')
print(f'Applied recovery fixes to {len(content)} files. No device permissions or snapshot expiry were loosened.')
