package dev.chanho.hermes;

import java.io.*;
import java.nio.file.*;
import org.json.*;

final class MemoryDocumentsHarness {
    static int passed;
    interface Action {void run() throws Exception;}
    static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);passed++;}
    static void refuses(Action action,String label) throws Exception {try{action.run();}catch(Exception expected){passed++;return;}throw new AssertionError(label);}
    public static void main(String[] args) throws Exception {
        File parent=Files.createTempDirectory("hermes-memory-test-").toFile(),root=new File(parent,"memories");
        try{
            final String[] synced={""};MemoryDocuments docs=new MemoryDocuments(root,"existing owner's note",text->synced[0]=text);
            check(docs.read("MEMORY.md").getString("content").equals("existing owner's note"),"legacy preserved");
            check(docs.list().length()==2&&docs.list().getJSONObject(0).getString("name").equals("USER.md"),"core list");
            docs.save("USER.md","Prefers Korean");docs.save("notes/task.md","# Verified workflow\nA real artifact");
            check(docs.list().length()==3&&docs.read("notes/task.md").getString("kind").equals("document"),"nested document");
            docs.save("MEMORY.md","Phone Android 16");check(synced[0].equals("Phone Android 16"),"legacy config synced");
            MemoryDocuments reopened=new MemoryDocuments(root,"stale legacy");check(reopened.context().contains("Prefers Korean")&&reopened.context().contains("Phone Android 16"),"reopen core context");
            check(!reopened.context().contains("A real artifact"),"extra docs demand only");
            refuses(()->docs.saveIfUnchanged("MEMORY.md","old note","bad write"),"stale approval rejected");
            refuses(()->docs.save("../escape.md","escape"),"traversal rejected");
            refuses(()->docs.save("bad.txt","not markdown"),"markdown only");
            refuses(()->docs.delete("USER.md"),"core deletion rejected");
            refuses(()->docs.save("USER.md",new String(new char[1376]).replace('\0','a')),"user budget");
            refuses(()->docs.save("MEMORY.md",new String(new char[4001]).replace('\0','a')),"memory budget");
            refuses(()->docs.save("notes/task.md","api_key = sk-123456789012345678901"),"credentials rejected");
            check(docs.read("notes/task.md").getString("content").contains("Verified workflow"),"rejected write preserved previous");
            File outside=new File(parent,"outside.md");Files.write(outside.toPath(),"outside".getBytes("UTF-8"));Files.createSymbolicLink(new File(root,"link.md").toPath(),outside.toPath());
            refuses(()->docs.read("link.md"),"symlink read rejected");refuses(()->docs.save("link.md","overwrite"),"symlink write rejected");Files.delete(new File(root,"link.md").toPath());
            docs.delete("notes/task.md");check(docs.list().length()==2,"custom deletion");
            docs.save("USER.md","");check(docs.read("USER.md").getString("content").isEmpty(),"core clear");
            JSONArray batch=J.arr(J.obj("action","remove","old_text","old"),J.obj("action","add","content","new"));
            check(LocalAgentTools.applyMemoryOperations("old",LocalAgentTools.memoryOperations(J.obj("target","memory","operations",batch))).equals("new"),"ordered atomic batch final result");
            refuses(()->LocalAgentTools.memoryOperations(J.obj("operations",batch,"action","add")),"batch single shape conflict");
            refuses(()->LocalAgentTools.applyMemoryOperations("repeated repeated",J.arr(J.obj("action","replace","old_text","repeated","content","new"))),"unique patch required");
            System.out.println("MemoryDocumentsHarness: "+passed+" checks passed");
        }finally{SkillPackages.deleteTree(parent);}
    }
}
