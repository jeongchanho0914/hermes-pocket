package dev.chanho.hermes;

import android.content.res.AssetManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Read-only, audited upstream instructions. Installing never executes or activates a skill. */
final class BuiltinSkillLibrary {
    private static final String CATALOG="hermes-skills/catalog.json";
    private static final int MAX_CATALOG_BYTES=2*1024*1024;
    private static final int MAX_ARCHIVE_BYTES=9*1024*1024;
    private final AssetManager assets;
    private JSONObject catalog;

    BuiltinSkillLibrary(AssetManager assets){this.assets=assets;}

    private synchronized JSONObject catalog() throws Exception {
        if(catalog==null){
            try(InputStream input=assets.open(CATALOG)){
                catalog=new JSONObject(SkillPackages.decode(SkillPackages.boundedRead(input,MAX_CATALOG_BYTES)));
            }
            if(catalog.getInt("schema_version")!=1)throw new IOException("지원하지 않는 내장 스킬 목록 형식입니다.");
        }
        return catalog;
    }

    /** Metadata copies keep callers from mutating the cached audited registry. */
    JSONArray list() throws Exception {
        JSONArray source=catalog().getJSONArray("skills"),out=new JSONArray();
        for(int i=0;i<source.length();i++){
            JSONObject entry=new JSONObject(source.getJSONObject(i).toString());
            entry.remove("files");out.put(entry);
        }
        return out;
    }

    JSONObject summary() throws Exception {
        JSONObject source=catalog();
        return J.obj("package_count",source.getInt("package_count"),"installable_count",source.getInt("installable_count"),
                "payload_bytes",source.getLong("payload_bytes"),"archive_bytes",source.getLong("archive_bytes"),
                "source",new JSONObject(source.getJSONObject("source").toString()));
    }

    private JSONObject find(String id) throws Exception {
        JSONArray entries=catalog().getJSONArray("skills");
        for(int i=0;i<entries.length();i++)if(entries.getJSONObject(i).getString("id").equals(id))return entries.getJSONObject(i);
        throw new IOException("내장 스킬을 찾을 수 없습니다: "+id);
    }

    private byte[] archive(JSONObject entry) throws Exception {
        String path=entry.getString("package_asset");
        if(!path.startsWith("hermes-skills/packages/")||path.contains("..")||path.contains("\\"))throw new IOException("잘못된 내장 스킬 경로입니다.");
        byte[] bytes;try(InputStream input=assets.open(path)){bytes=SkillPackages.boundedRead(input,MAX_ARCHIVE_BYTES);}
        if(bytes.length!=entry.getLong("archive_bytes")||!sha(bytes).equals(entry.getString("package_sha256")))throw new IOException("내장 스킬 패키지 검증 실패");
        return bytes;
    }

    /** Returns the original SKILL.md, including YAML, without importing or activating. */
    JSONObject read(String id) throws Exception {return read(id,"SKILL.md");}

    /** Manifest-backed resource reads do not depend on native installation eligibility. */
    JSONObject read(String id,String filePath) throws Exception {
        JSONObject entry=find(id),result=new JSONObject(entry.toString());
        String path=filePath==null||filePath.isEmpty()?"SKILL.md":filePath;
        JSONObject resource=null;JSONArray files=entry.getJSONArray("files");
        for(int i=0;i<files.length();i++)if(files.getJSONObject(i).getString("path").equals(path)){resource=files.getJSONObject(i);break;}
        if(resource==null)throw new IOException("내장 스킬의 지원 파일을 찾을 수 없습니다: "+path);
        if(resource.getLong("bytes")>1024*1024)throw new IOException("원본 지원 파일이 1 MiB 읽기 한도를 초과합니다: "+path);
        try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(archive(entry)))){
            ZipEntry file;int count=0;
            while((file=input.getNextEntry())!=null){
                if(++count>4096)throw new IOException("내장 패키지 항목 수 한도 초과");
                if(file.getName().equals(path)&&!file.isDirectory()){
                    byte[] bytes=SkillPackages.boundedRead(input,1024*1024);
                    if(bytes.length!=resource.getLong("bytes")||!sha(bytes).equals(resource.getString("sha256")))throw new IOException("내장 스킬 파일 검증 실패");
                    result.put("file_path",path);result.put("bytes",bytes.length);
                    try{result.put("content",SkillPackages.decode(bytes));result.put("encoding","utf-8");}
                    catch(IOException binary){result.put("content_base64",java.util.Base64.getEncoder().encodeToString(bytes));result.put("encoding","base64");}
                    if(path.equals("SKILL.md"))result.put("format","SKILL.md");
                    result.put("executable",false);return result;
                }
                input.closeEntry();
            }
        }
        throw new IOException("내장 패키지에 지원 파일이 없습니다: "+path);
    }

    /** Explicit user installation only. Existing user packages are never overwritten. */
    JSONObject install(String id,LocalSkillStore store) throws Exception {
        JSONObject entry=find(id);
        if(!entry.getBoolean("installable"))throw new IOException("원본 패키지를 그대로 가져올 수 없습니다: "+entry.getJSONArray("install_blockers").toString());
        try(InputStream input=new ByteArrayInputStream(archive(entry))){
            JSONObject result=store.importPackage(entry.getString("name"),input,false);
            result.put("builtin_id",id);result.put("source_sha256",entry.getString("sha256"));
            result.put("activated",false);result.put("executable",false);return result;
        }
    }

    private static String sha(byte[] data) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(data);StringBuilder out=new StringBuilder();
        for(byte value:digest)out.append(String.format(Locale.ROOT,"%02x",value&255));return out.toString();
    }
}
