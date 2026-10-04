from pathlib import Path
R=Path(__file__).resolve().parents[1];A=R/'app/src/main/assets';S=R/'app/src/main/java/dev/chanho/hermes'
def replace(path,old,new,count=1):
 s=path.read_text();assert s.count(old)==count,(path,s.count(old),old[:70]);path.write_text(s.replace(old,new),encoding='utf-8')
replace(A/'index.html','<script defer src="app.js"></script>','<script defer src="app.js"></script><script defer src="cli.js"></script>')
replace(A/'app.js',"        case 'terminal':updateTerminalState(d);break;","        case 'terminal':updateTerminalState(d);break;\n        case 'jobs':if(typeof updatePrivateJobs==='function')updatePrivateJobs(d);break;")
replace(A/'app.js',"if(s.terminal)updateTerminalState(s.terminal);","if(s.terminal)updateTerminalState(s.terminal);if(s.jobs&&typeof updatePrivateJobs==='function')updatePrivateJobs(s.jobs);")
replace(A/'app.js',"button.disabled=!state.busy&&(!hasConfiguredModel()||!$('messageInput').value.trim());","button.disabled=!state.busy&&(!hasConfiguredModel()&&!(typeof isCliInput==='function'&&isCliInput($('messageInput').value))||!$('messageInput').value.trim());")
replace(A/'app.js',"async function send(){const text=$('messageInput').value.trim();if(!text||state.busy)return;","async function send(){const text=$('messageInput').value.trim();if(!text)return;if(typeof handleCliCommand==='function'&&await handleCliCommand(text))return;if(state.busy)return;")
replace(A/'app.js',"m.role==='user'?'나':m.role==='assistant'?'Hermes':'실행 안내'","m.role==='user'?'you ›':m.role==='assistant'?'hermes ›':'[notice]'")
replace(A/'app.js',"$('resultBody').textContent=JSON.stringify(value,null,2);","$('resultBody').classList.remove('cli-output');$('resultBody').textContent=JSON.stringify(value,null,2);")
replace(A/'cli.js',"case 'skills':showPage('memory');break;","case 'skills':showPage('settings');openSettingsPanel('Skills');break;")
with (A/'cli.js').open('a') as f:f.write("\nfunction isCliInput(text){return /^\\/(help|status|jobs|bg|result|cancel|stop|plan|tools|skills|memory|compact|new)(?:\\s|$)/.test(text.trim());}\nupdateSend();\n")
with (A/'app.css').open('a') as f:f.write('''\n/* Capability-first transcript: preserve timeline and Markdown; no decorative streaming work. */
body[data-transcript="cli"] .message{border-radius:0;box-shadow:none;background:transparent;padding-top:12px;padding-bottom:12px}
body[data-transcript="cli"] .message-label{font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:11px;letter-spacing:.035em}
body[data-transcript="cli"] .message-content{line-height:1.65}
body[data-transcript="cli"] .chat-activity{border-radius:5px;border-left:2px solid var(--line);box-shadow:none;padding:9px 12px;margin-block:7px}
body[data-transcript="cli"] .chat-activity-head{font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:11px}
#privateJobsButton{white-space:nowrap;min-width:42px}
.cli-output{white-space:pre-wrap;overflow-wrap:anywhere;font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:12px;line-height:1.65}
.cli-output .cli-job-form,.cli-output .cli-job-row,.cli-output .cli-job-result,.cli-output .cli-job-actions{white-space:normal}
.cli-job-form{display:grid;gap:10px;margin:12px 0}
.cli-job-form textarea{width:100%;min-height:78px;padding:10px;box-sizing:border-box;font:inherit;border:1px solid var(--line);border-radius:6px;color:inherit;background:transparent}
.cli-job-row{padding:14px 0;border-bottom:1px solid var(--line)}
.cli-job-row strong,.cli-job-row small{display:block}
.cli-job-row small{font-size:10px;opacity:.65}
.cli-job-actions{display:flex;flex-wrap:wrap;gap:8px;margin:10px 0}
.cli-job-actions button,.cli-job-form button{min-height:42px;padding:8px 12px}
.cli-job-notice{font-size:12px;opacity:.75}
.cli-job-meta{font-size:11px;opacity:.8;margin-bottom:12px}
''')
replace(S/'AgentOverlay.java','private boolean collapsed=false,editing=false','private boolean collapsed=true,editing=false')
replace(S/'AgentOverlay.java','if(type.equals("started")){collapsed=false;dismissedForRun=false;}','if(type.equals("started")){collapsed=true;dismissedForRun=false;}')
replace(S/'AgentOverlay.java','status=!state.busy&&runtime.terminalHasJobs()?runtime.terminalStatus():state.status;','status=!state.busy&&runtime.jobs.hasActive()?"독립 작업 "+runtime.jobs.activeCount()+"개":!state.busy&&runtime.terminalHasJobs()?runtime.terminalStatus():state.status;')
replace(S/'AgentOverlay.java','(!state.ownerTask&&!runtime.terminalHasJobs())||state.cancelled','(!state.ownerTask&&!runtime.terminalHasJobs()&&!runtime.jobs.hasActive())||(state.cancelled&&!runtime.jobs.hasActive())')
replace(S/'AgentOverlay.java','boolean jobs=runtime.terminalHasJobs();','boolean jobs=runtime.terminalHasJobs()||runtime.jobs.hasActive();')
replace(S/'AgentOverlay.java','params.width=dp(collapsed?256:312);','params.width=dp(collapsed?256:280);')
replace(S/'AgentOverlay.java','new WindowManager.LayoutParams(dp(312),','new WindowManager.LayoutParams(dp(280),')
replace(S/'AgentOverlay.java','statusView.setMaxLines(2);statusView.setMinHeight(dp(38));','statusView.setMaxLines(1);statusView.setMinHeight(dp(28));')
replace(S/'AgentOverlay.java','panel.addView(statusLabel);','')
# Remove one redundant open button; the title opens the app, while close and Stop remain visible.
old='Button open=button("열기","Hermes 앱 열기");open.setOnClickListener(v->{finishEditing();context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));detach();});addControl(header,open);'
new='drag.setOnClickListener(v->{finishEditing();context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));detach();});'
replace(S/'AgentOverlay.java',old,new)
replace(S/'DirectAgent.java','Before each tool batch, provide one brief public sentence','''Use a compact CLI-like communication style: state the actual operation briefly, execute real tools, then report verified results, failures and unresolved work. Do not fabricate terminal output, progress percentages or hidden reasoning. For multi-step work use todo for a small evidence-based checklist, never as a scheduler. If delegate_task is available, use independent read-only workers only when useful for nontrivial analysis or writing, supply relevant context explicitly, then collect actual task_result output before claiming completion. Private workers cannot control the device or browser, execute code, or recursively delegate. Screen actions stay on the current single-writer device lane. Every worker uses the owner's model API and can incur additional cost; do not spawn workers for trivial requests. Browser page reading defaults to the same real phone browser: use browser_open or web_fetch without mode api, then browser_snapshot/read_screen. A requested URL and browserForegroundObserved do not establish that the requested page has loaded or that the user goal succeeded. Distinguish visible-page evidence from full-page extraction. Before each tool batch, provide one brief public sentence''')
print('CLI command UI, independent job controls and compact overlay integrated without changing gesture safety guards.')
