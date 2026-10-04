const checks={};const token='ANDROID_PUBLIC_FIXTURE_TOKEN';
const assert=(ok,label)=>{checks[label]=!!ok;if(!ok)throw new Error('Check failed: '+label);};
const fixture={mode:'direct',providerId:'custom',endpoint:'http://127.0.0.1:8877/v1',model:'android-fixture',token,reasoningEffort:'high'};
let cfg=await native('saveSettings',fixture);
assert(cfg.providerId==='custom'&&cfg.hasToken,'actual_save_api_and_keystore');
const catalog=await native('listModels');assert(catalog.models.length===16,'actual_model_api_catalog_16');
await native('testConnection');checks.nonstream_api_connection=true;
cfg=await native('setCapabilities',{model:'fixture-model-14',skillId:'code',reasoningEffort:'high',enabledPlugins:['screen','root'],maxRounds:8,contextChars:48000,allowLan:true});
assert(cfg.reasoningEffort==='high'&&cfg.skillId==='code'&&!cfg.enabledPlugins.includes('device'),'native_defaults_selected');
async function waitIdle(){for(let i=0;i<100;i++){const b=await native('boot');if(!b.busy)return b;await new Promise(r=>setTimeout(r,100));}throw new Error('agent did not settle');}
async function run(text){await native('startChat',{text,session:''});const b=await waitIdle();const s=b.sessions.find(s=>s.title===text);assert(!!s,'session_'+text);return await native('messages',{session:s.id});}
const ordinary=await run('NativeOrdinary');assert(ordinary.some(m=>m.role==='assistant'&&m.content.includes('installed APK')),'actual_streamed_reply_stored');
const denied=await run('NativeDisabledToolCheck');checks.disabled_tool_run_completed=denied.some(m=>m.role==='assistant');
await native('startChat',{text:'NativeCancelCheck',session:''});await new Promise(r=>setTimeout(r,1000));await native('stop');const stopped=await waitIdle();const s=stopped.sessions.find(s=>s.title==='NativeCancelCheck');const partial=await native('messages',{session:s.id});
assert(partial.some(m=>m.content.includes('미완료'))&&!partial.some(m=>m.content.includes('This must not arrive')),'cancel_partial_preserved_tail_absent');
cfg=await native('saveSettings',{providerId:'openai-api',endpoint:'http://127.0.0.1:8877/override',token:''});
assert(cfg.endpoint==='https://api.openai.com/v1'&&!cfg.hasToken&&!cfg.model&&cfg.reasoningEffort==='auto','provider_change_key_model_effort_reset_builtin_endpoint_fixed');
await native('saveSettings',fixture);cfg=await native('setCapabilities',{skillId:'code',reasoningEffort:'high',enabledPlugins:['screen','root'],maxRounds:8,contextChars:48000,allowLan:true});
return {checks,version:(await native('boot')).version,config:{providerId:cfg.providerId,model:cfg.model,hasToken:cfg.hasToken,skillId:cfg.skillId,reasoningEffort:cfg.reasoningEffort,enabledPlugins:cfg.enabledPlugins,maxRounds:cfg.maxRounds,contextChars:cfg.contextChars,allowLan:cfg.allowLan},sessions:(await native('boot')).sessions.map(s=>({id:s.id,title:s.title,count:s.count}))};
