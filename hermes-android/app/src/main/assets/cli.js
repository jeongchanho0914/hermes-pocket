'use strict';
// Local commands are interpreted on the phone; they are not sent as fake model/tool messages.
const jobUi={state:{active:0,jobs:[]},view:'',selected:'',refresh:0,revision:0};
const jobStatusLabels={queued:'대기',running:'실행 중',cancelling:'중단 처리 중',completed:'완료',failed:'오류',cancelled:'중단됨',interrupted:'앱 종료로 중단됨'};
function updatePrivateJobs(data){
    if(!data||!Array.isArray(data.jobs))return;
    jobUi.state=JSON.parse(JSON.stringify(data));jobUi.revision++;const button=$('privateJobsButton');
    if(button){button.textContent='작업'+(Number(data.active)>0?' '+data.active:'');button.setAttribute('aria-label','독립 작업 목록 · 실행 및 대기 '+(Number(data.active)||0)+'개');}
    if(jobUi.view==='list'&&!$('resultModal').classList.contains('hidden'))renderPrivateJobRows();
}
function cliModal(title){
    jobUi.view='';result(title,{});$('resultBody').textContent='';const content=$('resultBody');content.classList.add('cli-output');return content;
}
function cliHelp(){
    const out=cliModal('Hermes /help');
    out.textContent='메시지는 현재 모델로 보내고, / 명령은 휴대폰이 직접 처리합니다.\n\n'
        +'/help                 로컬 명령 안내\n/status               실제 실행·연결 상태\n/jobs                 독립 작업 목록·추가·중단\n/bg 작업 내용         읽기 전용 독립 에이전트 시작\n/result job_…         저장된 작업 결과\n/cancel job_…         독립 작업 하나 중단\n/stop                 현재 대화·독립 작업·터미널 모두 중단\n/plan                 현재 대화 체크리스트\n/tools                실제 활성 도구\n/skills               스킬 화면\n/memory               메모리 화면\n/compact              현재 대화 정리·원문 유지\n/new                  새 대화\n\n'
        +'독립 작업은 같은 모델 API를 사용하므로 추가 비용이 발생할 수 있습니다. 최대 2개 동시 실행, 실행·대기를 합쳐 8개, 작업당 최대 15분입니다. 독립 작업은 문서·메모리·스킬 읽기와 분석만 수행합니다. 휴대폰 화면과 기본 브라우저 조작은 메인 대화에서 순서대로 처리합니다.\n\n'
        +'표시된 시간·작업 상태는 실제 실행 기록입니다. 내부 작업 채널은 네트워크 수신 포트를 열지 않으며, 중단된 작업을 앱 재시작 후 자동 재실행하지 않습니다.';
}
async function showPrivateJobResult(id){
    const job=await native('jobResult',{job_id:id});const out=cliModal('Hermes 작업 결과');jobUi.view='detail';jobUi.selected=id;
    out.append(node('div','cli-job-meta',id+'\n'+(jobStatusLabels[job.status]||job.status)+' · '+(job.phase||'')));
    out.append(node('p','',job.goal||''));
    const body=node('div','cli-job-result');renderText(body,typeof job.result==='string'&&job.result?job.result:job.error||'아직 최종 결과가 없습니다.');out.append(body);
    const metrics=node('small','',job.usageKnown===true?'실제 토큰 · 입력 '+job.promptTokens+' / 출력 '+job.completionTokens:'토큰 사용량 미확인');out.append(metrics);
    const actions=node('div','cli-job-actions');
    const refresh=node('button','secondary','새로고침');refresh.type='button';refresh.addEventListener('click',()=>showPrivateJobResult(id).catch(e=>toast(e.message)));actions.append(refresh);
    const copy=node('button','secondary','결과 복사');copy.type='button';copy.disabled=!job.result;copy.addEventListener('click',()=>copyMessage(job.result));actions.append(copy);
    const back=node('button','secondary','목록');back.type='button';back.addEventListener('click',()=>showPrivateJobs().catch(e=>toast(e.message)));actions.append(back);out.append(actions);
}
function renderPrivateJobRows(){
    const list=$('privateJobRows');if(!list)return;
    list.replaceChildren();
    for(const job of jobUi.state.jobs){
        if(typeof job.id!=='string'||!/^job_[a-f0-9]{32}$/.test(job.id))continue;
        const row=node('article','cli-job-row');row.dataset.jobId=job.id;
        row.append(node('strong','',(jobStatusLabels[job.status]||'상태 확인')+' · '+(job.goal||'')),node('small','',job.id),node('p','',job.phase||''));
        const actions=node('div','cli-job-actions');const open=node('button','secondary','결과');open.type='button';open.addEventListener('click',()=>showPrivateJobResult(job.id).catch(e=>toast(e.message)));actions.append(open);
        if(['queued','running','cancelling'].includes(job.status)){
            const cancel=node('button','secondary',job.status==='cancelling'?'중단 처리 중':'중단');cancel.type='button';cancel.disabled=job.status==='cancelling';
            cancel.addEventListener('click',async()=>{cancel.disabled=true;try{const response=await native('cancelJob',{job_id:job.id});if(response.job?.id===job.id)updatePrivateJobs({...jobUi.state,jobs:jobUi.state.jobs.map(item=>item.id===job.id?response.job:item)});await refreshPrivateJobs();}catch(e){toast(e.message);cancel.disabled=false;}});actions.append(cancel);
        }
        row.append(actions);list.append(row);
    }
    if(!list.children.length)list.append(node('p','',jobUi.state.error||'기록된 독립 작업이 없습니다.'));
}
async function refreshPrivateJobs(){const sequence=++jobUi.refresh,revision=jobUi.revision;const data=await native('jobs');if(sequence===jobUi.refresh&&revision===jobUi.revision)updatePrivateJobs(data);}
async function showPrivateJobs(){
    const out=cliModal('Hermes 작업 채널');jobUi.view='list';
    out.append(node('p','cli-job-notice','사용자 대화와 독립 작업을 분리합니다. 화면 조작은 메인 대화에서 수행합니다.'));
    const form=node('form','cli-job-form'),input=node('textarea','');input.rows=3;input.maxLength=12000;input.placeholder='별도로 분석하거나 작성할 작업';input.setAttribute('aria-label','독립 작업 요청');
    const send=node('button','primary','독립 작업 시작');send.type='submit';
    const status=node('p','cli-job-notice','같은 모델 API를 사용하며 추가 비용이 발생할 수 있습니다.');
    form.append(input,send,status);form.addEventListener('submit',async event=>{
        event.preventDefault();const task=input.value.trim();if(!task)return;send.disabled=true;
        try{const response=await native('startBackgroundTask',{task,session:state.sid});input.value='';status.textContent='시작됨 · '+response.job_id;updatePrivateJobs(response.jobs);}
        catch(e){status.textContent=e.message;}finally{send.disabled=false;}
    });out.append(form);
    const refresh=node('button','secondary','목록 새로고침');refresh.type='button';refresh.addEventListener('click',()=>refreshPrivateJobs().catch(e=>toast(e.message)));out.append(refresh);
    const rows=node('div','');rows.id='privateJobRows';out.append(rows);await refreshPrivateJobs();
}
async function handleCliCommand(text){
    const match=/^\/(help|status|jobs|bg|result|cancel|stop|plan|tools|skills|memory|compact|new)(?:\s+([\s\S]*))?$/.exec(text);
    if(!match)return false;
    const command=match[1],argument=(match[2]||'').trim();
    try{
        switch(command){
            case 'help':cliHelp();break;
            case 'jobs':await showPrivateJobs();break;
            case 'bg':{
                if(!argument)throw new Error('/bg 뒤에 수행할 작업을 입력하세요.');
                const response=await native('startBackgroundTask',{task:argument,session:state.sid});updatePrivateJobs(response.jobs);await showPrivateJobs();toast('독립 작업을 시작했습니다.');break;
            }
            case 'result':if(!/^job_[a-f0-9]{32}$/.test(argument))throw new Error('/result 뒤에 /jobs의 정확한 작업 ID를 입력하세요.');await showPrivateJobResult(argument);break;
            case 'cancel':if(!/^job_[a-f0-9]{32}$/.test(argument))throw new Error('/cancel 뒤에 정확한 작업 ID를 입력하세요.');await native('cancelJob',{job_id:argument});await showPrivateJobs();break;
            case 'stop':await native('stop');toast('모든 실행 작업에 중단을 요청했습니다.');break;
            case 'status':{
                const current=await native('boot');const out=cliModal('Hermes 실제 상태');out.textContent=JSON.stringify({version:current.version,model:current.config?.model,foregroundBusy:current.busy,status:current.status,jobs:current.jobs,terminal:current.terminal,channels:{user:'대화',device:'단일 화면 실행',worker:'읽기 전용 독립 작업'},localListeningPorts:0},null,2);break;
            }
            case 'tools':{const current=await native('boot');const out=cliModal('Hermes 활성 도구');out.textContent=(current.availableTools?.names||[]).join('\n');break;}
            case 'plan':{const plan=await native('plan',{session:state.sid});const out=cliModal('Hermes 현재 계획');out.textContent=JSON.stringify(plan,null,2);break;}
            case 'skills':showPage('settings');openSettingsPanel('Skills');break;
            case 'memory':showPage('memory');break;
            case 'compact':if(state.busy)throw new Error('현재 대화 작업이 끝난 뒤 정리하세요.');$('compactConversation').click();break;
            case 'new':if(state.busy)throw new Error('현재 대화 작업이 끝난 뒤 새 대화를 여세요.');startNewChat();break;
        }
        $('messageInput').value='';clearComposerDraft();updateSend();return true;
    }catch(e){toast(e.message,6500);return true;}
}
Object.assign(agentToolLabels,{act_on_screen:'screen.act',browser_search:'browser.search',browser_open:'browser.open',browser_snapshot:'browser.snapshot',delegate_task:'agent.delegate',task_list:'jobs.list',task_result:'jobs.result',task_cancel:'jobs.cancel',todo:'plan'});
const jobsButton=node('button','composer-chip','작업');jobsButton.id='privateJobsButton';jobsButton.type='button';jobsButton.addEventListener('click',()=>showPrivateJobs().catch(e=>toast(e.message)));
$('composerTools').insertAdjacentElement('afterend',jobsButton);
const helpButton=node('button','composer-chip','/');helpButton.type='button';helpButton.setAttribute('aria-label','Hermes 로컬 명령 안내');helpButton.addEventListener('click',cliHelp);jobsButton.insertAdjacentElement('afterend',helpButton);
document.body.dataset.transcript='cli';
if(window.NativeBridge)refreshPrivateJobs().catch(()=>{});

function isCliInput(text){return /^\/(help|status|jobs|bg|result|cancel|stop|plan|tools|skills|memory|compact|new)(?:\s|$)/.test(text.trim());}
updateSend();
