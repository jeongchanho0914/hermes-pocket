package dev.chanho.hermes;

import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Bounded text/code workspace. Never addresses settings, signing keys, other apps or the shared-storage grant. */
final class WorkspaceFiles {
    static final int FILE_BYTES=1048576,MAX_FILES=2048;
    static final long TOTAL_BYTES=64L*1048576;
    private final Path root;
    WorkspaceFiles(File directory)throws Exception{
        if(Files.isSymbolicLink(directory.toPath()))throw new SecurityException("작업 폴더 링크는 허용하지 않습니다.");
        if(!directory.exists()&&!directory.mkdirs())throw new IOException("작업 폴더를 만들지 못했습니다.");root=directory.getCanonicalFile().toPath();
    }
    String root(){return root.toString();}
    private Path path(String relative,boolean directory)throws Exception{
        if(relative==null||relative.length()>512||relative.indexOf('\u0000')>=0||relative.indexOf('\\')>=0||relative.startsWith("/")||relative.contains(":"))throw new IllegalArgumentException("작업 폴더 안의 상대 경로만 사용하세요.");
        if(relative.isEmpty()){if(directory)return root;throw new IllegalArgumentException("파일 경로가 필요합니다.");}
        String[] pieces=relative.split("/",-1);if(pieces.length>16)throw new IllegalArgumentException("파일 경로가 너무 깊습니다.");
        Path current=root;
        for(String piece:pieces){
            if(piece.isEmpty()||piece.equals(".")||piece.equals("..")||piece.length()>128||piece.startsWith(".hermes-tmp-"))throw new IllegalArgumentException("파일 경로가 올바르지 않습니다.");
            for(int i=0;i<piece.length();i++)if(Character.isISOControl(piece.charAt(i)))throw new IllegalArgumentException("제어 문자가 있는 경로는 허용하지 않습니다.");
            current=current.resolve(piece);if(Files.isSymbolicLink(current))throw new SecurityException("작업 폴더의 심볼릭 링크는 사용할 수 없습니다.");
        }
        if(!current.normalize().startsWith(root))throw new SecurityException("작업 폴더 범위를 벗어났습니다.");return current;
    }
    static String hash(byte[] bytes)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private byte[] bytes(Path file)throws Exception{
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("읽을 수 있는 일반 파일이 아닙니다.");
        if(Files.size(file)>FILE_BYTES)throw new IllegalArgumentException("텍스트 파일은 최대 1 MiB까지 읽습니다.");
        try(InputStream in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>FILE_BYTES)throw new IllegalArgumentException("읽는 동안 파일 크기가 제한을 넘었습니다.");out.write(buffer,0,n);}return out.toByteArray();
        }
    }
    private String decode(byte[] data)throws CharacterCodingException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();}
    synchronized JSONObject read(String relative,int start,int count)throws Exception{
        if(start<1||start>1000000||count<1||count>400)throw new IllegalArgumentException("줄 범위는 1부터 시작하며 한 번에 최대 400줄입니다.");
        byte[] data=bytes(path(relative,false));String text=decode(data);if(text.indexOf('\u0000')>=0)throw new IllegalArgumentException("바이너리 파일은 텍스트로 읽지 않습니다.");
        String[] lines=text.split("\n",-1);int from=Math.min(lines.length,start-1),end=Math.min(lines.length,from+count);StringBuilder content=new StringBuilder();boolean clipped=false;int actualEnd=from;
        for(int i=from;i<end;i++){
            String line=lines[i];if(content.length()+line.length()>48000){if(content.length()==0){content.append(J.clipped(line,48000));actualEnd=i+1;clipped=true;}break;}
            if(content.length()>0)content.append('\n');content.append(line);actualEnd=i+1;
        }
        boolean truncated=clipped||actualEnd<lines.length;
        return J.obj("path",relative,"workspace",root(),"content",content.toString(),"encoding","utf-8","sha256",hash(data),"bytes",data.length,"totalLines",lines.length,"startLine",start,"endLine",actualEnd,"truncated",truncated,"longLineTruncated",clipped,"nextStartLine",!clipped&&actualEnd<lines.length?actualEnd+1:JSONObject.NULL,"untrusted",true);
    }
    synchronized JSONObject previewWrite(String relative,String content,String expected)throws Exception{
        if(content==null||content.length()>250000||content.indexOf('\u0000')>=0)throw new IllegalArgumentException("UTF-8 텍스트 250,000자 이하를 사용하세요.");
        byte[] data=content.getBytes(StandardCharsets.UTF_8);if(data.length>FILE_BYTES)throw new IllegalArgumentException("파일당 최대 1 MiB입니다.");
        Path target=path(relative,false);boolean exists=Files.exists(target,LinkOption.NOFOLLOW_LINKS);String before=exists?hash(bytes(target)):"";
        if(expected==null||!expected.isEmpty()&&!expected.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("expected_sha256은 read_file에서 읽은 SHA-256이어야 합니다.");
        if(exists&&expected.isEmpty())throw new IllegalStateException("기존 파일을 덮어쓰려면 먼저 read_file로 확인한 expected_sha256을 입력하세요.");
        if(!expected.equals(before))throw new IllegalStateException("파일이 변경되었습니다. 최신 내용을 다시 읽으세요.");
        return J.obj("path",relative,"beforeSha256",before,"sha256",hash(data),"bytes",data.length,"existed",exists);
    }
    synchronized JSONObject write(String relative,String content,String expected)throws Exception{
        JSONObject preview=previewWrite(relative,content,expected);Path target=path(relative,false);long[] usage=usage(root);
        long previous=Files.exists(target,LinkOption.NOFOLLOW_LINKS)?Files.size(target):0;
        if(usage[0]+preview.getLong("bytes")-previous>TOTAL_BYTES||!preview.getBoolean("existed")&&usage[1]>=MAX_FILES)throw new IllegalStateException("작업 폴더는 64 MiB·2,048개 파일 한도입니다.");
        Files.createDirectories(target.getParent());path(relative,false);byte[] data=content.getBytes(StandardCharsets.UTF_8);Path temporary=target.getParent().resolve(".hermes-tmp-"+UUID.randomUUID());
        try{
            try(FileOutputStream out=new FileOutputStream(temporary.toFile())){out.write(data);out.getFD().sync();}
            previewWrite(relative,content,expected);
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unavailable){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
        return J.obj("written",true,"path",relative,"workspace",root(),"bytes",data.length,"sha256",hash(data),"execution",false);
    }
    synchronized String patchedContent(String relative,String old,String replacement,boolean all,String expected)throws Exception{
        if(old==null||old.isEmpty()||replacement==null)throw new IllegalArgumentException("old_string은 비어 있을 수 없습니다.");
        String text=decode(bytes(path(relative,false)));previewWrite(relative,text,expected);int first=text.indexOf(old);
        if(first<0)throw new IllegalArgumentException("수정할 원문이 없습니다.");
        if(!all&&text.indexOf(old,first+old.length())>=0)throw new IllegalArgumentException("일치하는 원문이 여러 개입니다. 더 구체적인 원문을 사용하세요.");
        String result=all?text.replace(old,replacement):text.substring(0,first)+replacement+text.substring(first+old.length());previewWrite(relative,result,expected);return result;
    }
    private long[] usage(Path directory)throws Exception{
        long[] totals={0,0};try(java.util.stream.Stream<Path> paths=Files.walk(directory,16)){
            Iterator<Path> iterator=paths.iterator();int visited=0;
            while(iterator.hasNext()){
                Path path=iterator.next();if(++visited>8192)throw new IllegalStateException("작업 폴더 항목이 너무 많습니다.");
                if(Files.isSymbolicLink(path))throw new SecurityException("작업 폴더에 심볼릭 링크가 있습니다.");
                if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)){totals[0]+=Files.size(path);totals[1]++;}
            }
        }return totals;
    }
    synchronized JSONObject search(String relative,String query,boolean content,int limit)throws Exception{
        if(query==null||query.length()>300||limit<1||limit>100)throw new IllegalArgumentException("검색어는 300자, 결과는 최대 100개입니다.");
        Path directory=path(relative,true);if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("검색할 폴더가 없습니다.");
        JSONArray matches=new JSONArray();int visited=0,unreadable=0;boolean truncated=false;
        try(java.util.stream.Stream<Path> paths=Files.walk(directory,16)){
            Iterator<Path> iterator=paths.iterator();
            while(iterator.hasNext()){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException("파일 검색을 중단했습니다.");
                Path file=iterator.next();if(++visited>8192){truncated=true;break;}
                if(Files.isSymbolicLink(file)){unreadable++;continue;}if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                String name=root.relativize(file).toString();
                if(!content){if(name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))matches.put(J.obj("path",name,"bytes",Files.size(file)));}
                else{
                    try{String text=decode(bytes(file));if(text.indexOf('\u0000')>=0){unreadable++;continue;}String[] lines=text.split("\n",-1);
                        for(int i=0;i<lines.length;i++)if(lines[i].contains(query)){matches.put(J.obj("path",name,"line",i+1,"excerpt",J.clipped(lines[i],500)));if(matches.length()>=limit)break;}
                    }catch(IOException|IllegalArgumentException unsupported){unreadable++;}
                }
                if(matches.length()>=limit){truncated=iterator.hasNext()||content;break;}
            }
        }
        return J.obj("workspace",root(),"matches",matches,"truncated",truncated,"skippedFiles",unreadable,"matchMode",content?"literal_content":"filename_substring","untrusted",true);
    }
}
