package dev.chanho.hermes;

import org.json.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Android-native package IO; no external process or interpreter executes during import. */
final class SkillPackages {
    static final int MAX_FILES=256, MAX_FILE_BYTES=1024*1024, MAX_PACKAGE_BYTES=8*1024*1024;
    private SkillPackages(){}
    static File safeFile(File root,String relative) throws IOException {
        if(relative==null||relative.isEmpty()||relative.startsWith("/")||relative.contains("\\")||relative.indexOf('\0')>=0||relative.contains(":"))throw new IOException("상대 파일 경로가 올바르지 않습니다.");
        for(String part:relative.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("경로 이동은 허용되지 않습니다.");
        File base=root.getCanonicalFile(),file=new File(base,relative);
        if(!file.getCanonicalFile().equals(file.getAbsoluteFile())||!file.getCanonicalPath().startsWith(base.getPath()+File.separator))throw new IOException("스킬 폴더 밖 경로 또는 심볼릭 링크는 허용되지 않습니다.");return file;
    }
    /** Package preservation/read accepts any safe original relative resource. */
    static File resourceFile(File root,String path) throws IOException {return safeFile(root,path);}
    /** Agent mutations retain the upstream skill_manage supporting-file allowlist. */
    static File writableResourceFile(File root,String path) throws IOException {
        File f=safeFile(root,path);String first=path.split("/",2)[0];
        if(!path.equals("SKILL.md")&&(!Arrays.asList("scripts","references","templates","assets").contains(first)||!path.contains("/")))throw new IOException("지원 파일은 scripts/, references/, templates/, assets/ 아래에 저장하세요.");return f;
    }
    static byte[] boundedRead(InputStream stream,int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=stream.read(buffer))!=-1){if(out.size()+n>max)throw new IOException("파일 크기 한도를 초과했습니다.");out.write(buffer,0,n);}return out.toByteArray();
    }
    static String readText(File file,int max) throws IOException {
        if(!file.isFile())throw new FileNotFoundException("파일을 찾을 수 없습니다: "+file.getName());
        try(InputStream in=new FileInputStream(file)){return decode(boundedRead(in,max));}
    }
    static String decode(byte[] bytes) throws IOException {try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException e){throw new IOException("UTF-8 텍스트 파일이 아닙니다.",e);}}
    static JSONObject readResource(File root,String path) throws Exception {
        File f=resourceFile(root,path);byte[] bytes;try(InputStream in=new FileInputStream(f)){bytes=boundedRead(in,MAX_FILE_BYTES);}
        JSONObject result=J.obj("file_path",path,"path",f.getAbsolutePath(),"bytes",bytes.length);
        try{result.put("content",decode(bytes));result.put("encoding","utf-8");}catch(IOException e){result.put("content_base64",Base64.getEncoder().encodeToString(bytes));result.put("encoding","base64");}return result;
    }
    static JSONArray resources(File root) throws Exception {
        List<File> files=new ArrayList<>();collect(root,root,files);files.sort(Comparator.comparing(File::getPath));JSONArray out=new JSONArray();
        for(File f:files){String path=root.toPath().relativize(f.toPath()).toString().replace(File.separatorChar,'/');if(!path.equals("SKILL.md"))out.put(J.obj("file_path",path,"bytes",f.length(),"path",f.getAbsolutePath()));}return out;
    }
    private static void collect(File root,File dir,List<File> out) throws IOException {
        File[] children=dir.listFiles();if(children==null)throw new IOException("스킬 폴더를 읽을 수 없습니다.");
        for(File f:children){String relative=root.toPath().relativize(f.toPath()).toString().replace(File.separatorChar,'/');safeFile(root,relative);if(Files.isSymbolicLink(f.toPath()))throw new IOException("심볼릭 링크는 허용되지 않습니다.");if(f.isDirectory())collect(root,f,out);else if(f.isFile()){resourceFile(root,relative);out.add(f);if(out.size()>MAX_FILES)throw new IOException("패키지 파일 개수 한도 초과");}}
    }
    static void writeBytes(File root,String path,byte[] bytes) throws IOException {
        if(bytes.length>MAX_FILE_BYTES)throw new IOException("지원 파일은 최대 1 MiB입니다.");File target=resourceFile(root,path),parent=target.getParentFile();
        if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("스킬 폴더를 만들지 못했습니다.");
        List<File> all=new ArrayList<>();collect(root,root,all);long total=bytes.length;boolean existing=false;for(File f:all){if(f.equals(target))existing=true;else total+=f.length();}
        if((!existing&&all.size()>=MAX_FILES)||total>MAX_PACKAGE_BYTES)throw new IOException("스킬 패키지 크기/개수 한도 초과");
        File temporary=File.createTempFile(".skill-",".tmp",parent);
        try{try(FileOutputStream out=new FileOutputStream(temporary)){out.write(bytes);out.getFD().sync();}Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{temporary.delete();}
    }
    static void deleteTree(File root) throws IOException {
        if(Files.isSymbolicLink(root.toPath()))throw new IOException("심볼릭 링크는 허용되지 않습니다.");
        File[] children=root.listFiles();if(children!=null)for(File f:children){safeFile(root,f.getName());deleteTree(f);}if(root.exists()&&!root.delete())throw new IOException("스킬 파일을 삭제하지 못했습니다.");
    }
    static JSONObject importZip(LocalSkillStore store,String name,InputStream input,boolean replace) throws Exception {
        File target=store.folder(name);boolean existed=target.exists();if(existed&&!replace)throw new IOException("기존 스킬을 덮어쓰려면 명시적으로 교체하세요.");
        File staging=Files.createTempDirectory(target.getParentFile().toPath(),".skill-import-").toFile();
        try{
            Map<String,byte[]> entries=new LinkedHashMap<>();Set<String> paths=new HashSet<>();int total=0,count=0;
            byte[] archive=boundedRead(input,MAX_PACKAGE_BYTES+1024*1024);validateZipDirectory(archive);
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(++count>MAX_FILES*2)throw new IOException("ZIP 항목 개수 한도 초과");String path=entry.getName();if(entry.isDirectory()){if(!path.endsWith("/"))throw new IOException("잘못된 ZIP 폴더");path=path.substring(0,path.length()-1);}safeFile(staging,path);if(!paths.add(path))throw new IOException("중복 ZIP 파일 경로");if(!entry.isDirectory()){byte[] bytes=boundedRead(zip,MAX_FILE_BYTES);total+=bytes.length;if(total>MAX_PACKAGE_BYTES||entries.size()>=MAX_FILES)throw new IOException("ZIP 패키지 크기/개수 한도 초과");entries.put(path,bytes);}zip.closeEntry();}}
            String prefix="";if(!entries.containsKey("SKILL.md")){for(String p:entries.keySet())if(p.endsWith("/SKILL.md")&&p.indexOf('/')==p.lastIndexOf('/')){if(!prefix.isEmpty())throw new IOException("ZIP에는 하나의 스킬만 포함하세요.");prefix=p.substring(0,p.indexOf('/')+1);}if(prefix.isEmpty())throw new IOException("ZIP에 SKILL.md가 없습니다.");}
            for(Map.Entry<String,byte[]> e:entries.entrySet()){if(!e.getKey().startsWith(prefix))throw new IOException("스킬 폴더 밖 ZIP 파일");String path=e.getKey().substring(prefix.length());resourceFile(staging,path);if(path.equals("SKILL.md"))LocalSkillStore.parseMarkdown(name,decode(e.getValue()));writeBytes(staging,path,e.getValue());}
            install(staging,target,replace);return J.obj("name",name,"imported",true,"files",entries.size(),"bytes",total,"replaced",existed);
        }finally{if(staging.exists())deleteTree(staging);}
    }
    private static int u16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}
    private static long u32(byte[] b,int p){return (u16(b,p)&65535L)|((long)u16(b,p+2)<<16);}
    private static void validateZipDirectory(byte[] bytes) throws IOException {
        int end=-1;for(int p=bytes.length-22;p>=Math.max(0,bytes.length-65557);p--)if(u32(bytes,p)==0x06054b50L&&p+22+u16(bytes,p+20)==bytes.length){end=p;break;}
        if(end<0||u16(bytes,end+4)!=0||u16(bytes,end+6)!=0)throw new IOException("단일 디스크 ZIP 파일이 필요합니다.");
        int count=u16(bytes,end+10);long offset=u32(bytes,end+16),size=u32(bytes,end+12);
        if(count>MAX_FILES*2||offset+size!=end||offset>Integer.MAX_VALUE)throw new IOException("ZIP 디렉터리 형식/개수 한도 초과");
        int p=(int)offset;for(int i=0;i<count;i++){
            if(p+46>end||u32(bytes,p)!=0x02014b50L)throw new IOException("ZIP 디렉터리가 손상되었습니다.");
            int mode=(int)(u32(bytes,p+38)>>>16);if((mode&0170000)==0120000)throw new IOException("ZIP 심볼릭 링크는 허용되지 않습니다.");
            if((u16(bytes,p+8)&1)!=0)throw new IOException("암호화 ZIP은 지원하지 않습니다.");
            p+=46+u16(bytes,p+28)+u16(bytes,p+30)+u16(bytes,p+32);if(p>end)throw new IOException("ZIP 디렉터리가 손상되었습니다.");
        }
        if(p!=end)throw new IOException("ZIP 디렉터리가 손상되었습니다.");
    }
    static void install(File staging,File target,boolean replace) throws IOException {
        if(target.exists()&&!replace)throw new IOException("스킬이 이미 존재합니다.");File backup=null;
        if(target.exists()){backup=Files.createTempDirectory(target.getParentFile().toPath(),".skill-backup-").toFile();if(!backup.delete())throw new IOException("백업 준비 실패");Files.move(target.toPath(),backup.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        try{Files.move(staging.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(IOException e){if(backup!=null)Files.move(backup.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);throw e;}
        if(backup!=null)deleteTree(backup);
    }
    static void exportZip(File root,OutputStream output) throws Exception {
        List<File> files=new ArrayList<>();collect(root,root,files);files.sort(Comparator.comparing(File::getPath));long total=0;
        try(ZipOutputStream zip=new ZipOutputStream(output)){for(File f:files){total+=f.length();if(f.length()>MAX_FILE_BYTES||total>MAX_PACKAGE_BYTES)throw new IOException("패키지 크기 한도 초과");String path=root.toPath().relativize(f.toPath()).toString().replace(File.separatorChar,'/');zip.putNextEntry(new ZipEntry(path));try(InputStream in=new FileInputStream(f)){byte[] bytes=boundedRead(in,MAX_FILE_BYTES);zip.write(bytes);}zip.closeEntry();}}
    }
}
