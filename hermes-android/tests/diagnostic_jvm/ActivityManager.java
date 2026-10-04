package android.app;
import java.util.*;
public final class ActivityManager {public String requestedPackage;public int requestedPid,requestedLimit;public List<ApplicationExitInfo> exits=new ArrayList<>();public List<ApplicationExitInfo> getHistoricalProcessExitReasons(String pkg,int pid,int limit){requestedPackage=pkg;requestedPid=pid;requestedLimit=limit;return exits;}}
