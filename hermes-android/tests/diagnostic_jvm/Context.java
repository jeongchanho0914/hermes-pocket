package android.content;
import java.io.File;
/** Host file-directory adapter, not Android Context behavior. */
public final class Context {public static final String ACTIVITY_SERVICE="activity";public final android.app.ActivityManager activityManager=new android.app.ActivityManager();public Object getSystemService(String name){return activityManager;}public String getPackageName(){return "dev.chanho.hermes";}private final File root;public Context(File root){this.root=root;}public File getFilesDir(){return root;}public Context getApplicationContext(){return this;}}
