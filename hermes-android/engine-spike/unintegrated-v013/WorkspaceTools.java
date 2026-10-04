package dev.chanho.hermes;
import org.json.*;
import java.io.File;

/** Native approval adapter for the app-private code/document workspace. */
final class WorkspaceTools {
    final WorkspaceFiles files;
    private final AgentRuntime runtime;
    WorkspaceTools(AgentRuntime runtime){this.runtime=runtime;try{files=new WorkspaceFiles(new File(runtime.context.getFilesDir(),"workspace"));}catch(Exception e){throw new IllegalStateException("개인 작업 폴더를 열지 못했습니다.",e);}}
    private void gate(String name)throws Exception{
        runtime.checkCancelled();if(!runtime.busy()||!runtime.isUnlocked()||!LocalCapabilities.allowed(name,runtime.store.config()))throw new SecurityException("활성 요청·잠금 해제·에이전트 도구 허용이 필요합니다.");
    }
    JSONObject readOnly(String name,JSONObject args)throws Exception{
        ToolArgs.validate(name,args,WorkspaceSchemas.all());
        if("read_file".equals(name))return files.read(args.getString("path"),args.optInt("start_line",1),args.optInt("num_lines",200));
        if("search_files".equals(name))return files.search(args.optString("path",""),args.optString("query",""),"content".equals(args.optString("mode","name")),args.optInt("limit",30));
        throw new SecurityException("읽기 전용 작업에 허용되지 않는 파일 도구입니다.");
    }
    JSONObject execute(String name,JSONObject args)throws Exception{
        ToolArgs.validate(name,args,WorkspaceSchemas.all());gate(name);
        if(WorkspaceSchemas.readOnly(name))return J.obj("ok",true,"result",readOnly(name,args));
        String path=args.getString("path"),expected=args.optString("expected_sha256","");
        String content="write_file".equals(name)?args.getString("content"):files.patchedContent(path,args.getString("old_string"),args.getString("new_string"),args.optBoolean("replace_all",false),expected);
        JSONObject preview=files.previewWrite(path,content,expected);String approval=runtime.store.approvalMode();
        runtime.emit("tool",J.obj("name",name,"status","승인 대기"));
        if(!runtime.approvals.ask("작업 폴더 파일 저장",path+"\n"+preview.getLong("bytes")+" bytes\n\n앱의 개인 작업 폴더에 저장합니다. 기존 파일은 읽었을 때의 SHA-256이 같아야 바뀝니다. 코드를 저장해도 자동으로 실행하지 않습니다.\n\n"+J.clipped(content,2500),false))return J.obj("ok",false,"denied",true,"error","파일 저장을 승인하지 않았습니다.");
        gate(name);if(!approval.equals(runtime.store.approvalMode()))throw new SecurityException("승인 방식이 변경되었습니다.");
        return J.obj("ok",true,"result",files.write(path,content,expected));
    }
}
