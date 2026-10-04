package dev.chanho.hermes;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Private on-disk skill packages. Loading instructions never grants tool permissions. */
final class LocalSkillStore {
    static final int MAX_CONTENT=100000, MAX_SKILLS=256;
    private final File root;
    LocalSkillStore(File root) throws IOException {
        this.root=root.getCanonicalFile();
        if(!this.root.isDirectory()&&!this.root.mkdirs())throw new IOException("스킬 저장 폴더를 만들지 못했습니다.");
    }
    static String validName(String name){
        if(name==null||!name.matches("[a-z0-9][a-z0-9_-]{0,63}"))throw new IllegalArgumentException("스킬 이름은 영문 소문자·숫자·-·_로 1~64자 입력하세요.");
        return name;
    }
    File folder(String name) throws IOException {return SkillPackages.safeFile(root,validName(name));}
    private File document(String name) throws IOException {return SkillPackages.safeFile(folder(name),"SKILL.md");}
    synchronized JSONArray list() throws Exception {
        JSONArray out=new JSONArray();File[] folders=root.listFiles();if(folders==null)return out;
        Arrays.sort(folders,Comparator.comparing(File::getName));
        for(File folder:folders){
            if(out.length()>=MAX_SKILLS)break;
            if(!folder.isDirectory()||!folder.getName().matches("[a-z0-9][a-z0-9_-]{0,63}"))continue;
            try{JSONObject s=read(folder.getName());out.put(J.obj("name",folder.getName(),"description",s.getString("description"),"format","SKILL.md"));}
            catch(Exception ignored){/* One damaged package does not hide healthy skills. */}
        }
        return out;
    }
    synchronized JSONObject read(String name) throws Exception {
        String text=SkillPackages.readText(document(name),MAX_CONTENT*4+4096);
        JSONObject parsed=parseMarkdown(name,text);
        parsed.put("format","SKILL.md");parsed.put("executable",false);
        parsed.put("path",document(name).getAbsolutePath());parsed.put("skill_directory",folder(name).getAbsolutePath());
        parsed.put("files",resources(name));return parsed;
    }
    synchronized JSONObject read(String name,String filePath) throws Exception {
        if(filePath==null||filePath.isEmpty()||filePath.equals("SKILL.md"))return read(name);
        requireExists(name);return SkillPackages.readResource(folder(name),filePath);
    }
    synchronized JSONArray resources(String name) throws Exception {requireExists(name);return SkillPackages.resources(folder(name));}
    private void requireExists(String name) throws IOException {if(!document(name).isFile())throw new FileNotFoundException("저장된 스킬을 찾을 수 없습니다: "+name);}
    static JSONObject parseMarkdown(String name,String markdown) throws Exception {
        if(markdown==null||markdown.length()>MAX_CONTENT||markdown.indexOf('\0')>=0)throw new IllegalArgumentException("SKILL.md는 최대 100,000자입니다.");
        String text=markdown.startsWith("\ufeff")?markdown.substring(1):markdown;
        String normalized=text.replace("\r\n","\n");String[] lines=normalized.split("\n",-1);
        if(lines.length<4||!lines[0].equals("---"))throw new IllegalArgumentException("SKILL.md는 YAML 머리말(---), name, description, Markdown 본문이 필요합니다.");
        int end=1;while(end<lines.length&&!lines[end].equals("---"))end++;
        if(end==lines.length)throw new IllegalArgumentException("SKILL.md YAML 머리말이 닫히지 않았습니다.");
        String declared=null,description=null;
        for(int i=1;i<end;i++){
            String line=lines[i];if(line.startsWith("name:"))declared=scalar(line.substring(5).trim());
            if(line.startsWith("description:")){
                String value=line.substring(12).trim();
                if(value.matches("[>|][+-]?")){
                    StringBuilder b=new StringBuilder();boolean folded=value.charAt(0)=='>';
                    while(i+1<end&&(lines[i+1].startsWith(" ")||lines[i+1].isEmpty())){String part=lines[++i].trim();if(b.length()>0)b.append(folded?' ':'\n');b.append(part);}
                    description=b.toString().trim();
                }else description=scalar(value);
            }
        }
        if(declared==null||declared.isEmpty()||description==null||description.trim().isEmpty()||description.length()>1024)throw new IllegalArgumentException("YAML name과 description(1~1,024자)이 필요합니다.");
        StringBuilder body=new StringBuilder();for(int i=end+1;i<lines.length;i++){if(i>end+1)body.append('\n');body.append(lines[i]);}
        if(body.toString().trim().isEmpty())throw new IllegalArgumentException("스킬 Markdown 본문이 비어 있습니다.");
        return J.obj("name",name,"declared_name",declared,"description",description,"content",markdown,"body",body.toString().trim());
    }
    private static String scalar(String value) throws Exception {
        if(value.startsWith("\"")){Object parsed=new JSONTokener(value).nextValue();if(!(parsed instanceof String))throw new IllegalArgumentException("YAML 문자열 형식이 잘못되었습니다.");return (String)parsed;}
        if(value.startsWith("'")){if(!value.endsWith("'")||value.length()<2)throw new IllegalArgumentException("YAML 인용 문자열이 닫히지 않았습니다.");return value.substring(1,value.length()-1).replace("''","'");}
        if(value.startsWith("[")||value.startsWith("{")||value.startsWith("&")||value.startsWith("*")||value.startsWith("!")||value.matches("[>|].*"))throw new IllegalArgumentException("name/description은 일반 문자열 또는 인용/블록 문자열을 사용하세요.");
        int comment=value.indexOf(" #");return (comment<0?value:value.substring(0,comment)).trim();
    }
    synchronized JSONObject save(String name,String description,String content) throws Exception {
        validName(name);String markdown=content;
        if(content==null)throw new IllegalArgumentException("스킬 내용이 필요합니다.");
        if(!content.replace("\ufeff","").startsWith("---")){
            if(description==null||description.trim().isEmpty()||description.length()>1024||description.contains("\n")||description.contains("\r"))throw new IllegalArgumentException("설명은 한 줄로 1~1,024자 입력하세요.");
            markdown="---\nname: "+name+"\ndescription: "+JSONObject.quote(description.trim())+"\n---\n\n"+content+"\n";
        }
        parseMarkdown(name,markdown);File target=document(name);boolean existed=target.isFile();
        if(!existed&&list().length()>=MAX_SKILLS)throw new IllegalStateException("스킬은 최대 256개까지 저장할 수 있습니다.");
        SkillPackages.writeBytes(folder(name),"SKILL.md",markdown.getBytes(StandardCharsets.UTF_8));
        return J.obj("name",name,"saved",true,"replaced",existed,"format","SKILL.md","executable",false);
    }
    synchronized JSONObject create(String name,String content) throws Exception {if(document(name).exists())throw new IOException("이미 존재하는 스킬입니다: "+name);return save(name,null,content);}
    synchronized JSONObject rewrite(String name,String content) throws Exception {requireExists(name);return save(name,null,content);}
    synchronized JSONObject patch(String name,String oldString,String newString,boolean replaceAll,String filePath) throws Exception {
        requireExists(name);String path=filePath==null||filePath.isEmpty()?"SKILL.md":filePath;
        SkillPackages.writableResourceFile(folder(name),path);
        String before=path.equals("SKILL.md")?read(name).getString("content"):read(name,path).getString("content");
        if(oldString==null||oldString.isEmpty()||newString==null)throw new IllegalArgumentException("old_string과 new_string이 필요합니다.");
        int first=before.indexOf(oldString);if(first<0)throw new IllegalArgumentException("old_string을 찾을 수 없습니다.");
        if(!replaceAll&&before.indexOf(oldString,first+oldString.length())>=0)throw new IllegalArgumentException("old_string이 여러 번 나타납니다. 고유 문자열 또는 replace_all을 사용하세요.");
        String after=replaceAll?before.replace(oldString,newString):before.substring(0,first)+newString+before.substring(first+oldString.length());
        JSONObject result=path.equals("SKILL.md")?rewrite(name,after):writeFile(name,path,after);result.put("patched",true);return result;
    }
    synchronized JSONObject writeFile(String name,String path,String text) throws Exception {
        requireExists(name);SkillPackages.writableResourceFile(folder(name),path);if(path.equals("SKILL.md"))return rewrite(name,text);
        if(text==null||text.length()>MAX_CONTENT||text.indexOf('\0')>=0)throw new IllegalArgumentException("지원 파일은 최대 100,000자입니다.");
        SkillPackages.writeBytes(folder(name),path,text.getBytes(StandardCharsets.UTF_8));return J.obj("name",name,"file_path",path,"written",true);
    }
    synchronized JSONObject removeFile(String name,String path) throws Exception {requireExists(name);if(path.equals("SKILL.md"))throw new IllegalArgumentException("SKILL.md 삭제는 delete를 사용하세요.");File f=SkillPackages.writableResourceFile(folder(name),path);if(!f.isFile()||!f.delete())throw new IOException("지원 파일을 삭제하지 못했습니다.");return J.obj("name",name,"file_path",path,"removed",true);}
    synchronized boolean seedIfAbsent(String name,String description,String content) throws Exception {if(document(name).isFile()||list().length()>=MAX_SKILLS)return false;save(name,description,content);return true;}
    synchronized JSONObject delete(String name) throws Exception {requireExists(name);SkillPackages.deleteTree(folder(name));return J.obj("name",name,"deleted",true);}
    synchronized JSONObject importPackage(String name,InputStream input,boolean replace) throws Exception {
        validName(name);if(!document(name).exists()&&list().length()>=MAX_SKILLS)throw new IOException("스킬 저장 개수 한도 초과");return SkillPackages.importZip(this,name,input,replace);
    }
    synchronized JSONObject importMarkdown(String name,InputStream input,boolean replace) throws Exception {
        if(document(name).exists()&&!replace)throw new IOException("기존 스킬 교체 승인이 필요합니다.");
        String content=SkillPackages.decode(SkillPackages.boundedRead(input,MAX_CONTENT*4+4096));
        JSONObject result=save(name,null,content);result.put("imported",true);return result;
    }
    private String fingerprint() throws Exception {
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        fingerprintTree(root,root,digest);StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return hex.toString();
    }
    private static String approvalFingerprint(String state,JSONArray operations) throws Exception {
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");digest.update(state.getBytes(StandardCharsets.UTF_8));digest.update((byte)0);digest.update(operations.toString().getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static void fingerprintTree(File root,File dir,java.security.MessageDigest digest) throws Exception {
        File[] files=dir.listFiles();if(files==null)throw new IOException("스킬 폴더 읽기 실패");Arrays.sort(files,Comparator.comparing(File::getName));
        for(File file:files){String path=root.toPath().relativize(file.toPath()).toString();SkillPackages.safeFile(root,path);digest.update(path.getBytes(StandardCharsets.UTF_8));digest.update((byte)0);
            if(file.isDirectory()){digest.update((byte)1);fingerprintTree(root,file,digest);}else{digest.update((byte)2);try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);}digest.update((byte)0);}}
    }
    private static void copyTree(File source,File destination) throws IOException {
        if(!destination.isDirectory()&&!destination.mkdirs())throw new IOException("스킬 임시 폴더 생성 실패");File[] files=source.listFiles();if(files==null)throw new IOException("스킬 폴더 읽기 실패");
        for(File file:files){SkillPackages.safeFile(source,file.getName());File target=SkillPackages.safeFile(destination,file.getName());if(file.isDirectory())copyTree(file,target);else Files.copy(file.toPath(),target.toPath());}
    }
    synchronized JSONObject previewOperations(JSONArray operations) throws Exception {
        String before=fingerprint();File stage=Files.createTempDirectory(root.getParentFile().toPath(),".skills-preview-").toFile();
        try{copyTree(root,stage);JSONArray results=new LocalSkillStore(stage).executeOperations(operations);if(!before.equals(fingerprint()))throw new IOException("미리보기 중 스킬 파일이 변경되었습니다.");return J.obj("fingerprint",approvalFingerprint(before,operations),"preview",results,"operations",operations);}
        finally{SkillPackages.deleteTree(stage);}
    }
    synchronized JSONArray applyOperations(JSONArray operations,String expectedFingerprint) throws Exception {
        if(expectedFingerprint==null||!expectedFingerprint.equals(approvalFingerprint(fingerprint(),operations)))throw new IOException("승인 후 스킬 파일이 변경되었습니다. 다시 미리보기/승인하세요.");
        File stage=Files.createTempDirectory(root.getParentFile().toPath(),".skills-batch-").toFile();
        try{copyTree(root,stage);JSONArray results=new LocalSkillStore(stage).executeOperations(operations);if(!expectedFingerprint.equals(approvalFingerprint(fingerprint(),operations)))throw new IOException("작업 중 스킬 파일이 변경되었습니다.");SkillPackages.install(stage,root,true);return results;}
        finally{if(stage.exists())SkillPackages.deleteTree(stage);}
    }
    private JSONArray executeOperations(JSONArray operations) throws Exception {
        if(operations==null||operations.length()<1||operations.length()>32)throw new IllegalArgumentException("operations는 1~32개여야 합니다.");JSONArray results=new JSONArray();
        for(int i=0;i<operations.length();i++){
            JSONObject op=operations.getJSONObject(i);String action=op.getString("action"),name=validName(op.getString("name"));
            if(op.has("category")&&!op.optString("category").isEmpty())throw new IllegalArgumentException("Android에서는 category 하위 폴더를 아직 지원하지 않습니다.");
            JSONObject result;
            switch(action){
                case "create":result=create(name,op.getString("content"));break;
                case "save":result=save(name,op.optString("description",null),op.getString("content"));break;
                case "edit":result=rewrite(name,op.getString("content"));break;
                case "patch":if(op.has("content")){if(op.has("old_string")||op.has("new_string")||op.has("file_path"))throw new IllegalArgumentException("전체 rewrite와 문자열 patch 필드는 함께 사용할 수 없습니다.");result=rewrite(name,op.getString("content"));}else result=patch(name,op.getString("old_string"),op.getString("new_string"),op.optBoolean("replace_all",false),op.optString("file_path",null));break;
                case "write_file":result=writeFile(name,op.getString("file_path"),op.getString("file_content"));break;
                case "remove_file":result=removeFile(name,op.getString("file_path"));break;
                case "delete":if(op.has("absorbed_into")){String umbrella=validName(op.getString("absorbed_into"));if(umbrella.equals(name))throw new IllegalArgumentException("absorbed_into는 다른 존재하는 스킬이어야 합니다.");requireExists(umbrella);}result=delete(name);break;
                default:throw new IllegalArgumentException("지원하지 않는 스킬 작업: "+action);
            }
            result.put("action",action);results.put(result);
        }return results;
    }
    synchronized void exportPackage(String name,OutputStream output) throws Exception {requireExists(name);SkillPackages.exportZip(folder(name),output);}
}
