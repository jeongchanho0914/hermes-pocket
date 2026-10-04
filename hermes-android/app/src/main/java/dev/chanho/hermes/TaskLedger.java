package dev.chanho.hermes;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** App-private durable jobs. A restart never silently repeats an uncertain external operation. */
final class TaskLedger {
    private final File file;
    private final LinkedHashMap<String,JSONObject> rows=new LinkedHashMap<>();
    static final Set<String> ACTIVE=new HashSet<>(Arrays.asList("queued","running","cancelling"));
    TaskLedger(File directory) throws Exception {
        if(!directory.exists()&&!directory.mkdirs())throw new IOException("작업 기록 폴더를 만들지 못했습니다.");
        if(Files.isSymbolicLink(directory.toPath()))throw new SecurityException("작업 기록 폴더 자체의 심볼릭 링크는 허용하지 않습니다.");
        file=new File(directory.getCanonicalFile(),"jobs.json");
        if(Files.isSymbolicLink(file.toPath()))throw new SecurityException("작업 기록 링크는 허용하지 않습니다.");
        if(file.isFile()){
            if(file.length()>8*1024*1024)throw new IOException("작업 기록이 허용 크기를 넘었습니다.");
            JSONObject saved=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
            if(saved.optInt("schema")!=1)throw new IOException("지원하지 않는 작업 기록 버전입니다.");
            JSONArray jobs=saved.getJSONArray("jobs");if(jobs.length()>64)throw new IOException("작업 기록 개수가 올바르지 않습니다.");
            for(int i=0;i<jobs.length();i++){
                JSONObject job=jobs.getJSONObject(i);String id=job.getString("id");
                if(!id.matches("job_[a-f0-9]{32}")||rows.containsKey(id))throw new IOException("작업 식별자가 올바르지 않습니다.");
                if(ACTIVE.contains(job.optString("status"))){job.put("status","interrupted");job.put("phase","프로세스 종료로 중단됨 · 자동 재실행하지 않음");job.put("finishedAt",System.currentTimeMillis());}
                rows.put(id,job);
            }
            persist();
        }
    }
    synchronized String create(String goal,String parent,String channel) throws Exception {
        if(goal==null||goal.trim().isEmpty()||goal.length()>12000)throw new IllegalArgumentException("작업 요청은 1~12,000자여야 합니다.");
        int active=0;for(JSONObject job:rows.values())if(ACTIVE.contains(job.optString("status")))active++;
        if(active>=8)throw new IllegalStateException("독립 작업은 실행·대기를 합쳐 최대 8개입니다.");
        LinkedHashMap<String,JSONObject> original=new LinkedHashMap<>(rows);
        while(rows.size()>=64){String remove=null;for(Map.Entry<String,JSONObject> entry:rows.entrySet())if(!ACTIVE.contains(entry.getValue().optString("status"))){remove=entry.getKey();break;}if(remove==null)throw new IllegalStateException("작업 기록이 가득 찼습니다.");rows.remove(remove);}
        String id="job_"+UUID.randomUUID().toString().replace("-","");
        JSONObject job=J.obj("id",id,"goal",goal,"parentSession",parent==null?"":parent,"channel",channel,"status","queued","phase","실행 대기","createdAt",System.currentTimeMillis(),"updatedAt",System.currentTimeMillis(),"result","","error","","modelRequests",0,"usageKnown",false);
        rows.put(id,job);try{persist();}catch(Exception e){rows.clear();rows.putAll(original);throw e;}return id;
    }
    synchronized JSONObject get(String id) throws Exception {JSONObject job=rows.get(id);if(job==null)throw new IllegalArgumentException("해당 작업을 찾을 수 없습니다.");return new JSONObject(job.toString());}
    synchronized void update(String id,String status,String phase,String result,String error,JSONObject metrics) throws Exception {
        JSONObject old=get(id);String before=old.optString("status");
        if(!ACTIVE.contains(before))return; // Completion/cancellation races cannot overwrite a terminal outcome.
        if(!Arrays.asList("queued","running","cancelling","completed","failed","cancelled","interrupted").contains(status))throw new IllegalArgumentException("지원하지 않는 작업 상태입니다.");
        if("cancelling".equals(before)&&("running".equals(status)||"queued".equals(status)))return;
        if("cancelling".equals(before)&&"completed".equals(status))status="cancelled";
        JSONObject next=new JSONObject(old.toString());next.put("status",status);next.put("phase",J.clipped(phase,240));next.put("updatedAt",System.currentTimeMillis());
        if("running".equals(status)&&!next.has("startedAt"))next.put("startedAt",System.currentTimeMillis());
        if(!ACTIVE.contains(status))next.put("finishedAt",System.currentTimeMillis());
        if(result!=null)next.put("result",J.clipped(result,64000));if(error!=null)next.put("error",J.clipped(error,600));
        if(metrics!=null)for(String key:new String[]{"modelRequests","promptTokens","completionTokens","usageKnown","round","toolCalls"})if(metrics.has(key))next.put(key,metrics.get(key));
        rows.put(id,next);try{persist();}catch(Exception e){rows.put(id,old);throw e;}
    }
    synchronized JSONArray list(boolean full) throws Exception {
        JSONArray out=new JSONArray();List<JSONObject> jobs=new ArrayList<>(rows.values());Collections.reverse(jobs);
        for(JSONObject source:jobs){JSONObject job=new JSONObject(source.toString());if(!full){job.put("goal",J.clipped(job.optString("goal"),180));job.remove("result");}out.put(job);}return out;
    }
    private void persist() throws Exception {
        JSONArray jobs=new JSONArray();for(JSONObject job:rows.values())jobs.put(job);
        byte[] bytes=J.obj("schema",1,"jobs",jobs).toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>8*1024*1024)throw new IOException("작업 기록이 너무 큽니다.");
        File temp=new File(file.getParentFile(),"jobs-"+UUID.randomUUID()+".tmp");
        try{
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}
            try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp.toPath());}
    }
}
