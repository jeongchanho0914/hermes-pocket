package dev.chanho.hermes;
import android.content.res.AssetManager;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Production library over the actual shipped packages and real host skill folders. */
public final class BuiltinSkillLibraryHarness {
 interface Work{void run()throws Exception;} static int passed;
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 static void test(String name,Work work)throws Exception{work.run();passed++;System.out.println("PASS "+name);}
 static void reject(Work work)throws Exception{try{work.run();}catch(Exception expected){return;}throw new AssertionError("Unverified library operation accepted");}
 static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 static LocalSkillStore store()throws Exception{return new LocalSkillStore(Files.createTempDirectory("hermes-original-library-").toFile());}
 public static void main(String[] args)throws Exception{
  File assets=new File("app/src/main/assets");BuiltinSkillLibrary library=new BuiltinSkillLibrary(new AssetManager(assets));JSONArray entries=library.list();
  test("actual catalog exposes 210 original packages with 209 eligible and one honest size blocker",()->{
   check(entries.length()==210&&library.summary().getInt("package_count")==210&&library.summary().getInt("installable_count")==209,"catalog counts disagree");
   int blocked=0;Set<String> ids=new HashSet<>();for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);check(ids.add(e.getString("id")),"duplicate original ID");if(!e.getBoolean("installable")){blocked++;check(e.getJSONArray("install_blockers").length()>0,"blocked package missing reason");}}
   check(blocked==1,"eligibility false claims");entries.getJSONObject(0).put("name","fixture-mutated");check(!library.list().getJSONObject(0).getString("name").equals("fixture-mutated"),"caller mutated cached catalog");
  });
  test("all 210 document reads preserve audited original bytes without installation or execution",()->{
   JSONArray original=library.list();LocalSkillStore untouched=store();for(int i=0;i<original.length();i++){JSONObject e=original.getJSONObject(i),read=library.read(e.getString("id"));check(!read.getBoolean("executable")&&read.getString("file_path").equals("SKILL.md"),"document read grants execution");check(sha(read.getString("content").getBytes(StandardCharsets.UTF_8)).equals(e.getString("sha256")),"original document bytes changed");}
   check(untouched.list().length()==0,"reading installed instructions");
  });
  test("all 209 eligible original packages coexist in one real store without automatic activation",()->{
   LocalSkillStore installed=store();JSONArray original=library.list();int count=0;for(int i=0;i<original.length();i++){JSONObject e=original.getJSONObject(i);if(e.getBoolean("installable")){JSONObject result=library.install(e.getString("id"),installed);check(!result.getBoolean("activated")&&!result.getBoolean("executable"),"installation activated or executed instructions");check(sha(installed.read(e.getString("name")).getString("content").getBytes(StandardCharsets.UTF_8)).equals(e.getString("sha256")),"installed original changed");count++;}else reject(()->library.install(e.getString("id"),installed));}
   check(count==209&&installed.list().length()==209,"original library truncated at old 64-package limit");
  });
  test("explicit library install refuses overwrite and preserves owner document and resources",()->{
   JSONObject e=library.list().getJSONObject(0);LocalSkillStore owned=store();owned.save(e.getString("name"),"Owner document","Owner-authored instructions");owned.writeFile(e.getString("name"),"references/owner.txt","Owner bytes");String before=owned.read(e.getString("name")).getString("content");reject(()->library.install(e.getString("id"),owned));check(owned.read(e.getString("name")).getString("content").equals(before)&&owned.read(e.getString("name"),"references/owner.txt").getString("content").equals("Owner bytes"),"install overwrote owner package");
  });
  test("manifest resource lookup rejects traversal and oversized originals and preserves binary byte identity",()->{
   JSONArray original=library.list();JSONObject first=original.getJSONObject(0);reject(()->library.read(first.getString("id"),"../SKILL.md"));reject(()->library.read("../unknown"));reject(()->library.read(first.getString("id"),"not-in-original-manifest.txt"));boolean binary=false,oversize=false;
   outer:for(int i=0;i<original.length();i++){JSONObject e=library.read(original.getJSONObject(i).getString("id"));JSONArray files=e.getJSONArray("files");for(int j=0;j<files.length();j++){JSONObject f=files.getJSONObject(j);String path=f.getString("path");if(f.getLong("bytes")>1048576){reject(()->library.read(e.getString("id"),path));oversize=true;}else if(!binary&&!path.equals("SKILL.md")){JSONObject r=library.read(e.getString("id"),path);if(r.optString("encoding").equals("base64")){check(sha(Base64.getDecoder().decode(r.getString("content_base64"))).equals(f.getString("sha256")),"binary original corrupted");binary=true;}}}if(binary&&oversize)break outer;}
   check(binary&&oversize,"real binary/oversize resources were not exercised");
  });
  test("tampered packaged ZIP is rejected before read or install changes an owner store",()->{
   JSONObject e=library.list().getJSONObject(0);Path root=Files.createTempDirectory("hermes-library-tamper-");Path catalog=root.resolve("hermes-skills/catalog.json");Files.createDirectories(catalog.getParent());Files.copy(assets.toPath().resolve("hermes-skills/catalog.json"),catalog);Path zip=root.resolve(e.getString("package_asset"));Files.createDirectories(zip.getParent());byte[] bytes=Files.readAllBytes(assets.toPath().resolve(e.getString("package_asset")));bytes[bytes.length/2]^=1;Files.write(zip,bytes);BuiltinSkillLibrary altered=new BuiltinSkillLibrary(new AssetManager(root.toFile()));LocalSkillStore owned=store();owned.save("owner","Owner document","Retain me");reject(()->altered.read(e.getString("id")));reject(()->altered.install(e.getString("id"),owned));check(owned.list().length()==1&&owned.read("owner").getString("body").equals("Retain me"),"tampered ZIP changed store");
  });
  System.out.println("BuiltinSkillLibrary: "+passed+" real packaged-asset/host-filesystem checks passed; no Android activation claim.");
 }
}
