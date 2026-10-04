package dev.chanho.hermes;
import org.json.*;
import java.util.*;
final class ExtensionSchemas {
    static boolean handles(String name){return Arrays.asList("delegate_task","task_list","task_result","task_cancel","todo").contains(name);}
    static JSONArray jobs(){return J.arr(
        ToolArgs.schema("delegate_task","Start an independent read-only model worker for analysis, writing, reviewing code, or comparing supplied/local documents. Max 2 concurrent, 8 including queued; bounded 12 model rounds and 15 minutes. Worker cannot touch the screen, run commands, browse, contact MCP or recursively delegate. Parent must first gather default-browser evidence and pass relevant context. Uses the configured model API and may incur costs. Returns job_id; use task_result to obtain actual output. Native approval applies.",J.obj("task",ToolArgs.text(12000),"context",ToolArgs.text(20000),"wait_seconds",ToolArgs.integer(0,30)),"task"),
        ToolArgs.schema("task_list","List actual durable private worker jobs and their observed status. Does not invent progress or launch work.",J.obj()),
        ToolArgs.schema("task_result","Read an actual job result/status. Optionally wait at most 30 seconds. queued/running is not completed; interrupted jobs require a new explicit request and never replay themselves.",J.obj("job_id",ToolArgs.text(40),"wait_seconds",ToolArgs.integer(0,30)),"job_id"),
        ToolArgs.schema("task_cancel","Request cancellation of one private worker, including its in-flight HTTP request. Already completed work cannot be undone. Native owner approval applies.",J.obj("job_id",ToolArgs.text(40)),"job_id"),
        ToolArgs.schema("todo","Maintain a concise checklist for the current conversation. read returns saved plan; write replaces it. This is progress bookkeeping only and never schedules or executes a step. Mark completed only after matching evidence.",J.obj("action",ToolArgs.choice("read","write"),"items",J.obj("type","array","maxItems",32,"items",ToolArgs.object(J.obj("id",ToolArgs.text(40),"title",ToolArgs.text(300),"status",ToolArgs.choice("pending","in_progress","completed","blocked")),J.arr("id","title","status")))),"action")
    );}
    static JSONArray all(){return jobs().put(SemanticTarget.schema());}
}
