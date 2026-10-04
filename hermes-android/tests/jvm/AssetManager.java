package android.content.res;
import java.io.*;
/** Host fixture reads actual packaged assets; this does not exercise Android AssetManager. */
public final class AssetManager {
 private final File root;
 public AssetManager(File root){this.root=root;}
 public InputStream open(String path)throws IOException{return new FileInputStream(new File(root,path));}
}
