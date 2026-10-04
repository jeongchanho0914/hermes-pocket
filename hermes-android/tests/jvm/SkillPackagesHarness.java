package dev.chanho.hermes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Production package IO over real host files/ZIPs; loading never executes scripts. */
public final class SkillPackagesHarness {
    interface Work{void run()throws Exception;}
    static int passed;
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void test(String name,Work work)throws Exception{work.run();passed++;System.out.println("PASS "+name);}
    static Exception failure(Work work)throws Exception{try{work.run();}catch(Exception expected){return expected;}throw new AssertionError("Unsafe package operation accepted");}
    static LocalSkillStore store()throws Exception{return new LocalSkillStore(Files.createTempDirectory("hermes-skill-package-").toFile());}
    static String markdown(String name){return "---\nname: "+name+"\ndescription: Common YAML description\nlicense: MIT\nmetadata:\n  author: fixture\n---\n\n# Workflow\nRead linked resources before executing an explicitly approved task.\n";}
    static byte[] zip(String... pairs)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){for(int i=0;i<pairs.length;i+=2){zip.putNextEntry(new ZipEntry(pairs[i]));zip.write(pairs[i+1].getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}
        return bytes.toByteArray();
    }
    static Map<String,byte[]> entries(byte[] bytes)throws Exception{
        Map<String,byte[]> result=new LinkedHashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(!entry.isDirectory())result.put(entry.getName(),zip.readAllBytes());}}return result;
    }
    static void imports(LocalSkillStore store,String name,byte[] bytes,boolean replace)throws Exception{store.importPackage(name,new ByteArrayInputStream(bytes),replace);}
    static byte[] symlinkZip(byte[] input){
        byte[] bytes=input.clone();for(int i=0;i<bytes.length-46;i++)if(bytes[i]==0x50&&bytes[i+1]==0x4b&&bytes[i+2]==1&&bytes[i+3]==2){
            int length=(bytes[i+28]&255)|((bytes[i+29]&255)<<8);String name=new String(bytes,i+46,length,StandardCharsets.UTF_8);
            if(name.endsWith("references/link")){bytes[i+4]=20;bytes[i+5]=3;long mode=((long)0120777)<<16;for(int j=0;j<4;j++)bytes[i+38+j]=(byte)(mode>>(j*8));return bytes;}
        }throw new AssertionError("Fixture symlink central entry missing");
    }
    static byte[] duplicateZip(byte[] input){
        byte[] bytes=input.clone(),from="references/b.txt".getBytes(StandardCharsets.UTF_8),to="references/a.txt".getBytes(StandardCharsets.UTF_8);int replaced=0;
        for(int i=0;i<=bytes.length-from.length;i++){boolean match=true;for(int j=0;j<from.length;j++)if(bytes[i+j]!=from[j]){match=false;break;}if(match){System.arraycopy(to,0,bytes,i,to.length);replaced++;}}
        check(replaced==2,"fixture must patch local and central ZIP paths");return bytes;
    }
    public static void main(String[] unused)throws Exception{
        test("common YAML quoted and folded descriptions preserve exact user-authored SKILL.md",()->{
            LocalSkillStore store=store();
            for(String description:new String[]{"A plain description # YAML comment","'Owner''s workflow'","\"Quoted 한글 description\"",">-\n  First folded line\n  second folded line","|-\n  First literal line\n  second literal line"}){
                String source="\ufeff---\r\nname: 'External Community Name'\r\ndescription: "+description.replace("\n","\r\n")+"\r\nmetadata:\r\n  author: fixture\r\n---\r\n\r\n# Body 한글\r\n";
                store.save("community",null,source);JSONObject read=store.read("community");check(read.getString("content").equals(source),"YAML import rewrote owner bytes");
                check(!read.getBoolean("executable")&&read.getString("declared_name").equals("External Community Name"),"document grants execution or declared name lost");
                if(description.startsWith(">"))check(read.getString("description").equals("First folded line second folded line"),"folded description parsed incorrectly");
                if(description.startsWith("|"))check(read.getString("description").equals("First literal line\nsecond literal line"),"literal description parsed incorrectly");
            }
        });
        test("100000-character document boundary accepts full Unicode content and rejects oversized rewrite atomically",()->{
            LocalSkillStore store=store();String prefix="---\nname: boundary\ndescription: boundary\n---\n";String document=prefix+"한".repeat(100000-prefix.length());
            store.create("boundary",document);check(store.read("boundary").getString("content").equals(document),"valid 100k document lost Unicode");
            failure(()->store.rewrite("boundary",document+"x"));check(store.read("boundary").getString("content").equals(document),"oversized rewrite damaged old document");
            store.writeFile("boundary","references/large.txt","한".repeat(100000));failure(()->store.writeFile("boundary","references/large.txt","x".repeat(100001)));check(store.read("boundary","references/large.txt").getString("content").length()==100000,"oversized resource write damaged old file");
        });
        test("ZIP directory entries and nested Unicode resources roundtrip while preserving script bytes without executing",()->{
            LocalSkillStore store=store();String doc=markdown("package");String script="#!/system/bin/sh\nprintf 'explicit execution only\\n'\n";
            imports(store,"package",zip("community-package/","","community-package/SKILL.md",doc,"community-package/scripts/","","community-package/scripts/run.sh",script,"community-package/references/guide 한글.txt","linked reference\n"),false);
            check(store.read("package").getString("content").equals(doc),"ZIP SKILL.md rewritten");
            check(store.read("package","scripts/run.sh").getString("content").equals(script),"ZIP script bytes lost");
            check(store.read("package","references/guide 한글.txt").getString("content").equals("linked reference\n"),"Unicode resource name/content lost");
            ByteArrayOutputStream exported=new ByteArrayOutputStream();store.exportPackage("package",exported);Map<String,byte[]> all=entries(exported.toByteArray());
            check(all.size()==3,"export dropped or invented package files");
            LocalSkillStore reopened=store();imports(reopened,"copy",exported.toByteArray(),false);check(reopened.read("copy","scripts/run.sh").getString("content").equals(script),"export/import roundtrip script changed");
        });
        test("original LICENSE README and nested examples survive import read export while author mutations reject read-only paths",()->{
            LocalSkillStore store=store();String doc=markdown("original");
            imports(store,"original",zip("SKILL.md",doc,"LICENSE","Original license\n","README.md","Original readme\n","examples/nested/example.txt","Original example\n"),false);
            for(String path:new String[]{"LICENSE","README.md","examples/nested/example.txt"}){
                String before=store.read("original",path).getString("content");
                failure(()->store.writeFile("original",path,"modified"));failure(()->store.removeFile("original",path));failure(()->store.patch("original","Original","Modified",false,path));
                check(store.read("original",path).getString("content").equals(before),"read-only source resource was mutated");
            }
            ByteArrayOutputStream exported=new ByteArrayOutputStream();store.exportPackage("original",exported);
            LocalSkillStore copy=store();imports(copy,"copy",exported.toByteArray(),false);
            check(copy.read("copy","LICENSE").getString("content").equals("Original license\n")&&copy.read("copy","examples/nested/example.txt").getString("content").equals("Original example\n"),"original source extras lost on roundtrip");
        });
        test("ZIP traversal absolute paths and Unix symlink entries cannot escape or replace package",()->{
            LocalSkillStore store=store();store.create("safe",markdown("safe"));store.writeFile("safe","references/owner.txt","owner data");String before=store.read("safe").getString("content");
            for(String path:new String[]{"../escaped.txt","/absolute.txt","C:/windows.txt","safe/../../escaped.txt","safe\\..\\escaped.txt"}){
                byte[] bad=zip("SKILL.md",markdown("safe"),path,"escape");failure(()->imports(store,"safe",bad,true));
                check(store.read("safe").getString("content").equals(before)&&store.read("safe","references/owner.txt").getString("content").equals("owner data"),"rejected ZIP partially replaced owner package");
            }
            byte[] links=symlinkZip(zip("SKILL.md",markdown("safe"),"references/link","../../outside"));failure(()->imports(store,"safe",links,true));
            check(store.read("safe","references/owner.txt").getString("content").equals("owner data"),"symlink ZIP damaged package");
        });
        test("invalid replacement package preserves all existing document and resource bytes",()->{
            LocalSkillStore store=store();store.create("owner",markdown("owner"));store.writeFile("owner","scripts/original.sh","echo owner\n");
            String doc=store.read("owner").getString("content"),resource=store.read("owner","scripts/original.sh").getString("content");
            for(byte[] bad:Arrays.asList(zip("SKILL.md","malformed owner document","scripts/replacement.sh","echo replacement"),zip("one/SKILL.md",markdown("one"),"two/SKILL.md",markdown("two")))){
                failure(()->imports(store,"owner",bad,true));check(store.read("owner").getString("content").equals(doc)&&store.read("owner","scripts/original.sh").getString("content").equals(resource),"failed import destroyed old package");
            }
            failure(()->imports(store,"owner",zip("SKILL.md",markdown("different")),false));check(store.read("owner").getString("content").equals(doc),"nonreplace import overwrote owner doc");
            byte[] duplicate=duplicateZip(zip("SKILL.md",markdown("owner"),"references/a.txt","first","references/b.txt","second"));failure(()->imports(store,"owner",duplicate,true));
            failure(()->store.importMarkdown("owner",new ByteArrayInputStream(new byte[]{(byte)255,(byte)254}),true));check(store.read("owner").getString("content").equals(doc),"duplicate ZIP/invalid UTF8 Markdown damaged owner doc");
        });
        test("resource traversal and on-disk symlinks never read write or delete outside skill directory",()->{
            LocalSkillStore store=store();store.create("paths",markdown("paths"));Path outside=Files.createTempDirectory("hermes-resource-outside-");Path protectedFile=outside.resolve("owner.txt");Files.writeString(protectedFile,"outside-owned");
            Path skill=Path.of(store.read("paths").getString("skill_directory"));Files.createSymbolicLink(skill.resolve("references"),outside);
            failure(()->store.read("paths","references/owner.txt"));failure(()->store.writeFile("paths","references/new.txt","escape"));failure(()->store.removeFile("paths","references/owner.txt"));
            for(String path:new String[]{"../owner.txt","/absolute.txt","a/../../escape.txt","scripts\\escape.sh"})failure(()->store.writeFile("paths",path,"escape"));
            check(Files.readString(protectedFile).equals("outside-owned")&&!Files.exists(outside.resolve("new.txt")),"resource operation escaped via symlink");
        });
        test("binary resources survive ZIP export import and read reports base64 without pretending UTF8",()->{
            LocalSkillStore store=store();byte[] binary=new byte[]{0,(byte)255,(byte)254,42,13,10};ByteArrayOutputStream archive=new ByteArrayOutputStream();
            try(ZipOutputStream zip=new ZipOutputStream(archive)){zip.putNextEntry(new ZipEntry("SKILL.md"));zip.write(markdown("binary").getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.putNextEntry(new ZipEntry("assets/image.bin"));zip.write(binary);zip.closeEntry();}
            imports(store,"binary",archive.toByteArray(),false);JSONObject read=store.read("binary","assets/image.bin");
            check(read.getString("encoding").equals("base64")&&Arrays.equals(binary,Base64.getDecoder().decode(read.getString("content_base64"))),"binary resource corrupted or presented as text");
            ByteArrayOutputStream exported=new ByteArrayOutputStream();store.exportPackage("binary",exported);check(Arrays.equals(binary,entries(exported.toByteArray()).get("assets/image.bin")),"binary export altered bytes");
        });
        test("ZIP decompression file count and package limits reject before replacing owner bytes",()->{
            LocalSkillStore store=store();store.create("limits",markdown("limits"));store.writeFile("limits","references/keep.txt","owner retained");
            List<byte[]> archives=new ArrayList<>();archives.add(zip("SKILL.md",markdown("limits"),"assets/oversized.bin","x".repeat(1024*1024+1)));
            List<String> count=new ArrayList<>(Arrays.asList("SKILL.md",markdown("limits")));for(int i=0;i<256;i++){count.add("references/file-"+i+".txt");count.add("small");}archives.add(zip(count.toArray(new String[0])));
            List<String> size=new ArrayList<>(Arrays.asList("SKILL.md",markdown("limits")));for(int i=0;i<8;i++){size.add("assets/file-"+i+".bin");size.add("x".repeat(1024*1024));}archives.add(zip(size.toArray(new String[0])));
            for(byte[] bad:archives){failure(()->imports(store,"limits",bad,true));check(store.read("limits","references/keep.txt").getString("content").equals("owner retained"),"oversized ZIP partially replaced owner resources");}
        });
        test("canonical operation-array preview is read-only and one atomic commit creates patches writes reads removes deletes",()->{
            LocalSkillStore store=store();JSONArray create=J.arr(J.obj("action","create","name","batch","content",markdown("batch")),J.obj("action","write_file","name","batch","file_path","references/notes.txt","file_content","first first"));
            JSONObject preview=store.previewOperations(create);check(store.list().length()==0,"preview created real package before approval");
            store.applyOperations(create,preview.getString("fingerprint"));check(store.read("batch","references/notes.txt").getString("content").equals("first first"),"batch create/write missing");
            JSONArray patch=J.arr(J.obj("action","patch","name","batch","old_string","first","new_string","second","replace_all",true,"file_path","references/notes.txt"),J.obj("action","patch","name","batch","old_string","# Workflow","new_string","# Updated workflow"));
            store.applyOperations(patch,store.previewOperations(patch).getString("fingerprint"));check(store.read("batch","references/notes.txt").getString("content").equals("second second")&&store.read("batch").getString("content").contains("# Updated workflow"),"canonical patch operations lost resource/document update");
            JSONArray remove=J.arr(J.obj("action","remove_file","name","batch","file_path","references/notes.txt"),J.obj("action","delete","name","batch"));store.applyOperations(remove,store.previewOperations(remove).getString("fingerprint"));check(store.list().length()==0,"remove/delete left skill package");
        });
        test("invalid later batch operation and concurrent owner edit preserve every original package",()->{
            LocalSkillStore store=store();store.create("owner",markdown("owner"));store.writeFile("owner","references/keep.txt","owner original");
            JSONArray harmless=J.arr(J.obj("action","write_file","name","owner","file_path","references/keep.txt","file_content","proposed"));String fingerprint=store.previewOperations(harmless).getString("fingerprint");
            JSONArray invalid=J.arr(J.obj("action","create","name","partial","content",markdown("partial")),J.obj("action","write_file","name","owner","file_path","../outside","file_content","escape"));
            failure(()->store.previewOperations(invalid));failure(()->store.applyOperations(invalid,fingerprint));check(store.list().length()==1&&store.read("owner","references/keep.txt").getString("content").equals("owner original"),"invalid later operation committed earlier side effect");
            harmless.getJSONObject(0).put("file_content","changed after preview");failure(()->store.applyOperations(harmless,fingerprint));check(store.read("owner","references/keep.txt").getString("content").equals("owner original"),"changed operations reused approval for unreviewed content");harmless.getJSONObject(0).put("file_content","proposed");
            store.writeFile("owner","references/keep.txt","owner concurrent edit");failure(()->store.applyOperations(harmless,fingerprint));check(store.read("owner","references/keep.txt").getString("content").equals("owner concurrent edit"),"stale approval overwrote concurrent owner edit");
        });
        System.out.println("SkillPackages: "+passed+" production host file/ZIP checks passed; scripts are resources, not implicitly executed.");
    }
}
