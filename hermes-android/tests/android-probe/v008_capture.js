// Emulator-only actual bridge probe. Runner must bind accessibility and open visual fixture during settling delay.
const checks={};const assert=(ok,id)=>{checks[id]=!!ok;if(!ok)throw Error(id);};
async function run(name,args={}){let result;try{result=await native('runTool',{name,arguments:args});}catch(e){result={ok:false,error:e.message};}for(let i=0;i<100&&(await native('boot')).busy;i++)await new Promise(r=>setTimeout(r,30));return result;}
await native('setCapabilities',{enabledPlugins:['device','screen','root','agent','web'],deviceScope:'all',approvalMode:'auto'});
await new Promise(r=>setTimeout(r,25000));
const device=await native('device');assert(device.accessibility,'actual_accessibility_service_bound');
const tree=await run('read_screen');const capture=await run('capture_screen');
return {version:(await native('boot')).version,checks,device,tree,capture};
