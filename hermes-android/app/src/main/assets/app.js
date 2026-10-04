'use strict';
const $ = id => document.getElementById(id);
const state={page:'chat',sid:'',messages:[],activities:[],providerThoughts:[],modelProgress:null,sessions:[],audit:[],config:{mode:'direct',endpoint:'',deviceScope:'all',approvalMode:'ask',floatingEnabled:true},device:{},busy:false,live:'',activeRunId:'',progressStatus:'',historyTab:'sessions',connected:false};
const terminalUi={request:false,requestSeq:0,statusLoading:false,jobs:[]};
let skillPackageBusy=false,skillResourceBusy=false,skillResourceBinary=false;
let modelProgressTimer=null;
let conversationTimeline=[],timelineSession=null,liveOffset=0;
const skillLibraryUi={items:[],selected:null,loading:false,reading:false,requestSeq:0};
const pending=new Map();let serial=0,toastTimer;let followLatest=true,sidebarFocus,streamingTimer=null,lastStreamingPaint=0;
let browserProblemReported=0;
function reportBrowserProblem(code,event){
    if(!window.NativeBridge||Date.now()-browserProblemReported<1000)return;browserProblemReported=Date.now();
    const source=typeof event?.filename==='string'&&event.filename.split(/[\\/]/).pop().split('?')[0]==='index.html'?'index.html':'app.js';
    const coordinate=n=>Number.isInteger(n)&&n>=0&&n<=1000000?n:0;
    native('recordBrowserProblem',{code,source,line:coordinate(event?.lineno),column:coordinate(event?.colno)}).catch(()=>{});
}
window.addEventListener('error',event=>reportBrowserProblem('webview_error',event));
window.addEventListener('unhandledrejection',()=>reportBrowserProblem('unhandled_rejection'));
const icons={chat:'<circle cx="12" cy="11" r="8"/><path d="M7 18l-3 3v-7"/><path d="M8 11h8"/>',device:'<rect x="6" y="2" width="12" height="20" rx="3"/><path d="M10 18h4"/>',history:'<circle cx="12" cy="12" r="8"/><path d="M12 7v6l4 2"/>',memory:'<path d="M12 5C8 2 4 3 3 5v15c4-2 6-2 9 0 3-2 5-2 9 0V5c-1-2-5-3-9 0zm0 0v15"/>',settings:'<path d="M4 6h16M4 12h16M4 18h16"/><circle cx="8" cy="6" r="2"/><circle cx="16" cy="12" r="2"/><circle cx="9" cy="18" r="2"/>'};
function svg(name){return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+icons[name]+'</svg>';}
for(const b of document.querySelectorAll('[data-page]'))b.querySelector('.nav-icon').outerHTML=svg(b.dataset.page);

function native(method,data={}){
    return new Promise((resolve,reject)=>{
        if(!window.NativeBridge){reject(new Error('브라우저 미리보기입니다. 실제 기기 기능은 Android APK에서 사용할 수 있습니다.'));return;}
        const id='r'+(++serial)+'_'+Date.now();const timer=setTimeout(()=>{pending.delete(id);reject(new Error('앱 요청 시간이 초과되었습니다. 진행 중인 작업을 확인하세요.'));},190000);
        pending.set(id,{resolve,reject,timer});window.NativeBridge.postMessage(JSON.stringify({id,method,data}));
    });
}
window.PocketNative = message=>{
    if(message.id){const p=pending.get(message.id);if(p){clearTimeout(p.timer);pending.delete(message.id);message.ok?p.resolve(message.data):p.reject(new Error(message.data?.message||'요청이 실패했습니다.'));}return;}
    const d=message.data||{};
    switch(message.event){
        case 'started':cancelStreamingRender();clearSubmittedDraft(d.text);lastStreamingPaint=0;state.sid=d.session;stopModelProgressTimer();state.modelProgress=null;state.activeRunId=d.runId||'';state.progressStatus=d.status||'요청을 시작했습니다.';state.messages.push({role:'user',content:d.text,created:Number(d.created)||Date.now(),runId:d.runId||'',timelineOrder:Number(d.timelineOrder)||0,id:d.messageId});state.live='';state.liveTimelineOrder=0;liveOffset=0;state.busy=true;followLatest=true;renderConversation();setBusy(true);scrollBottom(true);break;
        case 'delta':if(d.session&&d.session!==state.sid||d.runId&&d.runId!==state.activeRunId)break;state.live+=d.text||'';state.liveTimelineOrder=Number(d.timelineOrder)||state.liveTimelineOrder||0;ensureStreamEntry();scheduleStreamingRender();break;
        case 'status':state.progressStatus=d.message||'실행 중';$('runStatusText').textContent=state.progressStatus;if(state.busy&&state.page==='chat')renderStreaming();break;
        case 'tool':state.progressStatus=(agentToolLabels[d.name]||d.name)+' · '+d.status;$('runStatusText').textContent=state.progressStatus;if(state.busy&&state.page==='chat')renderStreaming();break;
        case 'notice':toast(d.message,7500);$('runStatusText').textContent=d.message;break;
        case 'complete':if(d.session&&d.session!==state.sid||d.runId&&d.runId!==state.activeRunId)break;cancelStreamingRender();state.messages.push({role:'assistant',content:d.text||'',created:Number(d.created)||Date.now(),runId:d.runId||state.activeRunId,timelineOrder:Number(d.timelineOrder)||0,id:d.messageId});commitStreamEntry('message',state.messages[state.messages.length-1],state.messages.length-1);state.live='';state.liveTimelineOrder=0;state.busy=false;setBusy(false);renderConversation();scrollBottom();break;
        case 'failure':if(d.session&&d.session!==state.sid||d.runId&&d.runId!==state.activeRunId)break;cancelStreamingRender();if(d.partialMessage){state.messages.push(d.partialMessage);commitStreamEntry('message',d.partialMessage,state.messages.length-1);}else if(state.live){const partial={role:'assistant',content:'[미완료 응답 · 실행 완료를 의미하지 않습니다]\n'+state.live,created:Date.now()};state.messages.push(partial);commitStreamEntry('message',partial,state.messages.length-1);}state.messages.push(d.errorMessage||{role:'error',content:d.message,created:Number(d.created)||Date.now(),runId:d.runId||state.activeRunId,timelineOrder:Number(d.timelineOrder)||0,id:d.messageId});state.live='';state.liveTimelineOrder=0;state.busy=false;setBusy(false);renderConversation();scrollBottom();break;
        case 'settled':stopModelProgressTimer();state.sessions=d.sessions||state.sessions;state.audit=d.audit||state.audit;updateDevice(d.device||{});renderHistory();break;
        case 'device':updateDevice(d);break;
        case 'floating':updateFloating(d);break;
        case 'terminal':updateTerminalState(d);break;
        case 'jobs':if(typeof updatePrivateJobs==='function')updatePrivateJobs(d);break;
        case 'activity':updateChatActivity(d);break;
        case 'providerThought':updateProviderThought(d);break;
        case 'modelProgress':updateModelProgress(d);break;
        case 'files':updateFilesStatus(d);break;
        case 'shizuku':updateShizuku(d);break;
    }
};
const toolNames={get_device_state:'기기 상태 조회',list_apps:'앱 목록 조회',launch_app:'앱 실행',open_settings:'설정 페이지 열기',set_volume:'미디어 볼륨 변경',set_brightness:'화면 밝기 변경',root_processes:'프로세스 목록 조회',set_wifi:'Wi-Fi 변경',force_stop_app:'일반 앱 강제 종료',read_screen:'현재 앱 화면 요소 읽기',capture_screen:'현재 앱 화면 이미지 읽기',click_element:'화면 요소 클릭',type_text:'입력란에 텍스트 입력',scroll_element:'화면 요소 스크롤',press_back:'뒤로 가기',press_home:'홈으로 가기',tap_screen:'화면 좌표 누르기',swipe_screen:'화면 좌표 쓸기'};
const screenSnapshotTools=['click_element','type_text','scroll_element','press_back','press_home','tap_screen','swipe_screen'];
const agentToolLabels={...toolNames,terminal:'터미널 명령 실행',process_manage:'실행 프로세스 관리',open_link:'링크 열기',open_map:'지도 열기',share_text:'텍스트 공유 화면 열기',compose_message:'메시지 작성 화면 열기',set_alarm:'알람 설정 화면 열기',long_click_element:'화면 요소 길게 누르기',set_element_progress:'화면 값 조절',perform_phone_action:'휴대폰 기본 동작',memory:'메모리 관리',session_search:'대화 기록 검색',skills_list:'저장된 스킬 목록',skill_view:'스킬 읽기',skill_manage:'스킬 관리',web_search:'웹 검색',web_fetch:'웹 페이지 읽기',phone_list_files:'선택한 폴더 목록',phone_read_file:'선택한 폴더 파일 읽기',phone_write_file:'선택한 폴더 파일 쓰기',probe_root:'Root 실제 확인',privileged_status:'Root·Shizuku 상태',get_phone_settings:'휴대폰 설정 읽기',set_phone_setting:'휴대폰 설정 변경'};
function toast(text,ms=4500){clearTimeout(toastTimer);$('toast').textContent=text;$('toast').classList.remove('hidden');toastTimer=setTimeout(()=>$('toast').classList.add('hidden'),ms);}
function setSidebar(open){
    if(open)sidebarFocus=document.activeElement;
    document.body.dataset.sidebar=open?'open':'closed';$('menuToggle').setAttribute('aria-expanded',String(open));
    const desktop=false;
    $('sidebar').inert=!desktop&&!open;
    if(open&&!desktop)$('closeSidebar').focus();
    if(!open&&sidebarFocus&&sidebarFocus.isConnected&&!desktop)sidebarFocus.focus();
}
function showPage(page){
    if(!['chat','device','permissions','history','memory','settings'].includes(page))return;
    state.page=page;state.returnToSettings=false;delete document.body.dataset.settingsSubpage;document.body.dataset.page=page;closeSettingsDetail(false);setSidebar(false);
    for(const e of document.querySelectorAll('.page'))e.classList.toggle('active',e.id==='page-'+page);
    for(const b of document.querySelectorAll('[data-page]'))b.classList.toggle('active',b.dataset.page===page);
    $('headerTitle').textContent={chat:'Hermes',device:'기기 도구',permissions:'기기 권한',history:'기록',memory:'메모리',settings:'설정'}[page];
    $('composerWrap').classList.toggle('hidden',page!=='chat');
    $('scrollToLatest').classList.add('hidden');$('main').scrollTop=0;
    if(page==='chat'){if(state.busy)renderStreaming();scrollBottom(true);}if(page==='history')refreshHistory();if(page==='memory')loadMemoryDocuments();if(page==='device'||page==='permissions')native('device').then(updateDevice).catch(()=>{});
}
window.PocketBack=()=>{if(closeActiveSheet())return;if(!$('resultModal').classList.contains('hidden'))closeResult();else if(document.body.dataset.sidebar==='open')setSidebar(false);else if(state.page==='settings'&&state.settingsPanel)closeSettingsDetail();else if(state.returnToSettings)backToSettings();else if(state.page!=='chat')showPage('chat');else native('quit').catch(()=>{});};
for(const b of document.querySelectorAll('[data-page]'))b.addEventListener('click',()=>showPage(b.dataset.page));
$('menuToggle').addEventListener('click',()=>{if(state.returnToSettings){backToSettings();return;}if(state.page==='settings'&&state.settingsPanel){closeSettingsDetail();return;}setSidebar(document.body.dataset.sidebar!=='open');if(window.NativeBridge)refreshHistory();});
$('closeSidebar').addEventListener('click',()=>setSidebar(false));$('sidebarScrim').addEventListener('click',()=>setSidebar(false));
document.addEventListener('keydown',e=>{
    if(e.key==='Escape'){if(closeActiveSheet()){e.preventDefault();return;}if(!$('resultModal').classList.contains('hidden'))closeResult();else if(state.page==='settings'&&state.settingsPanel)closeSettingsDetail();else if(state.returnToSettings)backToSettings();else setSidebar(false);}
    if(e.key!=='Tab'||document.body.dataset.sidebar!=='open'||!$('resultModal').classList.contains('hidden'))return;
    const targets=[...$('sidebar').querySelectorAll('button:not(:disabled),input')].filter(e=>e.getClientRects().length);const first=targets[0],last=targets[targets.length-1];
    if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}
});
setSidebar(false);

for(const id of ['inlineSetup'])$(id).addEventListener('click',()=>{showPage('settings');openSettingsPanel('Connection');});
$('composerTools').addEventListener('click',openCapabilities);
$('composerConnection').addEventListener('click',openModels);$('reasoningPicker').addEventListener('click',openReasoning);
function hasConfiguredModel(){if(!state.config.model?.trim()||!state.config.endpoint)return false;const provider=state.config.providerId||providerForEndpoint(state.config.endpoint);return provider==='custom'||!!state.config.hasToken;}
function updateSend(){const button=$('sendMessage');button.disabled=!state.busy&&(!hasConfiguredModel()&&!(typeof isCliInput==='function'&&isCliInput($('messageInput').value))||!$('messageInput').value.trim());button.setAttribute('aria-label',state.busy?'중단':'전송');button.dataset.action=state.busy?'stop':'send';button.innerHTML=state.busy?'<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="7" y="7" width="10" height="10" rx="2"/></svg>':'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 19V5m-6 6 6-6 6 6"/></svg>';}
function setBusy(value){if(!value)stopModelProgressTimer();if(value)activeSidebarGesture?.cancel();state.busy=value;$('runStatus').classList.toggle('hidden',!value);$('newChat').disabled=value;for(const id of ['composerTools','composerConnection','reasoningPicker','capabilityApply','refreshModels'])$(id).disabled=value;for(const e of document.querySelectorAll('#settingsForm input,#settingsForm select,#settingsForm button,#settingsPanelAdvanced input,#settingsPanelAdvanced button,#webSettingsForm input,#webSettingsForm select,#webSettingsForm button,#skillEditorForm input,#skillEditorForm textarea,#skillEditorForm button,#capabilitySheet input,#modelSheet input,#modelSheet select,#modelResults button,#reasoningLevels button'))e.disabled=value;$('newSkill').disabled=value;$('refreshSkills').disabled=value;$('headerNewChat').disabled=value;$('messageInput').disabled=value;updateSend();syncComposerChoices();updateCapabilityDraft();renderToolInventory();renderFilesStatus();renderDevicePreferences();renderShizuku();renderTerminal();renderSkillPackageControls();if(!value)$('runStatusText').textContent='연결 중';}
function time(value){return new Date(value||Date.now()).toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit'});}
function date(value){return new Date(value).toLocaleString('ko-KR',{month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'});}
function node(tag,className,text){const e=document.createElement(tag);if(className)e.className=className;if(text!==undefined)e.textContent=text;return e;}
function renderText(target,text){renderChatMarkdown(target,text);}
function messageNode(m,streaming=false){
    const article=node('article','message '+m.role+(streaming?' streaming':''));if(streaming)article.id='streamingMessage';
    const label=node('div','message-label');label.append(node('span','',m.role==='user'?'you ›':m.role==='assistant'?'hermes ›':'[notice]'));
    const content=node('div','message-content');renderText(content,m.content);article.append(label,content);
    if(!streaming&&m.content){const actions=node('div','message-actions');const copy=node('button','copy-message','복사');copy.type='button';copy.setAttribute('aria-label','메시지 복사');copy.addEventListener('click',()=>copyMessage(m.content));actions.append(copy);article.append(actions);}
    return article;
}
async function copyMessage(text){
    try{if(navigator.clipboard&&window.isSecureContext)await navigator.clipboard.writeText(text);else{
        const input=node('textarea','clipboard-helper');input.value=text;input.setAttribute('readonly','');document.body.append(input);input.select();const ok=document.execCommand('copy');input.remove();if(!ok)throw new Error('copy unavailable');
    }toast('메시지를 복사했습니다.');}catch(_){toast('복사하지 못했습니다. 메시지 텍스트를 길게 눌러 선택하세요.');}
}
function messageTimelineKey(m,index){return 'message:'+(m.id??m.messageId??index);}
function timelineEntryKey(kind,value,index){return kind==='message'?messageTimelineKey(value,index):kind==='activity'?'activity:'+chatActivityKey(value):'thought:'+providerThoughtKey(value);}
function appendTimelineEntry(kind,value,index){const key=timelineEntryKey(kind,value,index),existing=conversationTimeline.find(e=>e.key===key);if(existing){existing.value=value;return existing;}const entry={kind,key,value};conversationTimeline.push(entry);return entry;}
function ensureStreamEntry(){if(!state.live)return;let entry=conversationTimeline.find(e=>e.kind==='stream');if(!entry){entry={kind:'stream',key:'stream:'+state.activeRunId+':'+(state.liveTimelineOrder||conversationTimeline.length),value:{timelineOrder:state.liveTimelineOrder}};conversationTimeline.push(entry);}return entry;}
function commitStreamEntry(kind,value,index){const at=conversationTimeline.findIndex(e=>e.kind==='stream');if(at<0)return appendTimelineEntry(kind,value,index);const existing=conversationTimeline.findIndex(e=>e.key===timelineEntryKey(kind,value,index));if(existing>=0)conversationTimeline.splice(existing,1);conversationTimeline.splice(conversationTimeline.findIndex(e=>e.kind==='stream'),1,{kind,key:timelineEntryKey(kind,value,index),value});}
function restoreConversationTimeline(){
    // Sort only persisted first appearances. Live updates append and never reorder a card.
    timelineSession=state.sid;conversationTimeline=[];liveOffset=0;
    const entries=state.messages.map((value,index)=>({kind:'message',value,index}));
    for(const value of state.activities||[])if(value.session===state.sid)entries.push({kind:'activity',value});
    for(const value of state.providerThoughts||[])if(value.session===state.sid)entries.push({kind:'thought',value});
    const time=e=>Number(e.value.created||e.value.firstTimestamp||e.value.timestamp)||0;
    const order=e=>Number(e.value.timelineOrder)||0;
    entries.sort((a,b)=>order(a)&&order(b)?order(a)-order(b):time(a)-time(b)||(a.kind==='message'&&a.value.role==='user'?-1:b.kind==='message'&&b.value.role==='user'?1:(Number(a.value.firstSeq||a.value.seq)||0)-(Number(b.value.firstSeq||b.value.seq)||0)));
    for(const e of entries)appendTimelineEntry(e.kind,e.value,e.index);
    if(state.busy&&state.live){const entry=ensureStreamEntry();const order=Number(entry.value.timelineOrder);if(order){conversationTimeline.splice(conversationTimeline.indexOf(entry),1);const at=conversationTimeline.findIndex(e=>Number(e.value.timelineOrder)>order);conversationTimeline.splice(at<0?conversationTimeline.length:at,0,entry);}}
}
function renderConversation(){
    const scroll=$('main').scrollTop,has=state.messages.length>0||state.busy;
    $('emptyChat').classList.toggle('hidden',has);$('conversation').classList.toggle('hidden',!has);
    if(timelineSession!==state.sid||(!state.messages.length&&!state.busy)){restoreConversationTimeline();}
    for(let i=0;i<state.messages.length;i++)appendTimelineEntry('message',state.messages[i],i);
    for(const a of state.activities||[])if(a.session===state.sid)appendTimelineEntry('activity',a);
    for(const t of state.providerThoughts||[])if(t.session===state.sid)appendTimelineEntry('thought',t);
    const container=$('conversation'),streamEntry=conversationTimeline.find(e=>e.kind==='stream');if(streamEntry&&$('streamingMessage'))$('streamingMessage').dataset.timelineKey=streamEntry.key;const existing=new Map([...container.children].filter(e=>e.dataset.timelineKey).map(e=>[e.dataset.timelineKey,e])),wanted=new Set();
    let previous=null;
    for(const entry of conversationTimeline){
        const value=entry.value;if(entry.kind==='stream'&&!state.busy)continue;if(entry.kind==='message'&&value.role==='assistant'&&!value.content)continue;
        wanted.add(entry.key);let card=existing.get(entry.key);
        if(!card){card=entry.kind==='stream'?messageNode({role:'assistant',content:state.live},true):entry.kind==='message'?messageNode(value):entry.kind==='activity'?chatActivityNode(value):providerThoughtNode(value);card.dataset.timelineKey=entry.key;card.dataset.timelineOrder=Number(value.timelineOrder)||0;}
        else if(entry.kind==='activity')fillChatActivity(card,value);else if(entry.kind==='thought')fillProviderThought(card,value);
        const next=previous?previous.nextSibling:container.firstChild;if(card!==next)container.insertBefore(card,next);previous=card;
    }
    for(const [key,card] of existing)if(!wanted.has(key))card.remove();
    if(state.busy)renderStreaming();else $('streamingMessage')?.remove();if(!followLatest)$('main').scrollTop=scroll;
}
function cancelStreamingRender(){if(streamingTimer!==null){clearTimeout(streamingTimer);streamingTimer=null;}}
function scheduleStreamingRender(){if(streamingTimer!==null)return;const delay=Math.max(0,80-(performance.now()-lastStreamingPaint));streamingTimer=setTimeout(()=>{streamingTimer=null;if(!state.busy||state.page!=='chat')return;lastStreamingPaint=performance.now();renderStreaming();scrollBottom();},delay);}
function renderStreaming(){let e=$('streamingMessage');if(!e){e=messageNode({role:'assistant',content:''},true);const entry=ensureStreamEntry();if(entry)e.dataset.timelineKey=entry.key;$('conversation').append(e);}const activeStream=ensureStreamEntry();if(activeStream){e.dataset.timelineKey=activeStream.key;const at=conversationTimeline.indexOf(activeStream),next=conversationTimeline.slice(at+1).map(v=>[...$('conversation').children].find(c=>c.dataset.timelineKey===v.key)).find(Boolean);if(next&&e.nextSibling!==next)$('conversation').insertBefore(e,next);} $('emptyChat').classList.add('hidden');$('conversation').classList.remove('hidden');renderText(e.querySelector('.message-content'),state.live.slice(liveOffset).replace(/^\n\n/,'')||'');$('chatProgress')?.remove();renderModelProgressLabel();}
function scrollBottom(force=false){
    if(force)followLatest=true;if(state.page!=='chat')return;
    if(followLatest)requestAnimationFrame(()=>{if(!followLatest||state.page!=='chat')return;$('main').scrollTo({top:$('main').scrollHeight,behavior:'instant'});$('scrollToLatest').classList.add('hidden');});
    else $('scrollToLatest').classList.remove('hidden');
}
$('main').addEventListener('scroll',()=>{if(state.page!=='chat')return;const e=$('main');followLatest=e.scrollHeight-e.scrollTop-e.clientHeight<100;$('scrollToLatest').classList.toggle('hidden',followLatest||!state.messages.length);},{passive:true});
$('scrollToLatest').addEventListener('click',()=>scrollBottom(true));
async function send(){const text=$('messageInput').value.trim();if(!text)return;if(typeof handleCliCommand==='function'&&await handleCliCommand(text))return;if(state.busy)return;if(!hasConfiguredModel()){showSetupGuide();return;}persistComposerDraft(text,Date.now());setBusy(true);$('messageInput').value='';$('messageInput').style.height='28px';try{await native('startChat',{text,session:state.sid});clearSubmittedDraft(text);}catch(e){$('messageInput').value=text;persistComposerDraft(text);setBusy(false);toast(e.message);}}
$('compactConversation').addEventListener('click',async()=>{if(state.busy||!state.sid){toast('정리할 대화를 먼저 열어 주세요.');return;}closeActiveSheet(false);setBusy(true);state.progressStatus='대화를 정리하고 있습니다.';$('runStatusText').textContent=state.progressStatus;try{const result=await native('compactConversation',{session:state.sid});if(result.compacted!==true)throw new Error('대화 정리 결과를 확인하지 못했습니다.');toast('대화를 정리했습니다. 원문은 유지됩니다.');}catch(e){toast(e.message);}finally{setBusy(false);$('streamingMessage')?.remove();}});
$('sendMessage').addEventListener('click',()=>state.busy?native('stop').catch(e=>toast(e.message)):send());
$('messageInput').addEventListener('input',()=>{const t=$('messageInput');t.style.height='28px';t.style.height=Math.min(t.scrollHeight,160)+'px';persistComposerDraft(t.value);updateSend();});
$('messageInput').addEventListener('keydown',e=>{if(e.key==='Enter'&&!e.isComposing&&!e.shiftKey&&(e.ctrlKey||e.metaKey||window.matchMedia('(pointer:fine)').matches)){e.preventDefault();send();}});
$('stopRun').addEventListener('click',()=>native('stop').catch(e=>toast(e.message)));
$('backgroundRun').addEventListener('click',()=>native('background').catch(e=>toast(e.message)));
function startNewChat(){if(state.busy)return;state.sid='';state.messages=[];state.activities=[];state.providerThoughts=[];state.modelProgress=null;stopModelProgressTimer();state.live='';followLatest=true;showPage('chat');renderConversation();renderSidebarSessions();$('messageInput').value='';$('messageInput').style.height='28px';clearComposerDraft();$('messageInput').blur();updateSend();native('hideKeyboard').catch(()=>{});}
$('newChat').addEventListener('click',startNewChat);$('headerNewChat').addEventListener('click',startNewChat);
for(const button of document.querySelectorAll('[data-prompt]'))button.addEventListener('click',()=>{if(state.busy)return;$('messageInput').value=button.dataset.prompt;$('messageInput').dispatchEvent(new Event('input'));$('messageInput').focus();});
function updateDevice(d){
    state.device={...state.device,...d};const v=state.device;if(v.shizuku)updateShizuku(v.shizuku);$('deviceName').textContent=v.model||'기기 확인 전';$('deviceOs').textContent=v.android?'Android '+v.android+' · API '+v.sdk:'APK에서 실제 정보를 조회합니다';
    $('battery').textContent=v.battery>=0?v.battery+'%':'—';$('batteryBar').style.width=Math.max(0,Math.min(100,v.battery||0))+'%';$('charging').textContent=v.battery>=0?(v.charging?'충전 중':'배터리 사용 중'):'확인 전';
    $('memoryAvailable').textContent=v.memoryAvailableGb!=null?v.memoryAvailableGb+' GB':'—';$('memoryTotal').textContent=v.memoryTotalGb?'전체 '+v.memoryTotalGb+' GB':'확인 전';$('memoryBar').style.width=(v.memoryTotalGb?Math.max(0,Math.min(100,v.memoryAvailableGb/v.memoryTotalGb*100)):0)+'%';
    renderToolInventory();
    badge('accessBadge',v.accessibility,'연결됨','연결 전');badge('writeBadge',v.writeSettings,'허용됨','권한 필요');badge('rootBadge',v.root,'su UID 0 확인','Root 미확인');$('rootToggle').textContent=v.rootEnabled?'su 연동 끄기':'su 연동 켜기';renderPrivilegeSource();$('systemUid').textContent=v.systemUid?'이 프로세스의 System UID가 확인되었습니다.':'System UID는 부여되지 않았습니다.';
    if(v.floating)updateFloating(v.floating);renderScreenCapabilities();renderFloating();
    if(v.volume!=null){$('volumeRange').value=v.volume;$('volumeValue').textContent=v.volume+'%';}if(v.brightness!=null){const p=Math.max(1,Math.round(v.brightness/255*100));$('brightnessRange').value=p;$('brightnessValue').textContent=p+'%';}
}
function badge(id,on,yes,no){$(id).textContent=on?yes:no;$(id).classList.toggle('on',!!on);}
function result(title,value){resultFocus=document.activeElement;if(typeof clearScreenTargets==='function')clearScreenTargets();$('resultTitle').textContent=title;$('resultBody').classList.remove('cli-output');$('resultBody').textContent=JSON.stringify(value,null,2);$('resultModal').classList.remove('hidden');$('closeResult').focus();}
function closeResult(){$('resultModal').classList.add('hidden');if(resultFocus&&resultFocus.isConnected)resultFocus.focus();}
$('closeResult').addEventListener('click',closeResult);document.querySelector('.modal-scrim').addEventListener('click',closeResult);
async function runTool(name,args={}){if(state.busy){toast('진행 중인 작업이 끝난 뒤 실행하세요.');return;}setBusy(true);try{const response=await native('runTool',{name,arguments:args});result(toolNames[name]||name,response);if(typeof captureToolResult==='function')captureToolResult(name,response);await refreshHistory();return response;}catch(e){toast(e.message,6500);}finally{setBusy(false);}}

function shizukuPrivilegeReady(){const s=state.shizuku||{};return s.available===true&&s.enabled===true&&s.serviceConnected===true&&s.permissionGranted===true&&s.binderAlive===true&&[0,2000].includes(s.uid);}
function renderPrivilegeSource(){const v=state.device,s=state.shizuku||{};let text=v.root===true?'su로 실제 UID 0 권한을 확인했습니다.':v.rootStatus?.message||'su Root 권한은 확인되지 않았습니다.';if(shizukuPrivilegeReady())text+=' · Shizuku '+(s.uid===2000?'Shell UID 2000':'UID 0')+' 연결됨';$('rootSourceStatus').textContent=text;}
function renderShizuku(){const s=state.shizuku||{},validUid=s.uid===0||s.uid===2000,ready=s.available===true&&s.enabled===true&&s.serviceConnected===true&&s.permissionGranted===true&&s.binderAlive===true&&validUid;if(!state.shizukuSaving)$('shizukuEnabled').checked=s.enabled===true||state.config.shizukuEnabled===true;$('shizukuEnabled').disabled=state.busy||!!state.shizukuSaving||preferenceSaving;let status='연결 미확인';if(ready)status=s.uid===0?'연결됨 · UID 0':'연결됨 · Shell UID 2000';else if(s.enabled===false)status='연결 꺼짐';else if(s.installed===false)status='Shizuku 설치 필요';else if(s.binderAlive===false)status='Shizuku 서비스 시작 필요';else if(s.permissionGranted===false)status='Shizuku 권한 필요';else if(s.serviceConnected===false)status='서비스 연결 확인 전';else if(s.uid!==undefined&&!validUid)status='권한 수준 확인 전';$('shizukuStatus').textContent=status;const needsPermission=s.enabled===true&&s.installed===true&&s.binderAlive===true&&s.permissionGranted!==true;$('requestShizukuPermission').classList.toggle('hidden',!needsPermission);$('requestShizukuPermission').disabled=state.busy||!!state.shizukuSaving||preferenceSaving;}
function updateShizuku(s){state.shizuku={...s};if(typeof s.enabled==='boolean')state.config.shizukuEnabled=s.enabled;renderShizuku();renderPrivilegeSource();}
async function changeShizuku(method,data={}){if(state.busy||state.shizukuSaving||preferenceSaving){renderShizuku();return;}state.shizukuSaving=true;renderShizuku();renderDevicePreferences();try{const response=await native(method,data);if(response.config){state.config={...state.config,...response.config};renderToolInventory();}if(response.device)updateDevice(response.device);updateShizuku(response.shizuku||response);if(method==='requestShizukuPermission')toast('Shizuku의 권한 요청을 확인하세요.');}catch(e){toast(e.message,6500);}finally{state.shizukuSaving=false;renderShizuku();renderDevicePreferences();}}
$('shizukuEnabled').addEventListener('change',()=>changeShizuku('setShizuku',{enabled:$('shizukuEnabled').checked}));
$('requestShizukuPermission').addEventListener('click',()=>changeShizuku('requestShizukuPermission'));
function updateFloating(v){state.floating={...v};renderFloating();}
function renderFloating(){const f=state.floating||{},enabled=state.config.floatingEnabled!==false;let text='접근성 연결 후 상태와 메시지 입력';if(!enabled)text='꺼짐';else if(state.device.accessibility===false||f.available===false)text='접근성 권한을 연결하세요';else if(f.visible===true)text=f.collapsed?'작은 창 표시 중 · 접힘':'작은 창 표시 중';else if(state.device.accessibility===true)text='다른 앱에서 상태와 메시지 입력';$('floatingStatus').textContent=text;}
function renderScreenCapabilities(){const v=state.device;const tree=v.accessibility===true;$('screenTreeStatus').textContent=tree?'화면 요소 읽기 · 클릭·입력·스크롤':'접근성 권한 연결';const supported=v.screenshotSupported,capability=v.screenshotCapability,available=v.screenshotAvailable;let label='확인 전',text='캡처 권한 확인 전 · 화면 요소 읽기와 별도';if(supported===false){label='미지원';text='화면 이미지는 Android 11 이상에서 지원합니다';}else if(v.accessibility===false){label='연결 전';text='접근성 권한을 연결하세요';}else if(capability===false){label='재연결 필요';text='업데이트 후 접근성 서비스를 껐다 켜세요';}else if(available===true){label='권한 연결';text='필요한 이미지를 설정한 모델 API로 전달 · 보안 화면 제외';}else if(capability===true){label='연결됨';text=v.locked?'잠금을 해제하면 캡처할 수 있습니다':'보안 화면은 캡처할 수 없습니다';}$('screenCaptureBadge').textContent=label;$('screenCaptureBadge').classList.toggle('on',available===true);$('screenCaptureStatus').textContent=text;}
function renderDevicePreferences(){const blocked=state.busy||preferenceSaving||!!state.shizukuSaving;if(!preferenceSaving){$('deviceScopeAll').checked=state.config.deviceScope==='all';$('approvalMode').value=state.config.approvalMode==='auto'?'auto':'ask';$('floatingEnabled').checked=state.config.floatingEnabled!==false;}$('deviceScopeAll').disabled=blocked;$('approvalMode').disabled=blocked;$('floatingEnabled').disabled=blocked;renderFloating();}
async function saveDevicePreference(p){if(state.busy||preferenceSaving||state.shizukuSaving){renderDevicePreferences();return;}$('devicePreferencesStatus').textContent='설정 저장 중…';const saved=await savePreferences(p);renderDevicePreferences();$('devicePreferencesStatus').textContent=saved?'설정을 저장했습니다.':'저장하지 못했습니다. 이전 설정을 유지합니다.';}
$('deviceScopeAll').addEventListener('change',()=>saveDevicePreference({deviceScope:$('deviceScopeAll').checked?'all':'none'}));
$('approvalMode').addEventListener('change',()=>saveDevicePreference({approvalMode:$('approvalMode').value}));
$('floatingEnabled').addEventListener('change',()=>saveDevicePreference({floatingEnabled:$('floatingEnabled').checked}));
function renderFilesStatus(){const f=state.files||{};const ready=f.configured&&f.readable;$('filesFolderStatus').textContent=ready?(f.displayName||'선택한 폴더')+(f.writable?'':' · 읽기만 가능'):f.permissionLost?'폴더 권한이 만료되었습니다. 다시 선택하세요.':'사용할 폴더를 선택하세요';badge('filesBadge',ready, f.writable?'읽기·쓰기':'읽기',f.permissionLost?'권한 필요':'선택 전');$('chooseFilesFolder').disabled=state.busy||!!state.filesSaving;$('revokeFilesFolder').classList.toggle('hidden',!f.configured&&!f.permissionLost);$('revokeFilesFolder').disabled=state.busy||!!state.filesSaving;}
function updateFilesStatus(f){state.files={configured:false,readable:false,writable:false,displayName:'',permissionLost:false,...f};if(f.config){state.config={...state.config,...f.config};renderToolInventory();updateCapabilityDraft();}renderFilesStatus();}
async function changeFilesFolder(method){if(state.busy||state.filesSaving)return;state.filesSaving=true;renderFilesStatus();try{const f=await native(method);updateFilesStatus(f);if(method==='revokeFilesFolder')toast('폴더 접근을 해제했습니다.');else if(f.configured&&!f.cancelled)toast('선택한 폴더에만 접근합니다.');}catch(e){toast(e.message,6500);}finally{state.filesSaving=false;renderFilesStatus();}}
$('chooseFilesFolder').addEventListener('click',()=>changeFilesFolder('chooseFilesFolder'));
$('revokeFilesFolder').addEventListener('click',()=>changeFilesFolder('revokeFilesFolder'));
$('refreshDevice').addEventListener('click',()=>native('device').then(d=>{updateDevice(d);toast('기기 상태를 새로 읽었습니다.');}).catch(e=>toast(e.message)));
for(const e of document.querySelectorAll('[data-permission]'))e.addEventListener('click',()=>native('permission',{kind:e.dataset.permission}).catch(err=>toast(err.message)));
for(const [range,label] of [['volumeRange','volumeValue'],['brightnessRange','brightnessValue']])$(range).addEventListener('input',()=>$(label).textContent=$(range).value+'%');
$('applyVolume').addEventListener('click',()=>runTool('set_volume',{percent:Number($('volumeRange').value)}));$('applyBrightness').addEventListener('click',()=>runTool('set_brightness',{percent:Number($('brightnessRange').value)}));
$('rootToggle').addEventListener('click',()=>native('rootToggle',{enabled:!state.device.rootEnabled}).then(updateDevice).catch(e=>toast(e.message)));
$('rootProbe').addEventListener('click',async()=>{try{const r=await native('probeRoot');result('Root 상태 확인',r);}catch(e){toast(e.message,7500);}});
$('rootProcesses').addEventListener('click',()=>runTool('root_processes'));
async function refreshHistory(){try{[state.sessions,state.audit]=await Promise.all([native('sessions'),native('audit')]);renderHistory();}catch(e){if(state.page==='history')toast(e.message);}}
for(const b of document.querySelectorAll('[data-history-tab]'))b.addEventListener('click',()=>{state.historyTab=b.dataset.historyTab;renderHistory();});$('historySearch').addEventListener('input',renderHistory);
function empty(title,text){const e=node('div','empty-state');e.append(node('strong','',title),node('p','',text));return e;}
async function openSession(session){
    if(state.busy){toast('진행 중인 대화가 있습니다.');return;}
    try{const messages=await native('messages',{session:session.id});state.sid=session.id;state.messages=messages;await loadChatActivities(session.id);state.live='';followLatest=true;showPage('chat');renderConversation();renderSidebarSessions();scrollBottom(true);}catch(e){toast(e.message);}
}
let activeSidebarGesture=null;
async function deleteSidebarSession(session,row,open,direction=0){
    if(state.busy||row.dataset.deleting==='true'){toast('작업이 끝난 뒤 대화를 삭제하세요.');return;}
    row.dataset.deleting='true';row.setAttribute('aria-busy','true');open.disabled=true;row.classList.remove('swiping');row.classList.add('swipe-deleting','delete-armed');row.classList.toggle('swiping-right',direction>0);row.classList.toggle('swiping-left',direction<0);if(direction)open.style.transform='translate3d('+direction*(row.getBoundingClientRect().width+32)+'px,0,0)';
    try{const response=await native('deleteSession',{session:session.id,gesture:true});if(!response.deleted)throw new Error('대화를 삭제하지 않았습니다.');row.classList.add('session-collapsing');if(!window.matchMedia('(prefers-reduced-motion:reduce)').matches&&row.isConnected)await new Promise(resolve=>setTimeout(resolve,180));state.sessions=response.sessions||state.sessions.filter(s=>s.id!==session.id);if(state.sid===session.id){state.sid='';state.messages=[];state.live='';renderConversation();}renderHistory();toast('대화를 삭제했습니다.');}
    catch(e){row.dataset.deleting='false';row.removeAttribute('aria-busy');open.disabled=false;open.style.transform='';row.classList.remove('swipe-deleting','delete-armed','swiping','swiping-right','swiping-left');toast(e.message);}
}
function bindSessionDeleteGesture(row,open,session){
    let pointer=null,timer=null,armed=false,suppressClick=false,x=0,y=0,dx=0;
    const clear=(preserve=false)=>{clearTimeout(timer);timer=null;const old=pointer;pointer=null;armed=false;dx=0;if(old!==null&&open.hasPointerCapture?.(old))try{open.releasePointerCapture(old);}catch(_){}if(!preserve&&row.dataset.deleting!=='true'){open.style.transform='';row.classList.remove('delete-armed','swiping','swiping-right','swiping-left');}if(activeSidebarGesture?.row===row)activeSidebarGesture=null;};
    open.addEventListener('click',e=>{if(suppressClick){e.preventDefault();suppressClick=false;return;}openSession(session);});
    open.addEventListener('pointerdown',e=>{if(e.button!==0||state.busy||row.dataset.deleting==='true')return;activeSidebarGesture?.cancel();pointer=e.pointerId;x=e.clientX;y=e.clientY;dx=0;suppressClick=false;activeSidebarGesture={row,cancel:clear};timer=setTimeout(()=>{if(pointer===null||state.busy)return;armed=true;suppressClick=true;row.classList.add('delete-armed');try{open.setPointerCapture(pointer);}catch(_){}},450);});
    open.addEventListener('pointermove',e=>{if(pointer!==e.pointerId)return;const sx=e.clientX-x,sy=e.clientY-y;if(state.busy){suppressClick=true;clear();return;}if(!armed){if(Math.abs(sx)>12||Math.abs(sy)>10){suppressClick=true;clear();}return;}if(Math.abs(sy)>24&&Math.abs(sy)>Math.abs(sx)){suppressClick=true;clear();return;}dx=sx;row.classList.add('swiping');row.classList.toggle('swiping-right',dx>0);row.classList.toggle('swiping-left',dx<0);open.style.transform='translate3d('+dx+'px,0,0)';e.preventDefault();},{passive:false});
    open.addEventListener('pointerup',e=>{if(pointer!==e.pointerId)return;const deleting=armed&&Math.abs(dx)>=Math.min(96,row.getBoundingClientRect().width*.32),direction=Math.sign(dx);if(armed)suppressClick=true;clear(deleting&&!state.busy);if(deleting&&!state.busy)deleteSidebarSession(session,row,open,direction);});
    for(const event of ['pointercancel','lostpointercapture'])open.addEventListener(event,()=>{if(pointer!==null){suppressClick=true;clear();}});
    open.addEventListener('contextmenu',e=>{e.preventDefault();});
    open.addEventListener('keydown',e=>{if(e.key==='Delete'){e.preventDefault();clear();deleteSidebarSession(session,row,open);}});
}
function renderSidebarSessions(){
    activeSidebarGesture?.cancel();const search=$('sidebarSearch').value.trim().toLocaleLowerCase();const list=state.sessions.filter(s=>String(s.title).toLocaleLowerCase().includes(search));
    $('sidebarSessions').replaceChildren(...list.map(session=>{const row=node('div','sidebar-session'+(session.id===state.sid?' selected':''));row.dataset.session=session.id;const plate=node('span','session-delete-backplate');plate.setAttribute('aria-hidden','true');plate.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><path d="M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13M10 10v7m4-7v7"/></svg>';const open=node('button','sidebar-session-open',session.title);open.type='button';open.title=session.title;open.setAttribute('aria-label',session.title+' 대화 열기. 삭제하려면 길게 누른 뒤 좌우로 밀거나 Delete 키를 누르세요.');open.setAttribute('aria-keyshortcuts','Delete');bindSessionDeleteGesture(row,open,session);row.append(plate,open);return row;}));
    if(!list.length)$('sidebarSessions').append(node('p','sidebar-empty',search?'검색 결과가 없습니다.':'아직 대화가 없습니다.'));
}
$('sidebarSearch').addEventListener('input',renderSidebarSessions);
function renderHistory(){
    renderSidebarSessions();
    for(const b of document.querySelectorAll('[data-history-tab]'))b.classList.toggle('selected',state.historyTab===b.dataset.historyTab);
    $('sessionList').classList.toggle('hidden',state.historyTab!=='sessions');$('auditList').classList.toggle('hidden',state.historyTab!=='audit');$('historySearch').classList.toggle('hidden',state.historyTab!=='sessions');
    const list=state.sessions.filter(s=>s.title.toLowerCase().includes($('historySearch').value.toLowerCase()));
    $('sessionList').replaceChildren(...list.map(s=>{const row=node('article','history-item'),open=node('button','history-open');open.append(node('h3','',s.title),node('p','',date(s.created)+' · '+s.count+'개 메시지'));open.addEventListener('click',async()=>{if(state.busy){toast('진행 중인 대화가 있습니다.');return;}try{state.sid=s.id;state.messages=await native('messages',{session:s.id});await loadChatActivities(s.id);state.live='';showPage('chat');renderConversation();scrollBottom();}catch(e){toast(e.message);}});const del=node('button','delete-session');del.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13M10 10v7m4-7v7"/></svg>';del.setAttribute('aria-label','대화 삭제');del.addEventListener('click',()=>native('deleteSession',{session:s.id}).then(r=>{if(r.deleted){state.sessions=r.sessions;if(state.sid===s.id){state.sid='';state.messages=[];renderConversation();}renderHistory();}}).catch(e=>toast(e.message)));row.append(open,del);return row;}));
    if(!list.length)$('sessionList').append(empty('아직 기록이 없어요.','첫 대화를 시작하면 이 기기에 보관됩니다.'));
    $('auditList').replaceChildren(...state.audit.map(a=>{const row=node('article','history-item');const body=node('div','history-open');body.append(node('h3','',agentToolLabels[a.tool]||a.tool),node('p','',date(a.created)),node('span','audit-label',a.detail));const label={completed:'반환 완료',failed:'실패',denied:'거부',unconfirmed:'미확인'}[a.status]||a.status;row.append(body,node('span','audit-status '+a.status,label));return row;}));
    if(!state.audit.length)$('auditList').append(empty('실행된 도구가 없습니다.','실행·거부·실패를 구분해서 남깁니다.'));
}
let memoryDocuments=[],memoryDocumentBusy=false,memoryDocumentsLoading=false,memoryDocumentEditing='';
function setMemoryDocumentBusy(value){memoryDocumentBusy=value;for(const e of $('memoryDocumentForm').querySelectorAll('input,textarea,button'))e.disabled=value||state.busy;$('newMemoryDocument').disabled=value||state.busy;$('deleteMemoryDocument').disabled=value||state.busy||!memoryDocumentEditing||['USER.md','MEMORY.md'].includes(memoryDocumentEditing);}
function editMemoryDocument(document){memoryDocumentEditing=document.name;$('memoryDocumentName').value=document.name;$('memoryDocumentName').readOnly=true;$('memoryDocumentContent').value=document.content||'';$('memoryDocumentEdit').open=true;setMemoryDocumentBusy(false);$('memoryDocumentEdit').scrollIntoView({block:'nearest'});}
function renderMemoryDocuments(){
    const list=$('memoryDocuments');list.replaceChildren();for(const document of memoryDocuments){if(typeof document.name!=='string')continue;const card=node('details','owned-document-card');card.dataset.memoryName=document.name;const heading=node('summary','owned-document-heading'),copy=node('span','row-text');copy.append(node('strong','',document.name),node('small','',document.description||document.title||''));heading.append(copy,node('span','chevron','⌄'));const content=node('div','owned-document-expanded');card.append(heading,content);let loading=false,loaded=false;
        card.addEventListener('toggle',async()=>{if(!card.open||loading||loaded)return;loading=true;content.textContent='읽는 중…';try{const result=await native('memoryDocumentsRead',{name:document.name});if(!card.isConnected)return;if(result.name!==document.name||typeof result.content!=='string')throw new Error('문서 내용을 확인하지 못했습니다.');loaded=true;content.replaceChildren();const full=node('pre','owned-document-content',result.content);full.tabIndex=0;content.append(full);const actions=node('div','button-pair');const edit=node('button','secondary','편집');edit.type='button';edit.addEventListener('click',()=>{if(!state.busy&&!memoryDocumentBusy)editMemoryDocument(result);});actions.append(edit);if(!['USER.md','MEMORY.md'].includes(document.name)){const remove=node('button','secondary','삭제');remove.type='button';remove.addEventListener('click',()=>deleteMemoryDocument(document.name));actions.append(remove);}content.append(actions);}catch(e){content.textContent=e.message;}finally{loading=false;}});list.append(card);
    }if(!memoryDocuments.length)list.append(node('p','field-help','저장된 문서가 없습니다.'));
}
async function loadMemoryDocuments(){if(memoryDocumentsLoading)return;memoryDocumentsLoading=true;$('refreshMemoryDocuments').disabled=true;try{const result=await native('memoryDocumentsList');memoryDocuments=Array.isArray(result)?result:Array.isArray(result.documents)?result.documents:[];renderMemoryDocuments();}catch(e){$('memoryDocumentStatus').textContent=e.message;}finally{memoryDocumentsLoading=false;$('refreshMemoryDocuments').disabled=false;}}
async function deleteMemoryDocument(name){if(state.busy||memoryDocumentBusy||['USER.md','MEMORY.md'].includes(name)||!window.confirm(name+' 문서를 삭제할까요?'))return;setMemoryDocumentBusy(true);try{const result=await native('memoryDocumentsDelete',{name});if(result.deleted!==true)throw new Error('삭제 결과를 확인하지 못했습니다.');memoryDocuments=result.documents||memoryDocuments.filter(d=>d.name!==name);renderMemoryDocuments();if(memoryDocumentEditing===name){memoryDocumentEditing='';$('memoryDocumentEdit').open=false;}$('memoryDocumentStatus').textContent='삭제했습니다.';}catch(e){$('memoryDocumentStatus').textContent=e.message;}finally{setMemoryDocumentBusy(false);}}
$('refreshMemoryDocuments').addEventListener('click',loadMemoryDocuments);
$('newMemoryDocument').addEventListener('click',()=>{if(state.busy||memoryDocumentBusy)return;memoryDocumentEditing='';$('memoryDocumentName').value='';$('memoryDocumentName').readOnly=false;$('memoryDocumentContent').value='';$('memoryDocumentEdit').open=true;setMemoryDocumentBusy(false);});
$('deleteMemoryDocument').addEventListener('click',()=>deleteMemoryDocument(memoryDocumentEditing));
$('memoryDocumentForm').addEventListener('submit',async event=>{event.preventDefault();if(state.busy||memoryDocumentBusy)return;const name=$('memoryDocumentName').value.trim(),content=$('memoryDocumentContent').value;if(!name.endsWith('.md')){$('memoryDocumentStatus').textContent='.md 파일 이름을 입력하세요.';return;}setMemoryDocumentBusy(true);try{const result=await native('memoryDocumentsSave',{name,content});if(result.saved!==true||result.name!==name)throw new Error('저장 결과를 확인하지 못했습니다.');memoryDocumentEditing=name;$('memoryDocumentName').readOnly=true;memoryDocuments=result.documents||memoryDocuments;renderMemoryDocuments();$('memoryDocumentStatus').textContent='저장했습니다.';}catch(e){$('memoryDocumentStatus').textContent=e.message;}finally{setMemoryDocumentBusy(false);}});
$('memoryEditor').addEventListener('input',()=> $('memoryCount').textContent=$('memoryEditor').value.length.toLocaleString()+' / 4,000');
$('saveMemory').addEventListener('click',()=>native('saveMemory',{text:$('memoryEditor').value}).then(()=>toast('저장했습니다. 다음 메시지부터 맥락으로 사용됩니다.')).catch(e=>toast(e.message)));
for(const [key,label] of Object.entries(toolNames)){const e=node('div','tool-item'),body=node('div');body.append(node('strong','',label),node('code','',key));e.append(body,node('span','',key==='get_device_state'||key==='list_apps'?'조회':'승인 필요'));$('toolList').append(e);}
function selectMode(){
    state.config.mode='direct';$('modeInfo').textContent='API를 연결하면 이 휴대폰에서 대화와 기기 작업을 처리합니다.';
    $('endpointHelp').textContent='OpenAI 호환 API의 기본 주소입니다. 예: https://your-provider.example/v1';
    $('modelField').classList.remove('hidden');$('composerMode').textContent=state.config.model||'모델 선택';syncThinking();updateProviderSummary();
}
function hydrateConfig(cfg){configRevision++;modelCatalog=[];modelCatalogEndpoint='';state.config={...state.config,...cfg};$('endpoint').value=cfg.endpoint||'';$('model').value=cfg.model||'';$('reasoningEffort').value=cfg.reasoningEffort||'auto';$('token').value='';$('clearToken').checked=false;$('allowLan').checked=!!cfg.allowLan;$('providerSelect').value=cfg.providerId||providerForEndpoint(cfg.endpoint);renderProviderFields();$('tokenStatus').textContent=cfg.hasToken?'암호화된 인증 정보가 저장됨':'저장된 인증 정보 없음';$('memoryEditor').value=cfg.memory||'';$('memoryCount').textContent=$('memoryEditor').value.length.toLocaleString()+' / 4,000';selectMode();renderDevicePreferences();updateProviderSummary();renderToolInventory();updateWebStatus();}
function setConnected(ok){state.connected=ok;$('offlineNote').classList.toggle('hidden',ok);}
$('capabilityTabs').addEventListener('keydown',e=>{if(!['ArrowLeft','ArrowRight'].includes(e.key))return;e.preventDefault();const buttons=[...document.querySelectorAll('[data-capability-tab]')];const active=buttons.indexOf(document.activeElement);const next=buttons[(active+(e.key==='ArrowRight'?1:buttons.length-1))%buttons.length];next.click();next.focus();});
$('settingsForm').addEventListener('submit',async e=>{e.preventDefault();if(state.busy)return;try{const cfg=await native('saveSettings',{mode:'direct',providerId:$('providerSelect').value,endpoint:$('endpoint').value.trim(),model:$('model').value.trim(),token:$('token').value,clearToken:$('clearToken').checked,allowLan:$('allowLan').checked,reasoningEffort:$('reasoningEffort').value});hydrateConfig(cfg);setConnected(false);if(cfg.hasToken||cfg.providerId==='custom'){toast('연결 정보를 저장했습니다. 사용할 모델을 선택하세요.');openModels();}else toast('API 키를 입력해 연결을 완료하세요.');}catch(err){toast(err.message,6500);}});
$('testConnection').addEventListener('click',async()=>{const b=$('testConnection');b.disabled=true;b.textContent='확인 중';$('connectionResult').classList.remove('hidden','error');$('connectionResult').textContent='저장된 API 설정으로 연결을 확인합니다.';try{const result=await native('testConnection');setConnected(true,'API 연결 확인');$('connectionResult').textContent=result.modelVerified?'API 연결·인증과 선택한 모델의 응답을 확인했습니다. 기기 도구 호출은 실제 작업에서 확인합니다.':'API 연결·인증을 확인했습니다. 대화에서 사용할 모델을 선택하세요.';}catch(e){setConnected(false);$('connectionResult').classList.add('error');$('connectionResult').textContent=e.message;}finally{b.disabled=false;b.textContent='연결 테스트';}});
async function boot(){
    try{const s=await native('boot');state.capabilities=s.capabilities||{};if(s.availableTools&&!s.config.availableTools)s.config.availableTools=s.availableTools;initProviders();initReasoning();hydrateConfig(s.config);$('appVersion').textContent='v'+s.version+' · '+(s.releaseChannel||'beta');state.sessions=s.sessions||[];state.audit=s.audit||[];updateDevice(s.device||{});updateFilesStatus(s.files||{});updateShizuku(s.shizuku||s.device?.shizuku||{});if(s.terminal)updateTerminalState(s.terminal);if(s.jobs&&typeof updatePrivateJobs==='function')updatePrivateJobs(s.jobs);state.busy=s.busy;state.activeRunId=s.runId||'';state.progressStatus=s.status||'';state.live=s.live||'';state.liveTimelineOrder=Number(s.liveTimelineOrder)||0;state.sid=s.session||'';if(state.sid){state.messages=await native('messages',{session:state.sid});await loadChatActivities(state.sid);}reconcileComposerDraft();setBusy(!!s.busy);$('runStatusText').textContent=s.status||'실행 중';renderConversation();if(s.busy&&s.modelProgress)updateModelProgress(s.modelProgress);renderHistory();}
    catch(e){selectMode();renderHistory();if(!window.NativeBridge){$('appVersion').textContent='브라우저 디자인 미리보기';$('offlineNote').querySelector('p').textContent='미리보기 · 실제 모델 연결은 APK에서 사용할 수 있습니다.';}else toast(e.message);}
    syncComposerChoices();if(!hasConfiguredModel()&&state.page==='chat')showSetupGuide();
}
updateSend();

// Appearance stays independent of credentials, conversations and native permissions.
const designs = [{id:'white',name:'화이트',description:'밝고 선명한 화면'},{id:'black',name:'블랙',description:'차분한 어두운 화면'}];
function applyDesign(id,persist=true){
 const index=designs.findIndex(d=>d.id===id);if(index<0)return;const d=designs[index];
 document.body.dataset.design=id;
 const label=d.name;
 $('currentDesignName').textContent=label;
 $('homeTitle').textContent='무엇을 도와드릴까요?';$('homeIntro').textContent='메시지를 입력해 대화를 시작하세요.';
 for(const b of document.querySelectorAll('[data-design-choice]'))b.setAttribute('aria-pressed',String(b.dataset.designChoice===id));
 const meta=document.querySelector('meta[name="theme-color"]');
 requestAnimationFrame(()=>{if(meta)meta.content=getComputedStyle(document.body).getPropertyValue('--bg').trim();if(window.NativeBridge){const rgb=getComputedStyle(document.body).backgroundColor.match(/\d+/g);if(rgb&&rgb.length>=3){const values=rgb.slice(0,3).map(Number),background='#'+values.map(v=>v.toString(16).padStart(2,'0')).join('');native('appearance',{background,light:values[0]*.299+values[1]*.587+values[2]*.114>150}).catch(()=>{});}}});
 if(persist)try{localStorage.setItem('pocket-design',id);}catch(_){}
}
for(const [i,d] of designs.entries()){
 const b=node('button','design-option');b.type='button';b.dataset.designChoice=d.id;
 const swatch=node('span','design-swatch');swatch.setAttribute('aria-hidden','true');
 for(let j=0;j<3;j++)swatch.append(node('span','swatch-dot'));
 const copy=node('span','design-option-copy');copy.append(node('strong','',d.name),node('small','',d.description));
 const check=node('span','design-check','✓');check.setAttribute('aria-hidden','true');
 b.append(swatch,copy,check);b.addEventListener('click',()=>applyDesign(d.id));$('designPicker').append(b);
}
let initialDesign=document.body.dataset.design||'white';
try{initialDesign=new URLSearchParams(location.search).get('design')||document.body.dataset.previewDesign||localStorage.getItem('pocket-design')||initialDesign;}catch(_){}
initialDesign=initialDesign==='midnight'||initialDesign==='terminal'?'black':initialDesign;applyDesign(designs.some(d=>d.id===initialDesign)?initialDesign:'white',false);



// Keep the result dialog usable with keyboard navigation as well as touch.
let resultFocus;
$('resultModal').addEventListener('keydown',e=>{
 if(e.key==='Escape'){e.preventDefault();closeResult();return;}
 if(e.key!=='Tab')return;
 const elements=[...$('resultModal').querySelectorAll('button:not(:disabled),[tabindex="0"]')].filter(e=>e.getClientRects().length);
 if(!elements.length)return;const first=elements[0],last=elements[elements.length-1];
 if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}
 else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}
});

// Manual controls use the same native tools and approval flow as model requests.
const manualToolState = {running:false,apps:[],snapshot:null,expiresAt:0,timer:null,loadingApps:false};
const manualToolSchema = {
    get_device_state:{help:'모델·배터리·메모리·권한 상태를 읽습니다.',fields:[]},
    list_apps:{help:'실행 가능한 앱과 제어 허용 상태를 읽습니다.',fields:[]},
    launch_app:{help:'선택한 앱을 실행합니다. 저장한 승인 방식이 적용됩니다.',fields:['package']},
    open_settings:{help:'선택한 Android 설정 화면을 엽니다. 설정이나 권한은 직접 변경하지 않습니다.',fields:['page']},
    set_volume:{help:'미디어 볼륨을 0~100%로 변경합니다.',fields:['volume']},
    set_brightness:{help:'화면 밝기를 1~100%로 변경합니다. 시스템 설정 쓰기 권한이 필요합니다.',fields:['brightness']},
    root_processes:{help:'실제 Root 또는 Shizuku 연결 권한으로 프로세스를 조회합니다. 연결 상태와 UID를 확인하세요.',fields:[]},
    set_wifi:{help:'실제 Root 또는 Shizuku 연결 권한으로 Wi-Fi를 변경합니다. Wi-Fi를 끄면 모델·서버 연결이 끊길 수 있습니다.',fields:['enabled']},
    force_stop_app:{help:'실제 Root 또는 Shizuku 연결 권한으로 일반 앱을 강제 종료합니다. 시스템 앱은 제외됩니다.',fields:['package']},
    read_screen:{help:'현재 앱의 화면 요소를 읽습니다. 읽은 결과에서 대상을 선택할 수 있으며 45초 후 만료됩니다.',fields:[]},
    capture_screen:{help:'현재 앱의 화면 이미지를 캡처합니다. 직접 실행에서는 화면 요소와 캡처 정보만 표시합니다. 이미지 분석은 채팅에서 요청하세요. Android 11 이상과 접근성 캡처 권한이 필요합니다.',fields:[]},
    click_element:{help:'새로 읽은 화면의 클릭 가능한 요소를 클릭합니다. 화면이 바뀌면 다시 읽으세요.',fields:['snapshot','element']},
    type_text:{help:'새로 읽은 화면의 입력란에 최대 2,000자를 입력합니다. 입력 내용을 자동 제출하지 않습니다.',fields:['snapshot','element','text']},
    press_back:{help:'새로 읽은 앱에서 뒤로 갑니다.',fields:['snapshot']},
    press_home:{help:'새로 읽은 앱에서 Android 홈으로 갑니다.',fields:['snapshot']},
    tap_screen:{help:'새로 읽은 화면의 지정한 좌표를 누릅니다. 화면 좌표는 픽셀 단위입니다.',fields:['snapshot','x','y']},
    swipe_screen:{help:'새로 읽은 화면의 시작 좌표에서 끝 좌표까지 쓸어 이동합니다.',fields:['snapshot','startX','startY','endX','endY','durationMs']},
    scroll_element:{help:'새로 읽은 화면의 스크롤 가능한 요소를 위 또는 아래로 스크롤합니다.',fields:['snapshot','element','direction']}
};
function clearScreenTargets(){ $('screenTargets').replaceChildren();$('screenTargets').classList.add('hidden'); }
function toolField(label,id,control){
    const wrap=node('div','field');const title=node('label','',label);title.htmlFor=id;control.id=id;control.name=id;control.setAttribute('aria-describedby','toolHelp');wrap.append(title,control);return wrap;
}
function toolOption(value,label){const option=node('option','',label);option.value=value;return option;}
function freshManualSnapshot(){return manualToolState.snapshot&&Date.now()<manualToolState.expiresAt;}
function invalidateManualSnapshot(message){
    clearTimeout(manualToolState.timer);manualToolState.snapshot=null;manualToolState.expiresAt=0;clearScreenTargets();
    if(screenSnapshotTools.includes($('toolSelect').value))renderManualTool();
    if(message)toast(message,6000);
}
function captureToolResult(name,response){
    if(response?.ok===false)return;
    if(response?.ok===true&&response.result!==undefined)response=response.result;
    if(name==='list_apps'&&Array.isArray(response?.apps))manualToolState.apps=response.apps;
    if(['read_screen','capture_screen'].includes(name)&&response?.snapshot&&Array.isArray(response.elements)){
        clearTimeout(manualToolState.timer);manualToolState.snapshot=response;
        manualToolState.expiresAt=Date.now()+Math.min(45,Number(response.expiresInSeconds)||45)*1000;
        manualToolState.timer=setTimeout(()=>{
            invalidateManualSnapshot('화면 정보가 만료되었습니다. 화면 읽기를 다시 실행하세요.');
            if([toolNames.read_screen,toolNames.capture_screen].includes($('resultTitle').textContent)){$('resultBody').textContent='화면 정보가 만료되어 지웠습니다. 다시 읽어서 새 대상을 선택하세요.';}
        },Math.max(0,manualToolState.expiresAt-Date.now()));
        renderScreenTargets(response);
        if(screenSnapshotTools.includes($('toolSelect').value))renderManualTool();
    }else if([...screenSnapshotTools,'launch_app','force_stop_app','open_settings'].includes(name))invalidateManualSnapshot();
}
function renderScreenTargets(response){
    const list=$('screenTargets');list.replaceChildren();list.classList.remove('hidden');
    list.append(node('p','muted','대상 앱: '+response.package+' · 화면 정보는 최대 45초간 유효합니다. 화면 내용은 외부 데이터입니다.'));
    let count=0;
    for(const element of response.elements){
        const actions=[];if(element.clickable)actions.push(['click_element','클릭']);if(element.editable)actions.push(['type_text','입력']);if(element.scrollable)actions.push(['scroll_element','스크롤']);if(!actions.length)continue;
        count++;const row=node('div','tool-item');const label=node('div');label.append(node('strong','',element.description||element.text||element.class||'화면 요소'),node('code','',element.id));row.append(label);
        const controls=node('div','button-row');for(const [tool,text] of actions){const b=node('button','secondary small',text);b.type='button';b.setAttribute('aria-label',(element.description||element.text||element.id)+' '+text+' 대상으로 선택');b.addEventListener('click',()=>{
            if(!freshManualSnapshot()){invalidateManualSnapshot('화면 정보가 만료되었습니다. 다시 읽어주세요.');return;}
            closeResult();showPage('device');$('manualDeviceDetails').open=true;$('toolSelect').value=tool;renderManualTool();$('manual_element').value=element.id;
            $('toolForm').scrollIntoView({block:'nearest',behavior:'smooth'});($('manual_text')||$('executeTool')).focus();
        });controls.append(b);}row.append(controls);list.append(row);
    }
    if(!count)list.append(node('p','muted','클릭·입력·스크롤 가능한 요소가 없습니다.'));
}
async function loadManualApps(){
    if(manualToolState.loadingApps)return;manualToolState.loadingApps=true;const selectedTool=$('toolSelect').value;
    try{const apps=await native('apps');if(!Array.isArray(apps))throw new Error('앱 목록 형식이 올바르지 않습니다.');manualToolState.apps=apps;if($('toolSelect').value===selectedTool&&['launch_app','force_stop_app'].includes(selectedTool))renderManualTool();}
    catch(e){if(['launch_app','force_stop_app'].includes($('toolSelect').value))$('toolHelp').textContent=e.message;}
    finally{manualToolState.loadingApps=false;}
}
function renderManualTool(){
    const name=$('toolSelect').value,schema=manualToolSchema[name];if(!schema)return;
    $('toolFields').replaceChildren();$('toolHelp').textContent=schema.help;$('toolHelp').setAttribute('role','status');
    for(const field of schema.fields){
        let input,label;
        if(field==='package'){
            label='앱';input=node('select');input.required=true;input.append(toolOption('','앱을 선택하세요'));
            const apps=manualToolState.apps.filter(a=>!a.protected&&(name!=='force_stop_app'||!a.system));
            for(const app of apps)input.append(toolOption(app.package,app.label+' · '+app.package));
            $('toolFields').append(toolField(label,'manual_package',input));
            const refresh=node('button','secondary small','앱 목록 새로 읽기');refresh.type='button';refresh.addEventListener('click',loadManualApps);$('toolFields').append(refresh);
            if(!apps.length)$('toolFields').append(node('p','muted','앱 목록을 읽어 실행할 앱을 선택하세요.'));
            if(name==='force_stop_app'&&!apps.length)$('toolFields').append(node('p','muted','앱 목록을 읽어 일반 앱을 확인하세요.'));
            continue;
        }
        if(field==='page'){label='설정 화면';input=node('select');for(const [value,text] of [['general','일반'],['wifi','Wi-Fi'],['bluetooth','블루투스'],['display','디스플레이'],['battery','배터리']])input.append(toolOption(value,text));}
        if(field==='volume'||field==='brightness'){
            label=field==='volume'?'미디어 볼륨 (%)':'화면 밝기 (%)';input=node('input');input.type='number';input.min=field==='volume'?'0':'1';input.max='100';input.step='1';input.required=true;
            input.value=field==='volume'?Math.round(state.device.volume??50):Math.max(1,Math.round((state.device.brightness??128)/255*100));
        }
        if(field==='enabled'){label='Wi-Fi 변경';input=node('select');input.append(toolOption('true','켜기'),toolOption('false','끄기'));}
        if(field==='snapshot'){label='화면 정보';input=node('input');input.type='text';input.readOnly=true;input.required=true;input.value=freshManualSnapshot()?manualToolState.snapshot.snapshot:'';input.placeholder='먼저 화면 읽기를 실행하세요';}
        if(field==='element'){
            label='화면 요소';input=node('select');input.required=true;input.append(toolOption('','요소를 선택하세요'));
            if(freshManualSnapshot())for(const el of manualToolState.snapshot.elements){const ok=name==='click_element'?el.clickable:name==='type_text'?el.editable:el.scrollable;if(ok)input.append(toolOption(el.id,el.id+' · '+(el.description||el.text||el.class||'화면 요소')));}
        }
        if(field==='text'){label='입력할 텍스트';input=node('textarea');input.maxLength=2000;input.rows=4;input.placeholder='최대 2,000자 · 비밀번호 입력란은 지원하지 않습니다';}
        if(['x','y','startX','startY','endX','endY','durationMs'].includes(field)){
            label={x:'누를 X 좌표',y:'누를 Y 좌표',startX:'시작 X 좌표',startY:'시작 Y 좌표',endX:'끝 X 좌표',endY:'끝 Y 좌표',durationMs:'쓸기 시간 (밀리초)'}[field];input=node('input');input.type='number';input.step='1';input.required=true;input.min=field==='durationMs'?'100':'0';if(field==='durationMs'){input.max='1200';input.value='350';}else{const b=manualToolState.snapshot?.bounds;if(Array.isArray(b)&&b.length===4){const horizontal=field.toLowerCase().endsWith('x');input.min=String(b[horizontal?0:1]);input.max=String(b[horizontal?2:3]-1);}}
        }
        if(field==='direction'){label='스크롤 방향';input=node('select');input.append(toolOption('down','아래로'),toolOption('up','위로'));}
        if(input)$('toolFields').append(toolField(label,'manual_'+field,input));
    }
    if(schema.fields.includes('snapshot')){
        const reread=node('button','secondary small','화면 새로 읽기');reread.type='button';reread.addEventListener('click',()=>executeManualTool('read_screen',{}));$('toolFields').append(reread);
        if(!freshManualSnapshot())$('toolHelp').textContent+=' 먼저 화면 새로 읽기를 실행한 뒤 결과에서 대상을 선택하세요.';
    }
    $('executeTool').disabled=manualToolState.running||state.busy||!!state.config.availableTools&&!state.config.availableTools.names.includes(name);
}
function manualToolArguments(name){
    const schema=manualToolSchema[name],args={};
    for(const field of schema.fields){const e=$('manual_'+field);if(!e)throw new Error('도구 입력란을 확인하세요.');
        if(field==='volume'||field==='brightness'){const n=Number(e.value);if(e.value===''||!Number.isInteger(n)||n<Number(e.min)||n>100)throw new Error('백분율을 '+e.min+'~100 사이의 정수로 입력하세요.');args.percent=n;}
        else if(['x','y','startX','startY','endX','endY','durationMs'].includes(field)){const n=Number(e.value);if(e.value===''||!Number.isInteger(n)||n<Number(e.min)||(e.max!==''&&n>Number(e.max)))throw new Error('좌표와 시간을 화면 범위 안의 정수로 입력하세요.');args[field]=n;}
        else if(field==='enabled')args.enabled=e.value==='true';
        else args[field]=e.value;
    }
    if(schema.fields.includes('package')&&!args.package)throw new Error('실행할 앱을 선택하세요.');
    if(schema.fields.includes('snapshot')){if(!freshManualSnapshot())throw new Error('화면 정보가 만료되었거나 없습니다. 먼저 화면을 다시 읽으세요.');if(schema.fields.includes('element')&&!args.element)throw new Error('동작할 화면 요소를 선택하세요.');}
    if(name==='type_text'&&args.text.length>2000)throw new Error('입력 텍스트는 최대 2,000자입니다.');return args;
}
async function executeManualTool(name,args){
    if(manualToolState.running||state.busy){toast('진행 중인 작업이 끝난 뒤 실행하세요.');return;}
    manualToolState.running=true;$('toolSelect').disabled=true;$('toolForm').setAttribute('aria-busy','true');$('toolForm').querySelectorAll('button,input,select,textarea').forEach(e=>e.disabled=true);$('executeTool').textContent='실행 대기 중';$('stopManualTool').classList.remove('hidden');$('stopManualTool').disabled=false;
    try{await runTool(name,args);}
    finally{manualToolState.running=false;$('toolSelect').disabled=false;$('toolForm').setAttribute('aria-busy','false');$('toolForm').querySelectorAll('button,input,select,textarea').forEach(e=>e.disabled=false);$('executeTool').textContent='도구 실행';renderToolInventory();$('stopManualTool').classList.add('hidden');}
}
$('toolSelect').replaceChildren(...Object.keys(manualToolSchema).map(name=>toolOption(name,toolNames[name])));
$('toolSelect').addEventListener('change',()=>{renderManualTool();if(['launch_app','force_stop_app'].includes($('toolSelect').value))loadManualApps();});
$('toolForm').addEventListener('submit',e=>{e.preventDefault();try{executeManualTool($('toolSelect').value,manualToolArguments($('toolSelect').value));}catch(error){$('toolHelp').textContent=error.message;toast(error.message);}});

renderManualTool();

$('stopManualTool').addEventListener('click',()=>{if(!manualToolState.running)return;native('stop').then(()=>toast('작업 중단을 요청했습니다. 완료될 때까지 기다려주세요.')).catch(error=>toast(error.message));});

// Small, dependency-free Markdown renderer. Model text is always inserted as text.
function renderChatMarkdown(target, text) {
  const source = String(text == null ? '' : text).slice(0, 150000).replace(/\r\n?/g, '\n');
  const fragment = document.createDocumentFragment();
  const make = (tag, className) => {
    const element = document.createElement(tag);
    if (className) element.className = className;
    return element;
  };
  const inline = (parent, value, depth = 0) => {
    if (depth >= 4) { parent.append(document.createTextNode(value)); return; }
    let plain = '';
    const flush = () => { if (plain) { parent.append(document.createTextNode(plain)); plain = ''; } };
    for (let i = 0; i < value.length;) {
      const character = value[i];
      if (character === '\\' && i + 1 < value.length && /[\\`*_\[\]()]/.test(value[i + 1])) {
        plain += value[i + 1]; i += 2; continue;
      }
      if (character === '\n') { flush(); parent.append(make('br')); i++; continue; }
      if (character === '`' || character === '*' || character === '_') {
        const marker = character === '`' ? (value.slice(i, i + 12).match(/^`{1,12}/) || ['`'])[0]
          : value.slice(i, i + 2) === character.repeat(2) ? character.repeat(2) : character;
        // Bound delimiter search to keep malformed streamed Markdown inexpensive.
        const window = value.slice(i + marker.length, i + marker.length + 4096);
        const close = window.indexOf(marker);
        const wordUnderscore = character === '_' && i > 0 && /\w/.test(value[i - 1]);
        if (close > 0 && !wordUnderscore) {
          flush();
          const element = make(character === '`' ? 'code' : marker.length === 2 ? 'strong' : 'em');
          const content = window.slice(0, close);
          if (character === '`') element.textContent = content;
          else inline(element, content, depth + 1);
          parent.append(element); i += marker.length * 2 + close; continue;
        }
      }
      if (character === '[') {
        const candidate = value.slice(i, i + 4096);
        const match = candidate.match(/^\[([^\]\n]+)\]\(([^\s)]+)\)/);
        if (match) {
          let url = null;
          try {
            const parsed = new URL(match[2]);
            if (['https:', 'http:'].includes(parsed.protocol) && !parsed.username && !parsed.password) url = parsed.href;
          } catch (_) { /* Invalid links remain literal text. */ }
          if (url) {
            flush(); const link = make('a'); link.href = url;
            link.target = '_blank'; link.rel = 'noopener noreferrer';
            inline(link, match[1], depth + 1); parent.append(link); i += match[0].length; continue;
          }
        }
      }
      plain += character; i++;
    }
    flush();
  };
  const tableCells = line => {
    let value = line.trim();
    if (value.startsWith('|')) value = value.slice(1);
    if (value.endsWith('|') && !value.endsWith('\\|')) value = value.slice(0, -1);
    const cells = []; let cell = '';
    for (let i = 0; i < value.length; i++) {
      if (value[i] === '\\' && value[i + 1] === '|') { cell += '|'; i++; }
      else if (value[i] === '|') { cells.push(cell.trim()); cell = ''; }
      else cell += value[i];
    }
    cells.push(cell.trim()); return cells;
  };
  const isDivider = line => line.includes('|') && tableCells(line).every(cell => /^:?-{3,}:?$/.test(cell));
  const fence = line => line.match(/^\s{0,3}(`{3,}|~{3,})([^`]*)$/);
  const listItem = line => line.match(/^\s{0,3}([-+*]|\d{1,9}[.)])\s+(.+)$/);
  const heading = line => line.match(/^\s{0,3}(#{1,6})\s+(.+)$/);
  const rule = line => /^\s{0,3}([-*_])(?:\s*\1){2,}\s*$/.test(line);
  const blocks = (parent, lines, depth = 0) => {
    for (let i = 0; i < lines.length;) {
      const line = lines[i];
      if (!line.trim()) { i++; continue; }
      const startFence = fence(line);
      if (startFence) {
        const content = []; const marker = startFence[1][0]; const minimum = startFence[1].length;
        i++;
        while (i < lines.length) {
          const closing = lines[i].trim();
          if (closing.length >= minimum && closing.split(marker).join('') === '') { i++; break; }
          content.push(lines[i++]);
        }
        const raw = content.join('\n');
        const block = make('div', 'code-block'); const head = make('div', 'code-head');
        const label = make('span'); label.textContent = startFence[2].trim().slice(0, 80) || '코드';
        const copy = make('button'); copy.type = 'button'; copy.textContent = '복사';
        copy.setAttribute('aria-label', '코드 복사');
        copy.addEventListener('click', () => copyMessage(raw));
        head.append(label, copy); const pre = make('pre'); const code = make('code');
        code.textContent = raw; pre.append(code); block.append(head, pre); parent.append(block); continue;
      }
      const title = heading(line);
      if (title) { const element = make('h' + title[1].length); inline(element, title[2].replace(/\s+#+\s*$/, '')); parent.append(element); i++; continue; }
      if (rule(line)) { parent.append(make('hr')); i++; continue; }
      if (/^\s{0,3}>/.test(line) && depth < 4) {
        const quoteLines = [];
        while (i < lines.length && /^\s{0,3}>/.test(lines[i])) quoteLines.push(lines[i++].replace(/^\s{0,3}> ?/, ''));
        const quote = make('blockquote'); blocks(quote, quoteLines, depth + 1); parent.append(quote); continue;
      }
      if (i + 1 < lines.length && line.includes('|') && isDivider(lines[i + 1])) {
        const headers = tableCells(line).slice(0, 32); const dividers = tableCells(lines[i + 1]);
        const wrapper = make('div', 'markdown-table'); const table = make('table'); const head = make('thead');
        const row = make('tr');
        headers.forEach((value, column) => {
          const cell = make('th'); cell.scope = 'col'; inline(cell, value);
          const divider = dividers[column] || '';
          if (divider.endsWith(':')) cell.style.textAlign = divider.startsWith(':') ? 'center' : 'right';
          row.append(cell);
        });
        head.append(row); const body = make('tbody'); i += 2;
        while (i < lines.length && lines[i].trim() && lines[i].includes('|')) {
          const values = tableCells(lines[i++]); const bodyRow = make('tr');
          headers.forEach((_, column) => { const cell = make('td'); inline(cell, values[column] || ''); cell.style.textAlign = row.children[column].style.textAlign; bodyRow.append(cell); });
          body.append(bodyRow);
        }
        table.append(head, body); wrapper.append(table); parent.append(wrapper); continue;
      }
      const firstItem = listItem(line);
      if (firstItem) {
        const ordered = /^\d/.test(firstItem[1]); const list = make(ordered ? 'ol' : 'ul');
        if (ordered) list.start = parseInt(firstItem[1], 10);
        while (i < lines.length) {
          const match = listItem(lines[i]); if (!match || /^\d/.test(match[1]) !== ordered || rule(lines[i])) break;
          const item = make('li'); let content = match[2]; i++;
          while (i < lines.length && /^\s{2,}\S/.test(lines[i]) && !listItem(lines[i]) && !fence(lines[i])) content += '\n' + lines[i++].trim();
          inline(item, content); list.append(item);
        }
        parent.append(list); continue;
      }
      const paragraph = [line]; i++;
      while (i < lines.length && lines[i].trim() && !fence(lines[i]) && !heading(lines[i]) && !rule(lines[i]) && !listItem(lines[i]) && !/^\s{0,3}>/.test(lines[i]) && !(i + 1 < lines.length && lines[i].includes('|') && isDivider(lines[i + 1]))) paragraph.push(lines[i++]);
      const element = make('p'); inline(element, paragraph.join('\n')); parent.append(element);
    }
  };
  blocks(fragment, source.split('\n'));
  target.replaceChildren(fragment);
}

// Local capabilities use the same native agent and tool registry as chat.
const capabilityFallback={skills:[{id:'general',name:'일반',description:'질문과 일상 작업을 함께 해결합니다.'},{id:'write',name:'글쓰기',description:'문장을 쓰고 다듬습니다.'},{id:'plan',name:'계획',description:'목표를 실행 가능한 단계로 정리합니다.'},{id:'code',name:'코딩',description:'코드를 설명하고 문제를 해결합니다.'}],plugins:[{id:'terminal',name:'터미널',description:'Android 앱 명령 실행과 프로세스 관리'},{id:'web',name:'웹 검색',description:'Tavily API로 실제 검색과 페이지 읽기'},{id:'device',name:'기기',description:'기기 상태, 앱 실행, 볼륨과 밝기 조절'},{id:'screen',name:'화면',description:'Android 권한으로 화면 읽기와 조작'},{id:'root',name:'Root · Shizuku',description:'실제 Root 또는 Shizuku 연결 권한의 기기 작업'}]};
const effortNames={auto:'자동',none:'없음',minimal:'최소',low:'낮음',medium:'보통',high:'높음',xhigh:'매우 높음',max:'최대',ultra:'최고'};
let activeSheet=null,sheetFocus=null,preferenceSaving=false,modelLoading=false,modelCatalog=[],modelCatalogEndpoint='',configRevision=0;
function syncComposerChoices(){const disabled=state.busy||preferenceSaving||!hasConfiguredModel();$('composerConnection').disabled=disabled;$('reasoningPicker').disabled=disabled;$('reasoningPicker').classList.remove('hidden');$('homeModelSetup').classList.toggle('hidden',hasConfiguredModel());updateSend();}
function syncThinking(){const effort=state.config.reasoningEffort||'auto';$('composerThinking').textContent=hasConfiguredModel()?'생각 · '+(effortNames[effort]||'자동'):'생각 수준';$('composerThinking').title=state.config.effectiveReasoningEffort&&state.config.effectiveReasoningEffort!==effort?'API 전달: '+(state.config.effectiveReasoningEffort==='enabled'?'생각 켜기':state.config.effectiveReasoningEffort):'';syncComposerChoices();}
function openSheet(id){closeActiveSheet(false);sheetFocus=document.activeElement;activeSheet=$(id);activeSheet.classList.remove('hidden');document.body.dataset.sheet='open';for(const child of $('app').children)if(child!==activeSheet&&child.id!=='toast')child.inert=true;activeSheet.inert=false;$('composerTools').setAttribute('aria-expanded',String(id==='capabilitySheet'));activeSheet.querySelector('.sheet-head button').focus();}
function closeActiveSheet(restore=true){if(!activeSheet)return false;if(activeSheet.id==='capabilitySheet'&&capabilitiesDirty()&&!preferenceSaving)toast('저장하지 않은 변경 사항을 취소했습니다.');activeSheet.classList.add('hidden');activeSheet=null;delete document.body.dataset.sheet;for(const child of $('app').children)child.inert=false;$('sidebar').inert=document.body.dataset.sidebar!=='open';$('composerTools').setAttribute('aria-expanded','false');if(restore&&sheetFocus?.isConnected)sheetFocus.focus();return true;}
for(const id of ['capabilitySheet','modelSheet','reasoningSheet','setupDialog']){
 $(id).querySelector('.sheet-scrim').addEventListener('click',()=>closeActiveSheet());
 $(id).addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();e.stopPropagation();closeActiveSheet();return;}if(e.key!=='Tab')return;const focus=[...$(id).querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled)')].filter(el=>el.tabIndex>=0&&el.getClientRects().length);if(!focus.length)return;const first=focus[0],last=focus[focus.length-1];if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}});
}
$('closeCapabilities').addEventListener('click',()=>closeActiveSheet());$('closeModels').addEventListener('click',()=>closeActiveSheet());$('closeReasoning').addEventListener('click',()=>closeActiveSheet());$('closeSetup').addEventListener('click',()=>closeActiveSheet());$('setupLater').addEventListener('click',()=>closeActiveSheet());
for(const button of document.querySelectorAll('[data-capability-tab]'))button.addEventListener('click',()=>{const tab=button.dataset.capabilityTab;for(const b of document.querySelectorAll('[data-capability-tab]')){const selected=b.dataset.capabilityTab===tab;b.classList.toggle('selected',selected);b.setAttribute('aria-selected',String(selected));}$('skillOptions').classList.toggle('hidden',tab!=='skills');$('pluginOptions').classList.toggle('hidden',tab!=='plugins');$('savedSkillOptions').classList.toggle('hidden',tab!=='savedSkills');});
function capabilityDraft(){return {skillId:$('skillOptions').querySelector('input:checked')?.value||state.config.skillId||'general',enabledPlugins:[...$('pluginOptions').querySelectorAll('input:checked')].map(e=>e.value),activeSkillName:$('savedSkillOptions').querySelector('input:checked')?.value??state.config.activeSkillName??''};}
function capabilitiesDirty(){if(!$('skillOptions').children.length)return false;const draft=capabilityDraft(),saved=state.config.enabledPlugins||['device','screen','root'];return draft.activeSkillName!==(state.config.activeSkillName||'')||draft.skillId!==(state.config.skillId||'general')||JSON.stringify([...draft.enabledPlugins].sort())!==JSON.stringify([...saved].sort());}
function updateCapabilityDraft(){const dirty=capabilitiesDirty();$('capabilityDraftStatus').classList.toggle('hidden',!dirty&&!preferenceSaving);$('capabilityDraftStatus').textContent=preferenceSaving?'변경 사항을 저장하고 있습니다.':dirty?'아직 저장되지 않았습니다. 아래에서 변경 사항을 저장하세요.':'';$('capabilityApply').disabled=state.busy||preferenceSaving||!dirty;$('capabilityApply').textContent=preferenceSaving?'저장 중…':dirty?'변경 사항 저장':'저장됨';for(const input of document.querySelectorAll('#skillOptions input,#pluginOptions input,#savedSkillOptions input'))input.disabled=state.busy||preferenceSaving||(input.name==='activeSkillName'&&savedSkillsLoading);}
function toolReadinessLabel(name,disabled=false){
    if(disabled)return '플러그인 꺼짐';
    if(name==='get_device_state'||name==='list_apps')return '조회';
    const v=state.device;
    if(state.config.deviceScope==='none'&&['launch_app','force_stop_app','read_screen','capture_screen',...screenSnapshotTools].includes(name))return '전체 앱 권한 꺼짐';
    if(name==='set_brightness'&&v.writeSettings===false)return '밝기 권한 필요';
    if(['root_processes','set_wifi','force_stop_app'].includes(name)&&v.root===false&&!shizukuPrivilegeReady())return 'Root·Shizuku 연결 필요';
    if(name==='read_screen'||name==='capture_screen'||screenSnapshotTools.includes(name)){
        if(v.locked===true)return '잠금 해제 필요';
        if(v.accessibility===false)return '화면 제어 연결 필요';

        if(name==='capture_screen'){if(v.screenshotSupported===false)return 'Android 11 이상 필요';if(v.screenshotCapability===false)return '접근성 재연결 필요';if(v.screenshotAvailable!==true)return '캡처 확인 전';}
        if(['tap_screen','swipe_screen'].includes(name)&&v.gestures===false)return '제스처 미지원';
    }
    return state.config.approvalMode==='auto'?'자동 승인 설정':'승인 요청';
}
function renderToolInventory(){const inventory=state.config.availableTools;const known=inventory&&Array.isArray(inventory.names),names=known?inventory.names:[],count=known?(Number.isInteger(inventory.count)?inventory.count:names.length):null;const enabled=(state.config.enabledPlugins||['device','screen','root']).map(id=>(state.capabilities?.plugins||capabilityFallback.plugins).find(p=>p.id===id)?.name||id).join(' · ');for(const e of document.querySelectorAll('.device-tool-count'))e.textContent=Object.keys(toolNames).length+'개 도구';$('capabilitySavedStatus').textContent=known?'저장됨 · 에이전트 도구 '+count+'개':'저장된 플러그인: '+(enabled||'모두 꺼짐')+' · 실제 도구 목록은 APK에서 확인합니다.';$('settingsToolCount').textContent=known?'모델에 제공하는 도구 '+count+'개':'플러그인과 기기 작업';$('capabilityToolNames').replaceChildren(...names.map(name=>node('p','',(agentToolLabels[name]?agentToolLabels[name]+' · '+name:name))));if(known&&!names.length)$('capabilityToolNames').append(node('p','','플러그인을 켜고 변경 사항을 저장하면 도구가 표시됩니다.'));$('toolList').replaceChildren(...Object.entries(toolNames).map(([key,label])=>{const e=node('div','tool-item'),body=node('div');body.append(node('strong','',label),node('code','',key));e.append(body,node('span','',toolReadinessLabel(key,known&&!names.includes(key))));return e;}));for(const option of $('toolSelect').options)option.disabled=!!known&&!names.includes(option.value);const selected=$('toolSelect').value;$('executeTool').disabled=state.busy||manualToolState.running||!!known&&!names.includes(selected);for(const [id,name] of [['applyVolume','set_volume'],['applyBrightness','set_brightness'],['rootProcesses','root_processes']]){const disabled=state.busy||!!known&&!names.includes(name);$(id).disabled=disabled;$(id).title=known&&!names.includes(name)?'이 도구의 플러그인을 켜고 저장하세요.':'';}const disabledDevice=known&&Object.keys(manualToolSchema).some(name=>!names.includes(name));$('devicePluginNotice').classList.toggle('hidden',!disabledDevice);renderTerminal();renderSkillPackageControls();}
function openCapabilities(){if(state.busy||preferenceSaving){toast('실행이 끝난 뒤 기능을 변경하세요.');return;}const c=state.capabilities||capabilityFallback;for(const [kind,host,type] of [['skills','skillOptions','radio'],['plugins','pluginOptions','checkbox']]){$(host).replaceChildren();for(const item of c[kind]||capabilityFallback[kind]){const label=node('label','sheet-row'),input=node('input');input.type=type;input.name=kind;input.value=item.id;input.checked=kind==='skills'?item.id===(state.config.skillId||'general'):(state.config.enabledPlugins||['device','screen','root']).includes(item.id);input.addEventListener('change',updateCapabilityDraft);const copy=node('span','option-copy');copy.append(node('strong','',item.name),node('small','',item.id==='web'?'무료 검색 또는 Tavily API로 웹을 검색합니다.':item.description||''));label.append(input,copy);$(host).append(label);}}renderSavedSkillOptions(savedSkills);renderToolInventory();updateCapabilityDraft();openSheet('capabilitySheet');loadSavedSkills();}
$('discardCapabilities').addEventListener('click',()=>closeActiveSheet());
async function savePreferences(p){if(state.busy||preferenceSaving||state.shizukuSaving){toast('현재 작업이 끝난 뒤 다시 시도하세요.');return false;}preferenceSaving=true;const savingSheet=activeSheet;const priorStatus=$('modelStatus').textContent;$('saveDefaults').disabled=true;$('capabilityApply').disabled=true;$('capabilityApply').textContent='적용 중…';if(savingSheet)savingSheet.setAttribute('aria-busy','true');if(p.model!==undefined)$('modelStatus').textContent='모델 적용 중…';if(p.reasoningEffort!==undefined)$('reasoningMapping').textContent='생각 수준 적용 중…';syncComposerChoices();updateCapabilityDraft();renderModels();renderReasoningChoices();renderDevicePreferences();renderShizuku();try{const cfg=await native('setCapabilities',p);state.config={...state.config,...cfg};for(const key of ['deviceScope','approvalMode','floatingEnabled'])if(p[key]!==undefined&&cfg[key]!==p[key])throw new Error('기기 설정 저장 결과를 확인하지 못했습니다. 다시 시도하세요.');if(p.enabledPlugins!==undefined&&(!Array.isArray(cfg.enabledPlugins)||JSON.stringify([...cfg.enabledPlugins].sort())!==JSON.stringify([...p.enabledPlugins].sort())))throw new Error('플러그인 저장 결과가 선택한 설정과 다릅니다. 저장된 상태를 확인하고 다시 시도하세요.');if(p.skillId!==undefined&&cfg.skillId!==p.skillId)throw new Error('응답 스타일 저장 결과를 확인하지 못했습니다. 다시 시도하세요.');if(p.activeSkillName!==undefined&&cfg.activeSkillName!==p.activeSkillName)throw new Error('선택한 스킬의 저장 결과를 확인하지 못했습니다. 다시 시도하세요.');selectMode();updateProviderSummary();renderToolInventory();updateWebStatus();if(p.model!==undefined){$('model').value=cfg.model||p.model;setConnected(false);renderProviderFields();}if(p.reasoningEffort!==undefined)$('reasoningEffort').value=cfg.reasoningEffort||p.reasoningEffort;toast('적용했습니다. 다음 메시지부터 사용합니다.');return true;}catch(e){toast(e.message,6500);if(p.model!==undefined)$('modelStatus').textContent=e.message;return false;}finally{preferenceSaving=false;renderDevicePreferences();renderShizuku();if(savingSheet)savingSheet.removeAttribute('aria-busy');$('saveDefaults').disabled=state.busy;$('capabilityApply').disabled=state.busy;$('capabilityApply').textContent='변경 사항 저장';syncComposerChoices();updateCapabilityDraft();renderToolInventory();renderModels();renderReasoningChoices();if(p.model!==undefined&&$('modelStatus').textContent==='모델 적용 중…')$('modelStatus').textContent=priorStatus;}}
$('capabilityApply').addEventListener('click',async()=>{const skill=$('skillOptions').querySelector('input:checked');const plugins=[...$('pluginOptions').querySelectorAll('input:checked')].map(e=>e.value);const sheet=activeSheet;const activeSkillName=capabilityDraft().activeSkillName;if(await savePreferences({skillId:skill?.value||'general',enabledPlugins:plugins,activeSkillName})&&activeSheet===sheet)closeActiveSheet();});
for(const [id,page] of [['sheetDevice','device'],['sheetMemory','memory'],['sheetApiSettings','settings']])$(id).addEventListener('click',()=>{closeActiveSheet(false);showPage(page);if(page==='settings')openSettingsPanel('Connection');});
function renderModels(){const q=$('modelSearch').value.trim().toLowerCase(),filtered=modelCatalog.filter(m=>String(m.name||m.id).toLowerCase().includes(q)||m.id.toLowerCase().includes(q));$('modelResults').replaceChildren();for(const m of filtered){const b=node('button','model-option'),copy=node('span','option-copy');b.type='button';b.disabled=state.busy||preferenceSaving;const selected=m.id===state.config.model;b.classList.toggle('selected',selected);b.setAttribute('aria-pressed',String(selected));copy.append(node('strong','',m.name||m.id));if(m.name&&m.name!==m.id)copy.append(node('small','',m.id));b.append(copy,node('span','model-check',selected?'✓':''));b.addEventListener('click',async()=>{const sheet=activeSheet;if(await savePreferences({model:m.id})&&activeSheet===sheet)closeActiveSheet();});$('modelResults').append(b);}if(!filtered.length&&modelCatalog.length)$('modelResults').append(node('p','field-help','검색 결과가 없습니다.'));}
async function loadModels(){if(modelLoading||state.busy)return;if(!state.config.endpoint){$('modelStatus').textContent='먼저 API 연결 설정을 저장하세요. 모델 설정에서 연결을 완료하세요.';return;}modelLoading=true;$('refreshModels').disabled=true;$('modelStatus').textContent='저장된 API에서 모델 목록을 불러오는 중…';const endpoint=state.config.endpoint,revision=configRevision;try{const response=await native('listModels');if(state.config.endpoint!==endpoint||configRevision!==revision)return;modelCatalog=(response.models||[]).filter(m=>typeof m.id==='string'&&m.id);modelCatalogEndpoint=endpoint;renderModels();$('modelStatus').textContent=modelCatalog.length?modelCatalog.length+'개 모델 · 위아래로 스크롤해 선택하세요.'+(response.truncated?' 목록 일부만 표시됩니다.':''):'API가 모델 목록을 반환하지 않았습니다. 모델 설정에서 ID를 직접 입력할 수 있습니다.';}catch(e){if(configRevision===revision)$('modelStatus').textContent=e.message+' 모델 설정에서 ID를 직접 입력할 수 있습니다.';}finally{modelLoading=false;$('refreshModels').disabled=state.busy;if(configRevision!==revision&&activeSheet===$('modelSheet'))loadModels();}}
function openModels(){if(state.busy||preferenceSaving){toast('실행이 끝난 뒤 모델을 변경하세요.');return;}if(!state.config.endpoint){showSetupGuide();return;}$('modelSearch').value='';if(modelCatalogEndpoint!==state.config.endpoint)modelCatalog=[];renderModels();openSheet('modelSheet');loadModels();}
function openReasoning(){if(state.busy||preferenceSaving)return;if(!hasConfiguredModel()){showSetupGuide();return;}$('sheetReasoning').value=state.config.reasoningEffort||'auto';renderReasoningChoices();openSheet('reasoningSheet');}
function goModelSettings(){closeActiveSheet(false);showPage('settings');openSettingsPanel('Connection');}
function showSetupGuide(){if(state.busy)return;openSheet('setupDialog');$('setupGoSettings').focus();}
$('setupGoSettings').addEventListener('click',goModelSettings);$('homeModelSetup').addEventListener('click',goModelSettings);
$('refreshModels').addEventListener('click',loadModels);$('modelSearch').addEventListener('input',renderModels);
const providerFallback=[
{id:'openrouter',name:'OpenRouter',endpoint:'https://openrouter.ai/api/v1',description:'Claude, GPT, Gemini 등 여러 제공자의 모델을 API 키 하나로 사용합니다.'},
{id:'openai-api',name:'OpenAI API',endpoint:'https://api.openai.com/v1',description:'도구 호출과 Chat Completions를 지원하는 OpenAI 모델을 사용합니다.'},
{id:'gemini',name:'Google AI Studio',endpoint:'https://generativelanguage.googleapis.com/v1beta/openai',description:'Gemini의 OpenAI 호환 API를 사용합니다.'},
{id:'deepseek',name:'DeepSeek',endpoint:'https://api.deepseek.com/v1',description:'DeepSeek API 키로 연결합니다.'},
{id:'groq',name:'Groq',endpoint:'https://api.groq.com/openai/v1',description:'Groq API 키로 연결합니다.'},
{id:'nvidia',name:'NVIDIA',endpoint:'https://integrate.api.nvidia.com/v1',description:'NVIDIA API 키로 연결합니다.'},
{id:'zai',name:'Z.AI / GLM',endpoint:'https://api.z.ai/api/paas/v4',description:'GLM 모델을 API 키로 연결합니다.'},
{id:'kimi-coding',name:'Kimi / Moonshot',endpoint:'https://api.moonshot.ai/v1',description:'Moonshot 플랫폼 API 키로 Kimi를 연결합니다.'},
{id:'kimi-coding-cn',name:'Kimi / Moonshot 중국',endpoint:'https://api.moonshot.cn/v1',description:'Moonshot 중국 플랫폼의 API 키로 연결합니다.'},
{id:'alibaba',name:'Qwen Cloud',endpoint:'https://dashscope-intl.aliyuncs.com/compatible-mode/v1',description:'Qwen의 OpenAI 호환 API로 연결합니다.'},
{id:'arcee',name:'Arcee AI',endpoint:'https://api.arcee.ai/api/v1',description:'Arcee API 키로 연결합니다.'},
{id:'gmi',name:'GMI Cloud',endpoint:'https://api.gmi-serving.com/v1',description:'GMI Cloud API 키로 연결합니다.'},
{id:'huggingface',name:'Hugging Face',endpoint:'https://router.huggingface.co/v1',description:'Hugging Face 라우터 API로 연결합니다.'},
{id:'xiaomi',name:'Xiaomi MiMo',endpoint:'https://api.xiaomimimo.com/v1',description:'Xiaomi MiMo API 키로 연결합니다.'},
{id:'custom',name:'사용자 지정 API',endpoint:'',description:'OpenAI 호환 API 주소와 키를 직접 입력합니다.'}];
function providers(){return state.capabilities?.providers||providerFallback;}
function providerForEndpoint(endpoint){if(!endpoint)return '';return providers().find(p=>p.endpoint&&p.endpoint.replace(/\/$/,'')===endpoint.replace(/\/$/,''))?.id||'custom';}
function initProviders(){const selected=$('providerSelect').value;$('providerSelect').replaceChildren();const first=node('option','','제공자 선택');first.value='';$('providerSelect').append(first);for(const p of providers()){const option=node('option','',p.name);option.value=p.id;$('providerSelect').append(option);}$('providerSelect').value=selected;renderProviderFields();}
function renderProviderFields(){const id=$('providerSelect').value,p=providers().find(p=>p.id===id);$('providerFields').classList.toggle('hidden',!p);$('providerDescription').textContent=p?.description||'제공자를 고르고 API 키를 연결하세요.';const custom=id==='custom';$('endpointDetails').open=custom;$('endpointDetails').classList.toggle('hidden',!custom);$('endpoint').readOnly=!custom;$('endpoint').required=!!p;const saved=!!state.config.endpoint&&state.config.providerId===id||!!state.config.endpoint&&!state.config.providerId&&providerForEndpoint(state.config.endpoint)===id;$('clearTokenWrap').classList.toggle('hidden',!saved||!state.config.hasToken);$('token').required=!!p&&p.id!=='custom'&&(!saved||!state.config.hasToken);const ready=saved&&(state.config.hasToken||id==='custom');$('settingsModelSection').classList.toggle('hidden',!ready);$('reasoningSettings').classList.toggle('hidden',!$('model').value.trim());$('settingsModelPicker').disabled=state.busy||!ready;$('providerSettingsDetails').open=!ready;}
function updateProviderSummary(){const context=Number(state.config.modelContextTokens),output=Number(state.config.modelOutputTokens);$('modelContextSummary').textContent=state.config.modelLimitsSource==='api'&&context>0?'모델 문맥 '+context.toLocaleString()+' 토큰'+(output>0?' · 출력 '+output.toLocaleString()+' 토큰':''):'문맥 한도는 API 응답에 따라 자동으로 적용됩니다.';const provider=providers().find(p=>p.id===(state.config.providerId||providerForEndpoint(state.config.endpoint)));$('settingsProviderName').textContent=provider?.name||'모델 연결';$('settingsConnectionState').textContent=state.config.model|| (state.config.endpoint?(state.config.hasToken||provider?.id==='custom'?'모델을 선택하세요':'API 키를 연결하세요'):'제공자를 선택해 시작하세요');$('settingsModelName').textContent=state.config.model||'모델 선택';$('reasoningSettings').classList.toggle('hidden',!state.config.model);$('reasoningSettings').querySelector('.field-help').textContent=reasoningMappingText(state.config.reasoningEffort||'auto');}
$('providerSelect').addEventListener('change',()=>{if(state.busy)return;const p=providers().find(p=>p.id===$('providerSelect').value);const savedId=state.config.providerId||providerForEndpoint(state.config.endpoint);if(p?.id===savedId){$('endpoint').value=state.config.endpoint||p.endpoint;$('model').value=state.config.model||'';$('reasoningEffort').value=state.config.reasoningEffort||'auto';$('tokenStatus').textContent=state.config.hasToken?'암호화된 인증 정보가 저장됨':'API 키를 입력하세요';}else{$('endpoint').value=p?.endpoint||'';$('model').value='';$('reasoningEffort').value='auto';$('tokenStatus').textContent='새 제공자의 API 키를 입력하세요';}$('token').value='';$('clearToken').checked=false;renderProviderFields();});
$('model').addEventListener('input',()=>{$('reasoningSettings').classList.toggle('hidden',!$('model').value.trim());});
$('settingsModelPicker').addEventListener('click',openModels);$('settingsCapabilities').addEventListener('click',openCapabilities);$('settingsMemory').addEventListener('click',()=>openSettingsDestination('memory','settingsMemory'));$('settingsDevice').addEventListener('click',()=>openSettingsDestination('permissions','settingsDevice'));$('settingsTools').addEventListener('click',()=>{openSettingsDestination('device','settingsTools');document.querySelector('.tool-workspace').scrollIntoView({block:'start'});});$('settingsHistory').addEventListener('click',()=>openSettingsDestination('history','settingsHistory'));
function reasoningMappingText(selected){if(selected===state.config.reasoningEffort&&state.config.effectiveReasoningEffort==='enabled')return '이 API에서는 생각 켜기로 적용됩니다. 단계별 강도 지원은 확인되지 않았습니다.';const provider=state.config.providerId||providerForEndpoint(state.config.endpoint);let effective=selected===state.config.reasoningEffort&&state.config.effectiveReasoningEffort?state.config.effectiveReasoningEffort:selected==='ultra'?'max':selected;if(provider==='gemini'&&['xhigh','max','ultra'].includes(selected))effective='high';if(provider==='deepseek'){if(selected==='minimal')effective='low';if(['xhigh','ultra'].includes(selected))effective='max';}return selected!==effective?'선택한 '+selected+'는 이 API에 '+effective+'로 전달합니다.':'지원 여부는 API와 모델에 따라 다릅니다.';}
function renderReasoningChoices(){const selected=$('sheetReasoning').value||'auto';for(const b of document.querySelectorAll('[data-reasoning-effort]')){const active=b.dataset.reasoningEffort===selected;b.classList.toggle('selected',active);b.setAttribute('aria-pressed',String(active));b.disabled=state.busy||preferenceSaving;}$('reasoningMapping').textContent=preferenceSaving&&activeSheet?.id==='reasoningSheet'?'생각 수준 적용 중…':reasoningMappingText(selected);}
function initReasoning(){const efforts=state.capabilities?.reasoningEfforts||['auto','none','minimal','low','medium','high','xhigh','max','ultra'];for(const id of ['reasoningEffort','sheetReasoning']){const selected=$(id).value;$(id).replaceChildren();for(const item of efforts){const value=typeof item==='string'?item:item.id;const label=value==='auto'?'자동 · 모델 기본값':(effortNames[value]||value)+' · '+value;const option=node('option','',label);option.value=value;$(id).append(option);}$(id).value=selected||'auto';}const levels=$('reasoningLevels');levels.replaceChildren();for(const item of efforts){const value=typeof item==='string'?item:item.id;const b=node('button','reasoning-option');b.type='button';b.dataset.reasoningEffort=value;b.append(node('strong','',value),node('small','',effortNames[value]||value));b.addEventListener('click',async()=>{if(state.busy||preferenceSaving)return;const sheet=activeSheet;$('sheetReasoning').value=value;renderReasoningChoices();if(await savePreferences({reasoningEffort:value})){if(activeSheet===sheet)closeActiveSheet();}else{$('sheetReasoning').value=state.config.reasoningEffort||'auto';renderReasoningChoices();}});levels.append(b);}renderReasoningChoices();}
$('saveDefaults').addEventListener('click',async()=>{const model=$('model').value.trim();if(!model){toast('기본 모델을 먼저 선택하세요.');openModels();return;}await savePreferences({model,reasoningEffort:$('reasoningEffort').value});});
$('newProviderConnection').addEventListener('click',()=>{if(state.busy)return;$('providerSelect').value='';$('endpoint').value='';$('token').value='';$('model').value='';$('reasoningEffort').value='auto';$('clearToken').checked=false;renderProviderFields();$('providerSettingsDetails').open=true;$('providerSelect').focus();});
function openSettingsDestination(page,control){const parent=state.settingsPanel;showPage(page);state.settingsDestinationParent=parent;state.returnToSettings=true;state.settingsOrigin=control;document.body.dataset.settingsSubpage=page;$('menuToggle').innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 12H5m6-6-6 6 6 6"/></svg>';$('menuToggle').setAttribute('aria-label','설정으로 돌아가기');}
function backToSettings(){const control=state.settingsOrigin,parent=state.settingsDestinationParent;showPage('settings');if(parent)openSettingsPanel(parent);$(control)?.focus();}
const settingsMenuIcon=$('menuToggle').innerHTML;
function openSettingsPanel(panel,fromBack=false){if(!['Connection','Appearance','About','Web','Skills','Tools','Terminal'].includes(panel))return;if(!fromBack&&state.settingsPanel&&state.settingsPanel!==panel)state.settingsTrail=[...(state.settingsTrail||[]),state.settingsPanel];state.settingsPanel=panel;$('settingsHome').classList.add('hidden');$('settingsDetail').classList.remove('hidden');for(const e of document.querySelectorAll('.settings-detail-panel'))e.classList.toggle('hidden',e.id!=='settingsPanel'+panel);const title={Connection:'모델 설정',Appearance:'화면 스타일',Advanced:'고급 설정',About:'앱 정보',Web:'웹 검색',Skills:'스킬 관리',Tools:'도구',Terminal:'터미널'}[panel];$('settingsDetailTitle').textContent=title;$('headerTitle').textContent=title;$('menuToggle').innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 12H5m6-6-6 6 6 6"/></svg>';$('menuToggle').setAttribute('aria-label','설정으로 돌아가기');document.body.dataset.settingsPanel=panel;$('main').scrollTop=0;if(panel==='Web')hydrateWebSettings();if(panel==='Skills')loadSavedSkills();if(panel==='Terminal'){renderTerminal();refreshTerminal();}}
function closeSettingsDetail(focus=true){if(focus&&state.settingsTrail?.length){const parent=state.settingsTrail.pop();openSettingsPanel(parent,true);return;}state.settingsTrail=[];const old=state.settingsPanel;state.settingsPanel='';$('settingsHome').classList.remove('hidden');$('settingsDetail').classList.add('hidden');$('menuToggle').innerHTML=settingsMenuIcon;$('menuToggle').setAttribute('aria-label','메뉴 열기');delete document.body.dataset.settingsPanel;if(state.page==='settings'){$('headerTitle').textContent='설정';$('main').scrollTop=0;}if(focus&&old)$(old==='Tools'?'settingsToolsHome':'settings'+old)?.focus();}
for(const panel of ['Connection','Appearance','About','Web','Skills','Terminal'])$('settings'+panel).addEventListener('click',()=>openSettingsPanel(panel));
$('settingsToolsHome').addEventListener('click',()=>openSettingsPanel('Tools'));
$('settingsPermissionsHome').addEventListener('click',()=>openSettingsDestination('permissions','settingsPermissionsHome'));
$('settingsBack').addEventListener('click',()=>closeSettingsDetail());
let savedSkills=[],savedSkillsLoading=false,skillSaving=false,editingSkill='';
function updateWebStatus(){$('webTokenStatus').textContent=state.config.hasWebToken?'암호화된 Tavily 키가 저장됨':'저장된 Tavily 키 없음';}
function renderWebProvider(){const tavily=$('webProvider').value==='tavily';$('webTavilyFields').classList.toggle('hidden',!tavily);$('webProviderHelp').textContent=tavily?'검색·페이지 읽기에 Tavily API 키를 사용합니다.':'키 없이 검색합니다. 검색 범위·최신성은 제한적이며 페이지 읽기는 Tavily 선택·키가 필요합니다.';}
function hydrateWebSettings(){updateWebStatus();$('webProvider').value=state.config.webProvider==='tavily'?'tavily':'mwmbl';$('webPluginEnabled').checked=(state.config.enabledPlugins||[]).includes('web');$('clearWebToken').checked=false;renderWebProvider();}
$('webProvider').addEventListener('change',renderWebProvider);
$('webSettingsForm').addEventListener('submit',async e=>{e.preventDefault();if(state.busy||preferenceSaving)return;const enabled=(state.config.enabledPlugins||['device','screen','root']).filter(id=>id!=='web');if($('webPluginEnabled').checked)enabled.push('web');const provider=$('webProvider').value,p={webProvider:provider,enabledPlugins:enabled};if(provider==='tavily'){if($('webToken').value)p.webToken=$('webToken').value;p.clearWebToken=$('clearWebToken').checked;}const button=$('saveWebSettings');for(const el of $('webSettingsForm').querySelectorAll('input,select,button'))el.disabled=true;button.textContent='저장 중…';try{if(await savePreferences(p)){if(provider==='tavily'){$('webToken').value='';$('clearWebToken').checked=false;}updateWebStatus();renderWebProvider();}}finally{for(const el of $('webSettingsForm').querySelectorAll('input,select,button'))el.disabled=state.busy;button.textContent='웹 검색 설정 저장';}});
function renderSavedSkillOptions(skills){const selected=state.config.activeSkillName||'';$('savedSkillOptions').replaceChildren();const items=[{name:'',description:'저장된 스킬을 대화에 추가하지 않습니다.'},...skills];if(selected&&!skills.some(s=>s.name===selected))items.push({name:selected,description:'선택한 스킬 파일을 확인하세요.'});for(const item of items){const label=node('label','sheet-row'),input=node('input');input.type='radio';input.name='activeSkillName';input.value=item.name;input.checked=item.name===selected;input.disabled=state.busy||preferenceSaving||savedSkillsLoading;input.addEventListener('change',updateCapabilityDraft);const copy=node('span','option-copy');copy.append(node('strong','',item.name||'선택 안 함'),node('small','',item.description));label.append(input,copy);$('savedSkillOptions').append(label);}if(!skills.length)$('savedSkillOptions').append(node('p','field-help',savedSkillsLoading?'저장된 스킬을 읽고 있습니다.':'스킬 관리에서 실제 SKILL.md를 만들 수 있습니다.'));}
function populateSkillEditor(result,open=true){editingSkill=result.name;skillResourceBinary=false;$('skillName').value=result.name;$('skillName').readOnly=true;$('skillDescription').value=result.description||'';$('skillContent').value=result.content||result.body||'';$('skillPackageName').value=result.name;syncSkillDocumentMode();updateSkillResources(result.files||[],result.skill_directory||'');$('deleteSkill').disabled=state.busy;if(open){$('skillEditPanel').open=true;$('skillEditPanel').scrollIntoView({block:'nearest'});}}
function renderSavedSkills(){
    const list=$('savedSkills');list.replaceChildren();
    for(const skill of savedSkills){const card=node('details','owned-document-card');card.dataset.skillName=skill.name;const heading=node('summary','owned-document-heading'),copy=node('span','row-text');copy.append(node('strong','',skill.name),node('small','',skill.description||''));heading.append(copy,node('span','chevron','⌄'));const content=node('div','owned-document-expanded');card.append(heading,content);let result=null,loading=false;
        card.addEventListener('toggle',async()=>{if(!card.open||result||loading)return;loading=true;content.textContent='읽는 중…';try{result=await native('skillsRead',{name:skill.name});if(!card.isConnected)return;content.replaceChildren();const full=node('pre','owned-document-content',result.content||result.body||'');full.tabIndex=0;content.append(full);const actions=node('div','button-pair');for(const [action,label] of [['edit','편집'],['files','패키지 파일'],['delete','삭제']]){const button=node('button','secondary',label);button.type='button';button.dataset.skillAction=action;button.addEventListener('click',()=>{if(state.busy||skillSaving)return;populateSkillEditor(result,action==='edit');if(action==='files'){$('skillResourcesPanel').open=true;$('skillResourcesPanel').scrollIntoView({block:'nearest'});}if(action==='delete')$('deleteSkill').click();});actions.append(button);}content.append(actions);}catch(e){content.textContent=e.message;}finally{loading=false;}});list.append(card);
    }
    if(!savedSkills.length)list.append(node('p','field-help','저장된 스킬이 없습니다.'));
}
async function loadSavedSkills(){if(savedSkillsLoading)return;savedSkillsLoading=true;$('refreshSkills').disabled=true;renderSavedSkillOptions(savedSkills);try{const result=await native('skillsList');savedSkills=Array.isArray(result)?result:result.skills||[];renderSavedSkills();}catch(e){$('savedSkills').replaceChildren(node('p','field-help',e.message));}finally{savedSkillsLoading=false;$('refreshSkills').disabled=state.busy;if(activeSheet?.id==='capabilitySheet'){renderSavedSkillOptions(savedSkills);updateCapabilityDraft();}}}
function newSkill(open=true){$('skillEditPanel').open=!!open;editingSkill='';skillResourceBinary=false;$('skillName').readOnly=false;$('skillName').value='';$('skillDescription').value='';$('skillContent').value='';$('skillPackageName').value='';$('skillDescription').readOnly=false;$('skillResourcesPanel').classList.add('hidden');$('skillResourcePath').value='';$('skillResourceContent').value='';syncSkillDocumentMode();$('deleteSkill').disabled=true;$('skillStatus').textContent='';if(open)$('skillName').focus();}
$('newSkill').addEventListener('click',newSkill);$('refreshSkills').addEventListener('click',loadSavedSkills);
$('skillEditorForm').addEventListener('submit',async e=>{e.preventDefault();if(state.busy||skillSaving)return;skillSaving=true;for(const el of $('skillEditorForm').querySelectorAll('input,textarea,button'))el.disabled=true;$('saveSkill').disabled=true;$('saveSkill').textContent='저장 중…';try{const result=await native('skillsSave',{name:$('skillName').value.trim(),description:$('skillDescription').value.trim(),content:$('skillContent').value});if(!result.saved)throw new Error('스킬 저장 결과를 확인하지 못했습니다.');editingSkill=result.name;$('skillName').readOnly=true;$('skillPackageName').value=result.name;await refreshSkillResources();syncSkillDocumentMode();if(result.config){state.config={...state.config,...result.config};renderToolInventory();}savedSkills=result.skills||savedSkills;renderSavedSkills();$('deleteSkill').disabled=false;$('skillStatus').textContent='SKILL.md로 저장했습니다. +의 내 스킬에서 선택하면 다음 대화부터 적용합니다.';}catch(e){$('skillStatus').textContent=e.message;}finally{skillSaving=false;for(const el of $('skillEditorForm').querySelectorAll('input,textarea,button'))el.disabled=state.busy;$('deleteSkill').disabled=!editingSkill||state.busy;$('saveSkill').disabled=state.busy;$('saveSkill').textContent='스킬 저장';renderSkillPackageControls();}});
$('deleteSkill').addEventListener('click',async()=>{if(!editingSkill||skillSaving||state.busy)return;const name=editingSkill;if(!window.confirm(name+' 스킬을 삭제할까요?'))return;skillSaving=true;for(const el of $('skillEditorForm').querySelectorAll('input,textarea,button'))el.disabled=true;$('deleteSkill').disabled=true;try{const result=await native('skillsDelete',{name});if(!result.deleted)throw new Error('스킬 삭제 결과를 확인하지 못했습니다.');if(result.config)state.config={...state.config,...result.config};if(state.config.activeSkillName===name)await savePreferences({activeSkillName:''});savedSkills=result.skills||savedSkills.filter(s=>s.name!==name);renderSavedSkills();renderToolInventory();newSkill(false);$('skillStatus').textContent='스킬을 삭제했습니다.';}catch(e){$('skillStatus').textContent=e.message;}finally{skillSaving=false;for(const el of $('skillEditorForm').querySelectorAll('input,textarea,button'))el.disabled=state.busy;$('deleteSkill').disabled=!editingSkill||state.busy;}});
$('sheetSkills').addEventListener('click',()=>{closeActiveSheet(false);showPage('settings');openSettingsPanel('Skills');});
$('devicePlugins').addEventListener('click',()=>{openCapabilities();document.querySelector('[data-capability-tab="plugins"]').click();});
const composerDraftKey='hermes-pocket-composer-draft';let restoredComposerDraft=null;
function persistComposerDraft(text,pendingSince=0){restoredComposerDraft=text?{text:text.slice(0,12000),pendingSince}:null;try{if(restoredComposerDraft)localStorage.setItem(composerDraftKey,JSON.stringify(restoredComposerDraft));else localStorage.removeItem(composerDraftKey);}catch(_){} }
function clearSubmittedDraft(text){if(restoredComposerDraft?.pendingSince&&restoredComposerDraft.text===text)clearComposerDraft();}
function clearComposerDraft(){restoredComposerDraft=null;try{localStorage.removeItem(composerDraftKey);}catch(_){} }
function restoreComposerDraft(){try{const record=JSON.parse(localStorage.getItem(composerDraftKey)||'null');if(record&&typeof record.text==='string'&&record.text.length<=12000){restoredComposerDraft=record;$('messageInput').value=record.text;const input=$('messageInput');input.style.height='28px';input.style.height=Math.min(input.scrollHeight,160)+'px';updateSend();}}catch(_){} }
function reconcileComposerDraft(){if(!restoredComposerDraft?.pendingSince)return;const accepted=state.messages.some(m=>m.role==='user'&&m.content===restoredComposerDraft.text&&Number(m.created)>=restoredComposerDraft.pendingSince);if(accepted){$('messageInput').value='';$('messageInput').style.height='28px';clearComposerDraft();}}
restoreComposerDraft();
const permissionIcons={accessibility:'<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="12" cy="10" r="2"/><path d="M7 17c1-4 9-4 10 0"/>',brightness:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>',notifications:'<path d="M6 10a6 6 0 0 1 12 0v5l2 2H4l2-2zM9 20h6"/>',apps:'<rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/>'};
for(const button of document.querySelectorAll('[data-permission]')){const icon=button.querySelector('.row-icon');if(icon){const key=button.dataset.permission||'apps';icon.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+permissionIcons[key]+'</svg>';}}
const memoryIcon=document.querySelector('.memory-intro .row-icon');if(memoryIcon)memoryIcon.innerHTML=svg('memory');
initProviders();initReasoning();
boot();

// Native terminal requests share the same plugin and approval gate as agent tools.
function terminalEnabled(){const inventory=state.config.availableTools;if(inventory&&Array.isArray(inventory.names))return inventory.names.includes('terminal')&&inventory.names.includes('process_manage');return (state.config.enabledPlugins||[]).includes('terminal');}
function renderTerminal(){const enabled=terminalEnabled(),blocked=state.busy||terminalUi.request||preferenceSaving;for(const el of $('terminalForm').querySelectorAll('input,textarea,select,button'))el.disabled=blocked||!enabled;$('terminalStop').disabled=!terminalUi.request;$('terminalStop').classList.toggle('hidden',!terminalUi.request);$('terminalRefresh').disabled=blocked||!enabled||terminalUi.statusLoading;$('terminalPluginNotice').classList.toggle('hidden',enabled);const t=state.terminal||{};$('terminalEnvironment').textContent=t.available===true?'Android 앱 샌드박스'+(t.cwd||t.workspace?' · '+(t.cwd||t.workspace):'')+(t.cwdPersistence===true&&t.environmentPersistence===true?' · 일반 포그라운드 명령의 cd·export 유지':'')+(t.pty===false?' · PTY 미지원':''):'실행 환경 확인 전 · 실제 실행은 Android APK에서 가능합니다';const shizuku=state.shizuku||{},ready=shizuku.available===true&&shizuku.enabled===true&&shizuku.serviceConnected===true&&shizuku.permissionGranted===true&&shizuku.binderAlive===true&&[0,2000].includes(shizuku.uid);$('terminalBackendStatus').textContent=$('terminalBackend').value==='shizuku'?(ready?'실제 Shizuku 연결 · UID '+shizuku.uid:'Shizuku 연결과 권한을 확인하세요. 앱 권한으로 자동 전환하지 않습니다.'):'앱 UID 권한으로 실행 · 다른 앱의 비공개 데이터에 접근할 수 없습니다.';for(const button of $('terminalJobs').querySelectorAll('button'))button.disabled=blocked||!enabled;}
function updateTerminalState(t){state.terminal={...t};terminalUi.jobs=Array.isArray(t.sessions)?t.sessions:[];renderTerminalJobs();renderTerminal();}
async function refreshTerminal(){if(terminalUi.statusLoading)return;terminalUi.statusLoading=true;renderTerminal();try{updateTerminalState(await native('terminalStatus'));}catch(e){$('terminalRequestStatus').textContent=e.message;}finally{terminalUi.statusLoading=false;renderTerminal();}}
function terminalResult(response){if(response?.ok===true&&response.result!==undefined)return response.result;return response;}
async function requestTerminalTool(name,args){if(state.busy||terminalUi.request||!terminalEnabled()){renderTerminal();return null;}terminalUi.request=true;terminalUi.requestSeq++;setBusy(true);$('terminalRequestStatus').textContent='실제 기기 응답 대기 중…';try{const response=await native('runTool',{name,arguments:args});$('terminalOutput').textContent=JSON.stringify(response,null,2);if(response?.ok===false)throw new Error(response.message||response.error||'명령 실행이 실패했습니다. 실제 응답을 확인하세요.');$('terminalRequestStatus').textContent='기기 응답을 받았습니다. 출력과 종료 상태를 확인하세요.';return terminalResult(response);}catch(e){$('terminalRequestStatus').textContent=e.message;return null;}finally{terminalUi.request=false;setBusy(false);await refreshTerminal();}}
function renderTerminalJobs(){const list=$('terminalJobs');list.replaceChildren();if(!terminalUi.jobs.length){list.append(node('p','field-help','기록된 프로세스가 없습니다.'));return;}for(const job of terminalUi.jobs){const id=job.session_id;if(typeof id!=='string'||!id)continue;const row=node('div','terminal-job');row.setAttribute('role','listitem');row.dataset.sessionId=id;row.append(node('p','',id),node('small','',JSON.stringify(job)));const actions=node('div','button-pair');for(const [action,label] of [['poll','상태·출력'],['kill','중단']]){const button=node('button','secondary',label);button.type='button';button.dataset.processAction=action;button.addEventListener('click',()=>requestTerminalTool('process_manage',{action,session_id:id,backend:job.backend==='shizuku'?'shizuku':'app',max_output_chars:12000}));actions.append(button);}row.append(actions);list.append(row);}}
$('terminalForm').addEventListener('submit',e=>{e.preventDefault();const command=$('terminalCommand').value,timeout=Number($('terminalTimeout').value);if(!command.trim()){ $('terminalRequestStatus').textContent='명령을 입력하세요.';return;}if(!Number.isInteger(timeout)||timeout<1||timeout>120){$('terminalRequestStatus').textContent='대기 한도는 1~120초로 입력하세요.';return;}requestTerminalTool('terminal',{command,backend:$('terminalBackend').value,background:$('terminalBackground').checked,timeout,max_output_chars:12000,pty:false});});
$('terminalBackend').addEventListener('change',renderTerminal);
$('terminalRefresh').addEventListener('click',refreshTerminal);
$('terminalStop').addEventListener('click',()=>{const sequence=terminalUi.requestSeq;native('stop').then(()=>{if(terminalUi.request&&terminalUi.requestSeq===sequence)$('terminalRequestStatus').textContent='중단을 요청했습니다. 실제 종료 상태를 확인하세요.';}).catch(e=>{if(terminalUi.request&&terminalUi.requestSeq===sequence)$('terminalRequestStatus').textContent=e.message;});});
$('terminalPlugins').addEventListener('click',openCapabilities);

function chatActivityKey(a){return [a.runId,a.toolCallId||a.kind,a.kind].join(':');}
const chatActivityStatus={started:'진행 중',approval:'승인 대기',completed:'완료',failed:'오류',cancelled:'중단됨'};
function fillChatActivity(card,a){if(a.kind==='response'&&a.text){card.dataset.activityKey=chatActivityKey(a);card.dataset.activityStatus=a.status;card.className='message assistant chat-public-response';let label=card.querySelector('.message-label');if(!label){card.replaceChildren();label=node('div','message-label');label.append(node('span','','hermes ›'));card.append(label,node('div','message-content'));}const body=card.querySelector('.message-content');if(card._publicText!==a.text){renderText(body,a.text);card._publicText=a.text;}let limit=card.querySelector('.public-response-limit');if(a.textTruncated){if(!limit){limit=node('small','public-response-limit');card.append(limit);}limit.textContent='긴 안내는 일부만 저장됨';}else limit?.remove();return card;}const open=card.querySelector('details')?.open===true;card.dataset.activityKey=chatActivityKey(a);card.dataset.activityStatus=a.status;card.className='chat-activity';const header=node('div','chat-activity-head');header.append(node('strong','',a.kind==='response'?'작업 설명':agentToolLabels[a.name]||a.name||'기기 작업'),node('span','chat-activity-status',['started','approval'].includes(a.status)&&!(state.busy&&state.activeRunId===a.runId)?'실행 상태 확인 필요':chatActivityStatus[a.status]||'상태 확인'));const summary=typeof a.summary==='string'?a.summary.slice(0,2000):'';card.replaceChildren(header);if(summary)card.append(node('p','chat-activity-summary',summary.slice(0,280)));const args=typeof a.argsSummary==='string'?a.argsSummary.slice(0,2000):'';if(args||summary.length>280){const detail=node('details','chat-activity-detail');detail.open=open;detail.append(node('summary','','작업 정보'));if(args)detail.append(node('p','',args));if(summary.length>280)detail.append(node('p','',summary));card.append(detail);}return card;}
function chatActivityNode(a){return fillChatActivity(node('article','chat-activity'),a);}
function sanitizeChatActivity(a,session){if(!a||!a.session||a.session!==session||typeof a.runId!=='string'||!a.runId||!['tool','response'].includes(a.kind)||!Object.prototype.hasOwnProperty.call(chatActivityStatus,a.status))return null;return {runId:a.runId.slice(0,200),session:a.session,toolCallId:typeof a.toolCallId==='string'?a.toolCallId.slice(0,200):'',seq:Number(a.seq)||0,firstSeq:Number(a.firstSeq||a.seq)||0,timelineOrder:Number(a.timelineOrder)||0,text:a.kind==='response'&&typeof a.text==='string'?a.text.slice(0,16000):'',textTruncated:a.kind==='response'&&a.textTruncated===true,kind:a.kind,name:typeof a.name==='string'?a.name.slice(0,150):'',status:a.status,summary:typeof a.summary==='string'?a.summary.slice(0,2000):'',argsSummary:typeof a.argsSummary==='string'?a.argsSummary.slice(0,2000):'',timestamp:Number(a.timestamp)||0,firstTimestamp:Number(a.firstTimestamp||a.timestamp)||0};}
function updateChatActivity(a){const safe=sanitizeChatActivity(a,state.sid);if(!safe)return;if(state.busy&&!state.activeRunId)state.activeRunId=safe.runId;const key=chatActivityKey(safe),index=state.activities.findIndex(item=>chatActivityKey(item)===key);const previous=index>=0?state.activities[index]:conversationTimeline.find(e=>e.kind==='activity'&&chatActivityKey(e.value)===key)?.value;safe.firstTimestamp=previous?.firstTimestamp||safe.firstTimestamp;safe.firstSeq=previous?.firstSeq||safe.firstSeq;safe.timelineOrder=previous?.timelineOrder||safe.timelineOrder;if(state.activeRunId&&safe.runId!==state.activeRunId)return;if(index>=0)state.activities[index]=safe;else {state.activities.push(safe);if(safe.kind==='response'&&safe.runId===state.activeRunId){state.live='';state.liveTimelineOrder=0;liveOffset=0;commitStreamEntry('activity',safe);}else appendTimelineEntry('activity',safe);}let card=[...$('conversation').querySelectorAll('[data-activity-key]')].find(e=>e.dataset.activityKey===key);if(card){fillChatActivity(card,safe);appendTimelineEntry('activity',safe);}else renderConversation();scrollBottom();}
async function loadChatActivities(session){state.activities=[];state.providerThoughts=[];stopModelProgressTimer();state.modelProgress=null;const [activities,thoughts]=await Promise.allSettled([native('activities',{session}),native('providerThoughts',{session})]);if(state.sid!==session)return;if(activities.status==='fulfilled'){const merged=new Map((Array.isArray(activities.value)?activities.value:[]).map(a=>sanitizeChatActivity(a,session)).filter(Boolean).map(a=>[chatActivityKey(a),a]));for(const a of state.activities){const old=merged.get(chatActivityKey(a));if(!old||a.seq>=old.seq)merged.set(chatActivityKey(a),a);}state.activities=[...merged.values()];}if(thoughts.status==='fulfilled'){const merged=new Map((Array.isArray(thoughts.value)?thoughts.value:[]).map(a=>sanitizeProviderThought(a,session)).filter(Boolean).map(a=>[providerThoughtKey(a),a]));for(const a of state.providerThoughts){const old=merged.get(providerThoughtKey(a));if(!old||a.timestamp>=old.timestamp)merged.set(providerThoughtKey(a),a);}state.providerThoughts=[...merged.values()];}restoreConversationTimeline();}

function syncSkillDocumentMode(){const full=$('skillContent').value.trimStart().startsWith('---');$('skillDescription').readOnly=full;$('skillDocumentMode').textContent=full?'전체 SKILL.md를 저장합니다. 이름·설명과 추가 메타데이터는 YAML 머리말 안에서 수정하세요.':'본문을 Markdown으로 입력하세요. 이름과 설명을 YAML 머리말로 함께 저장합니다.';renderSkillPackageControls();}
function renderSkillPackageControls(){const blocked=state.busy||skillSaving||skillPackageBusy||skillResourceBusy;for(const id of ['importSkill','skillPackageName','skillImportReplace'])$(id).disabled=blocked;$('exportSkill').disabled=blocked||!editingSkill;$('refreshSkillResources').disabled=blocked||!editingSkill;const tools=state.config.availableTools?.names;const writable=Array.isArray(tools)?tools.includes('skill_manage'):(state.config.enabledPlugins||[]).includes('agent');for(const el of $('skillResourceForm').querySelectorAll('input,textarea,button'))el.disabled=blocked||!editingSkill||!writable||skillResourceBinary;$('skillResourcePath').disabled=blocked||!editingSkill||!writable;for(const button of $('skillResourceList').querySelectorAll('button'))button.disabled=blocked;renderSkillLibraryControls();}
function updateSkillResources(files,directory=''){$('skillResourcesPanel').classList.toggle('hidden',!editingSkill);$('skillDirectory').textContent=directory;const list=$('skillResourceList');list.replaceChildren();for(const file of files){if(typeof file.file_path!=='string')continue;const button=node('button','setting-row');button.type='button';button.dataset.filePath=file.file_path;const text=node('span','row-text');text.append(node('strong','',file.file_path),node('small','',Number(file.bytes||0)+' bytes'));button.append(text,node('span','chevron','›'));button.addEventListener('click',()=>readSkillResource(file.file_path));list.append(button);}if(!files.length)list.append(node('p','field-help','지원 파일이 없습니다. 아래에서 새 텍스트 파일을 만들 수 있습니다.'));renderSkillPackageControls();}
async function refreshSkillResources(){if(!editingSkill)return;try{const files=await native('skillsResources',{name:editingSkill});updateSkillResources(Array.isArray(files)?files:[]);}catch(e){$('skillResourceStatus').textContent=e.message;}}
async function readSkillResource(path){if(state.busy||skillResourceBusy)return;skillResourceBusy=true;renderSkillPackageControls();try{const result=await native('skillsRead',{name:editingSkill,file_path:path});$('skillResourcePath').value=path;skillResourceBinary=result.encoding==='base64';$('skillResourceContent').value=skillResourceBinary?'':result.content||'';$('skillResourceStatus').textContent=skillResourceBinary?'바이너리 파일 · 텍스트 편집을 지원하지 않습니다. ZIP 내보내기에서 원본을 보존합니다.':'저장된 파일을 읽었습니다.';}catch(e){$('skillResourceStatus').textContent=e.message;}finally{skillResourceBusy=false;renderSkillPackageControls();}}
async function manageSkillResource(action){if(!editingSkill||state.busy||skillResourceBusy)return;const path=$('skillResourcePath').value.trim();if(!path){$('skillResourceStatus').textContent='파일 경로를 입력하세요.';return;}if(action==='remove_file'&&!window.confirm(path+' 파일을 삭제할까요?'))return;skillResourceBusy=true;setBusy(true);const operation={action,name:editingSkill,file_path:path};if(action==='write_file')operation.file_content=$('skillResourceContent').value;try{const result=await native('runTool',{name:'skill_manage',arguments:{operations:[operation]}});if(result?.ok===false)throw new Error(result.message||result.error||'파일 작업이 실패했습니다.');const applied=result?.ok===true?result.result:null,operations=applied?.operations;const confirmed=applied?.atomic===true&&applied.count===1&&Array.isArray(operations)&&operations.some(op=>op.action===action&&op.name===editingSkill&&op.file_path===path&&(action==='write_file'?op.written===true:op.removed===true));if(!confirmed)throw new Error('파일 작업 결과를 확인하지 못했습니다. 작성한 내용은 유지합니다.');$('skillResourceStatus').textContent=action==='write_file'?'파일을 저장했습니다.':'파일을 삭제했습니다.';if(action==='remove_file'){$('skillResourcePath').value='';$('skillResourceContent').value='';}await refreshSkillResources();}catch(e){$('skillResourceStatus').textContent=e.message;}finally{skillResourceBusy=false;setBusy(false);renderSkillPackageControls();}}
async function skillPackagePicker(exporting){if(state.busy||skillPackageBusy)return;const name=exporting?editingSkill:$('skillPackageName').value.trim();if(!/^[a-z0-9][a-z0-9_-]{0,63}$/.test(name)){ $('skillStatus').textContent='가져올 스킬 이름을 영문 소문자·숫자·-·_로 입력하세요.';return;}skillPackageBusy=true;renderSkillPackageControls();$('skillStatus').textContent='Android 파일 선택 대기 중…';try{const result=await native(exporting?'skillsExport':'skillsImport',exporting?{name}:{name,replace:$('skillImportReplace').checked});if(result.cancelled){$('skillStatus').textContent='파일 선택을 취소했습니다. 저장된 스킬은 바뀌지 않았습니다.';return;}if(exporting){if(result.exported!==true)throw new Error('ZIP 저장 결과를 확인하지 못했습니다.');$('skillStatus').textContent='선택한 위치에 실제 ZIP 패키지를 저장했습니다.';}else{if(result.imported!==true)throw new Error('스킬 가져오기 결과를 확인하지 못했습니다.');savedSkills=result.skills||savedSkills;if(result.config)state.config={...state.config,...result.config};renderSavedSkills();await openImportedSkill(result.name||name);$('skillStatus').textContent='SKILL.md와 패키지 파일을 가져왔습니다. +의 내 스킬에서 선택해 사용하세요.';}}catch(e){$('skillStatus').textContent=e.message;}finally{skillPackageBusy=false;renderSkillPackageControls();}}
async function openImportedSkill(name){$('skillEditPanel').open=true;const result=await native('skillsRead',{name});editingSkill=result.name;$('skillName').value=result.name;$('skillName').readOnly=true;$('skillPackageName').value=result.name;$('skillDescription').value=result.description||'';$('skillContent').value=result.content||result.body||'';skillResourceBinary=false;updateSkillResources(result.files||[],result.skill_directory||'');syncSkillDocumentMode();$('deleteSkill').disabled=state.busy;}
$('skillContent').addEventListener('input',syncSkillDocumentMode);
$('importSkill').addEventListener('click',()=>skillPackagePicker(false));$('exportSkill').addEventListener('click',()=>skillPackagePicker(true));
$('refreshSkillResources').addEventListener('click',refreshSkillResources);
$('skillResourcePath').addEventListener('input',()=>{skillResourceBinary=false;renderSkillPackageControls();});
$('skillResourceForm').addEventListener('submit',e=>{e.preventDefault();manageSkillResource('write_file');});$('deleteSkillResource').addEventListener('click',()=>manageSkillResource('remove_file'));

function renderSkillLibraryControls(){const blocked=state.busy||skillPackageBusy||skillSaving||skillLibraryUi.loading||skillLibraryUi.reading;$('skillLibraryInstall').disabled=blocked||skillLibraryUi.selected?.installable!==true;for(const button of document.querySelectorAll('#skillLibraryList button,#skillLibraryFiles button'))button.disabled=blocked;}
async function loadSkillLibrary(){if(skillLibraryUi.loading||skillLibraryUi.items.length)return;skillLibraryUi.loading=true;renderSkillLibraryControls();$('skillLibraryStatus').textContent='APK의 실제 원본 목록을 읽고 있습니다.';try{const result=await native('skillsLibrary');if(!Array.isArray(result.skills))throw new Error('원본 스킬 목록을 확인하지 못했습니다.');skillLibraryUi.items=result.skills;const summary=result.summary||{},count=Number(summary.package_count)||result.skills.length,installable=Number(summary.installable_count);$('skillLibraryStatus').textContent='원본 '+count+'개'+(Number.isFinite(installable)?' · 현재 가져오기 지원 '+installable+'개':'')+' · 설치는 개별 확인합니다.';renderSkillLibrary();}catch(e){$('skillLibraryStatus').textContent=e.message;}finally{skillLibraryUi.loading=false;renderSkillLibraryControls();}}
function renderSkillLibrary(){const query=$('skillLibrarySearch').value.toLowerCase().trim(),list=$('skillLibraryList');list.replaceChildren();for(const skill of skillLibraryUi.items){if(query&&![skill.name,skill.id,skill.description,skill.category,skill.scope].some(v=>String(v||'').toLowerCase().includes(query)))continue;const button=node('button','setting-row');button.type='button';button.dataset.libraryId=skill.id;const copy=node('span','row-text');copy.append(node('strong','',skill.name||skill.id),node('small','',(skill.scope||'')+' / '+(skill.category||'')+' · '+(skill.description||'')));button.append(copy,node('span','chevron','›'));button.addEventListener('click',()=>readSkillLibrary(skill.id));list.append(button);}if(!list.children.length)list.append(node('p','field-help','조건에 맞는 원본 스킬이 없습니다.'));renderSkillLibraryControls();}
async function readSkillLibrary(id,filePath){if(state.busy||skillLibraryUi.reading)return;const sequence=++skillLibraryUi.requestSeq;skillLibraryUi.reading=true;renderSkillLibraryControls();const status=$(filePath?'skillLibraryFileStatus':'skillLibraryReason');status.textContent='원본 파일을 읽고 있습니다.';try{const result=await native('skillsLibraryRead',filePath?{id,file_path:filePath}:{id});if(sequence!==skillLibraryUi.requestSeq)return;if(filePath){const binary=result.encoding==='base64';$('skillLibraryResource').textContent=binary?'바이너리 리소스는 텍스트 미리보기를 지원하지 않습니다. 패키지 설치·ZIP 저장에서 원본을 보존합니다.':typeof result.content==='string'?result.content:'';$('skillLibraryResource').classList.remove('hidden');status.textContent=filePath+' · '+Number(result.bytes||0)+' bytes';return;}if(typeof result.content!=='string'||result.id!==id)throw new Error('원본 문서를 확인하지 못했습니다.');skillLibraryUi.selected=result;$('skillLibraryDetail').classList.remove('hidden');$('skillLibraryName').textContent=result.name||id;$('skillLibraryContent').textContent=result.content;$('skillLibraryReason').textContent=result.installable===true?'원본 패키지를 내 스킬로 가져올 수 있습니다.':('현재 가져오기 제한: '+(Array.isArray(result.install_blockers)?result.install_blockers.join(' · '):'지원 여부 확인 필요'));$('skillLibraryFileStatus').textContent='';$('skillLibraryResource').classList.add('hidden');const files=$('skillLibraryFiles');files.replaceChildren();for(const file of result.files||[]){if(typeof file.path!=='string')continue;const button=node('button','setting-row');button.type='button';button.dataset.libraryFile=file.path;button.append(node('span','row-text',file.path+' · '+Number(file.bytes||0)+' bytes'));button.addEventListener('click',()=>readSkillLibrary(id,file.path));files.append(button);}}catch(e){status.textContent=e.message;}finally{skillLibraryUi.reading=false;renderSkillLibraryControls();}}
async function installSkillLibrary(){const selected=skillLibraryUi.selected;if(!selected||selected.installable!==true||state.busy||skillPackageBusy)return;skillPackageBusy=true;renderSkillPackageControls();$('skillLibraryReason').textContent='Android에서 가져오기를 확인하세요.';try{const result=await native('skillsLibraryInstall',{id:selected.id});if(result.cancelled){$('skillLibraryReason').textContent='가져오기를 취소했습니다.';return;}if(result.imported!==true||result.builtin_id!==selected.id||result.activated!==false)throw new Error('원본 패키지 저장 결과를 확인하지 못했습니다.');savedSkills=result.skills||savedSkills;if(result.config)state.config={...state.config,...result.config};renderSavedSkills();renderToolInventory();$('skillLibraryReason').textContent='원본 패키지를 내 스킬로 가져왔습니다. +의 내 스킬에서 직접 선택하세요.';}catch(e){$('skillLibraryReason').textContent=e.message;}finally{skillPackageBusy=false;renderSkillPackageControls();}}
$('skillLibraryPanel').addEventListener('toggle',()=>{if($('skillLibraryPanel').open)loadSkillLibrary();});$('skillLibrarySearch').addEventListener('input',renderSkillLibrary);$('skillLibraryInstall').addEventListener('click',installSkillLibrary);

const modelProgressLabels={sending:'모델 API에 요청 전송 중',receiving:'모델 응답 수신 중',thinking:'모델 생각 생성 중',tool_preparing:'도구 호출 준비 중',answering:'답변 작성 중',completed:'모델 응답 완료',failed:'모델 요청 오류',cancelled:'모델 요청 중단'};
function modelProgressElapsed(){const p=state.modelProgress;if(!p)return 0;return Math.max(0,p.elapsedMs+(p.running?performance.now()-p.receivedAt:0));}
function stopModelProgressTimer(){if(state.modelProgress){state.modelProgress.elapsedMs=modelProgressElapsed();state.modelProgress.running=false;}if(modelProgressTimer!==null){clearInterval(modelProgressTimer);modelProgressTimer=null;}renderModelProgressLabel();}
const thinkingBrain='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 5a3 3 0 0 0-5-2 4 4 0 0 0-3 6 4 4 0 0 0 0 6 4 4 0 0 0 5 5 3 3 0 0 0 3-2V5zm0 0a3 3 0 0 1 5-2 4 4 0 0 1 3 6 4 4 0 0 1 0 6 4 4 0 0 1-5 5 3 3 0 0 1-3-2M7 8l2 2m-3 5 3-1m8-6-2 2m3 5-3-1"/></svg>';
function thinkingCaption(elapsed,running){return 'thinking'+'.'.repeat(running?1+Math.floor(performance.now()/500)%3:3)+' '+Math.floor(Math.max(0,elapsed)/1000)+'s';}
function renderModelProgressLabel(){
    $('runStatus').classList.toggle('model-waiting',!!state.modelProgress?.running&&state.busy);
    const p=state.modelProgress,e=$('streamingMessage');
    for(const t of state.providerThoughts||[]){const card=[...$('conversation').querySelectorAll('[data-provider-thought-key]')].find(c=>c.dataset.providerThoughtKey===providerThoughtKey(t));if(card){const active=!!p&&state.busy&&p.runId===t.runId&&p.round===t.round&&p.running;card.querySelector('.thinking-caption').textContent=thinkingCaption(active?modelProgressElapsed():t.elapsedMs,active);}}
    if(!e)return;let label=$('chatModelProgress');
    const visibleThought=p&&(state.providerThoughts||[]).some(t=>t.runId===p.runId&&t.round===p.round);
    if(!p||p.session!==state.sid||p.runId!==state.activeRunId||visibleThought){label?.remove();return;}
    if(!label){label=node('span','chat-model-progress thinking-indicator');label.id='chatModelProgress';label.innerHTML=thinkingBrain;label.append(node('span','thinking-caption'));e.querySelector('.message-label').append(label);}
    label.querySelector('.thinking-caption').textContent=thinkingCaption(modelProgressElapsed(),p.running);
}
function updateModelProgress(a){if(!a||a.session!==state.sid||a.runId!==state.activeRunId||!Object.prototype.hasOwnProperty.call(modelProgressLabels,a.phase))return;const round=Number(a.round),elapsedMs=Number(a.elapsedMs);if(!Number.isInteger(round)||round<1||round>64||!Number.isFinite(elapsedMs)||elapsedMs<0)return;const terminal=['completed','failed','cancelled'].includes(a.phase);if(!state.busy&&!terminal)return;stopModelProgressTimer();state.progressStatus=modelProgressLabels[a.phase];$('runStatusText').textContent=state.progressStatus;state.modelProgress={session:a.session,runId:a.runId,phase:a.phase,round,elapsedMs,timestamp:Number(a.timestamp)||0,receivedAt:performance.now(),running:!terminal&&state.busy};if(state.busy&&state.page==='chat')renderStreaming();renderModelProgressLabel();if(state.modelProgress.running)modelProgressTimer=setInterval(renderModelProgressLabel,250);}
function sanitizeProviderThought(a,session){if(!a||a.session!==session||!session||a.provider!=='mimo'||a.source!=='mimo.reasoning_content'||typeof a.runId!=='string'||!a.runId||typeof a.text!=='string')return null;const round=Number(a.round);if(!Number.isInteger(round)||round<1||round>64)return null;return {session,runId:a.runId.slice(0,200),round,provider:'mimo',source:'mimo.reasoning_content',text:a.text.slice(0,32000),truncated:a.truncated===true||a.text.length>32000,elapsedMs:Number.isFinite(Number(a.elapsedMs))?Math.max(0,Number(a.elapsedMs)):0,timestamp:Number.isFinite(Number(a.timestamp))?Number(a.timestamp):0,firstTimestamp:Number.isFinite(Number(a.firstTimestamp||a.timestamp))?Number(a.firstTimestamp||a.timestamp):0,timelineOrder:Number(a.timelineOrder)||0};}
function providerThoughtKey(t){return t.runId+':'+t.round;}
function fillProviderThought(card,t){
    card.dataset.providerThoughtKey=providerThoughtKey(t);
    let detail=card.querySelector('.provider-thought-detail');if(!detail){detail=node('details','provider-thought-detail');const heading=node('summary','provider-thought-heading thinking-indicator');heading.innerHTML=thinkingBrain;heading.append(node('span','thinking-caption'),node('span','thinking-caret','⌄'));detail.append(heading,node('pre','provider-thought-body'));card.append(detail,node('p','provider-thought-limit'));}
    const p=state.modelProgress,active=!!p&&state.busy&&p.runId===t.runId&&p.round===t.round&&p.running;
    const caption=card.querySelector('.thinking-caption');caption.textContent=thinkingCaption(active?modelProgressElapsed():t.elapsedMs,active);
    const body=card.querySelector('.provider-thought-body'),scroll=body.scrollTop;if(body.textContent!==t.text){body.textContent=t.text;body.scrollTop=scroll;}
    card.querySelector('.provider-thought-limit').textContent=t.truncated?'긴 내용은 일부만 저장됨':'';return card;
}
function providerThoughtNode(t){return fillProviderThought(node('article','provider-thought'),t);}
function updateProviderThought(a){const safe=sanitizeProviderThought(a,state.sid);if(!safe||safe.runId!==state.activeRunId)return;const key=providerThoughtKey(safe),index=state.providerThoughts.findIndex(t=>providerThoughtKey(t)===key);const previous=index>=0?state.providerThoughts[index]:conversationTimeline.find(e=>e.kind==='thought'&&providerThoughtKey(e.value)===key)?.value;if(previous){safe.firstTimestamp=previous.firstTimestamp||safe.firstTimestamp;safe.timelineOrder=previous.timelineOrder||safe.timelineOrder;}if(index>=0)state.providerThoughts[index]=safe;else {state.providerThoughts.push(safe);appendTimelineEntry('thought',safe);}const card=[...$('conversation').querySelectorAll('[data-provider-thought-key]')].find(e=>e.dataset.providerThoughtKey===key);if(card){fillProviderThought(card,safe);appendTimelineEntry('thought',safe);}else renderConversation();scrollBottom();}
