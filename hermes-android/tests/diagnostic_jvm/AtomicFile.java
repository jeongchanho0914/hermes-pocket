package android.util;
import java.io.*;import java.nio.file.*;
/** Real host-file adapter models writer rollback; this is not proof of Android AtomicFile. */
public final class AtomicFile {
 private final File base,backup;public static boolean failFinish;
 public AtomicFile(File base){this.base=base;backup=new File(base.getPath()+".bak");}
 public File getBaseFile(){return base;}
 public InputStream openRead()throws IOException{if(backup.exists())Files.move(backup.toPath(),base.toPath(),StandardCopyOption.REPLACE_EXISTING);return new FileInputStream(base);}
 public FileOutputStream startWrite()throws IOException{if(base.exists()&&!backup.exists())Files.move(base.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING);return new FileOutputStream(base);}
 public void finishWrite(FileOutputStream out)throws IOException{out.flush();out.getFD().sync();out.close();if(failFinish)throw new IOException("Host adapter injected atomic finish failure");Files.deleteIfExists(backup.toPath());}
 public void failWrite(FileOutputStream out){try{out.close();Files.deleteIfExists(base.toPath());if(backup.exists())Files.move(backup.toPath(),base.toPath(),StandardCopyOption.REPLACE_EXISTING);}catch(IOException error){throw new UncheckedIOException(error);}}
}
