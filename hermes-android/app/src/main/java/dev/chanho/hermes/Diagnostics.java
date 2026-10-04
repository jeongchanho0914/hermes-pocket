package dev.chanho.hermes;

import android.content.Context;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.os.Build;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Private, bounded failure metadata. Never records messages, request bodies or user content. */
public final class Diagnostics {
    public static final String RELATIVE_PATH = "diagnostics/events.jsonl";
    public static final int MAX_RECORDS = 256;
    public static final int MAX_BYTES = 256 * 1024;
    public enum Phase { STARTUP, RUNTIME, MODEL_CONNECTION, MODEL_STREAM, TOOL, BRIDGE, UI,
        OVERLAY, ACCESSIBILITY, SCREEN_CAPTURE, TERMINAL, SKILLS, STORAGE, OTHER }
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final AtomicBoolean CRASH_RECORDING = new AtomicBoolean(false);
    private static boolean installed;
    private Diagnostics() {}

    public static void initialize(final Context context) {
        LOCK.lock(); try {
            if (installed) return;
            installed = true;
            final Context app = context.getApplicationContext() == null ? context : context.getApplicationContext();
            final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                if (CRASH_RECORDING.compareAndSet(false, true)) {
                    try {
                        if (LOCK.tryLock(150, TimeUnit.MILLISECONDS)) {
                            try { save(app, Phase.RUNTIME, error, "uncaught"); } finally { LOCK.unlock(); }
                        }
                    }
                    catch (Throwable ignored) { /* The original crash always remains authoritative. */ }
                    finally { CRASH_RECORDING.set(false); }
                }
                if (previous != null) previous.uncaughtException(thread, error);
                else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
            });
        } finally { LOCK.unlock(); }
    }

    public static void record(Context context, String phase, Throwable error) {
        Phase parsed = Phase.OTHER;
        if (phase != null) try { parsed = Phase.valueOf(phase.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) {}
        record(context, parsed, error);
    }
    public static void record(Context context, Phase phase, Throwable error) {
        if (context == null || error == null) return;
        try { save(context, phase == null ? Phase.OTHER : phase, error, "caught"); }
        catch (Throwable ignored) { /* Diagnostics must not introduce a second failure. */ }
    }

    /** Browser errors are metadata only; never accept JavaScript messages or stacks. */
    public static void recordBrowserProblem(Context context, String code, String source, int line, int column) {
        if(context==null || (!"webview_error".equals(code) && !"unhandled_rejection".equals(code))) return;
        String file=("app.js".equals(source) || "index.html".equals(source))?source:"unknown";
        int safeLine=Math.max(0,Math.min(10000000,line)), safeColumn=Math.max(0,Math.min(10000000,column));
        try {
            String identity=BuildConfig.VERSION_CODE+":browser_error:"+code+":"+file+":"+safeLine+":"+safeColumn;
            String id=hex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
            long now=System.currentTimeMillis();
            LOCK.lock(); try {
                JSONArray current=read(context), next=new JSONArray(); JSONObject prior=null;
                for(int i=0;i<current.length();i++) {
                    JSONObject row=current.optJSONObject(i); if(row==null)continue;
                    if(id.equals(row.optString("id"))) prior=row; else next.put(row);
                }
                next.put(new JSONObject().put("schema",1).put("id",id).put("versionCode",BuildConfig.VERSION_CODE)
                    .put("versionName",BuildConfig.VERSION_NAME).put("phase","ui").put("type","browser_error")
                    .put("firstSeen",prior==null?now:prior.optLong("firstSeen",now)).put("lastSeen",now)
                    .put("count",prior==null?1:Math.min(1000000000L,prior.optLong("count",1)+1))
                    .put("code",code).put("source",file).put("line",safeLine).put("column",safeColumn));
                write(context,next);
            } finally { LOCK.unlock(); }
        } catch(Throwable ignored) {}
    }

    /** OS crash/ANR history for this app only. Captured off the main thread on startup. */
    public static void collectPreviousProcessExits(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 30) return;
        try {
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager==null) return;
            List<ApplicationExitInfo> exits=manager.getHistoricalProcessExitReasons(context.getPackageName(),0,5);
            for(ApplicationExitInfo exit:exits) {
                int reason=exit.getReason();
                if(reason!=ApplicationExitInfo.REASON_CRASH && reason!=ApplicationExitInfo.REASON_CRASH_NATIVE
                    && reason!=ApplicationExitInfo.REASON_ANR) continue;
                long timestamp=exit.getTimestamp(); int pid=exit.getPid();
                String identity="process_exit:"+reason+":"+timestamp+":"+pid;
                String id=hex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
                LOCK.lock(); try {
                    JSONArray current=read(context); boolean existing=false;
                    for(int i=0;i<current.length();i++) if(id.equals(current.optJSONObject(i).optString("id"))) { existing=true; break; }
                    if(existing) continue;
                    long now=System.currentTimeMillis();
                    current.put(new JSONObject().put("schema",1).put("id",id)
                        .put("versionCode",BuildConfig.VERSION_CODE).put("versionName",BuildConfig.VERSION_NAME)
                        .put("versionMeaning","observed_at_startup").put("phase","startup").put("type","process_exit")
                        .put("firstSeen",now).put("lastSeen",now).put("count",1)
                        .put("exitReason",reason).put("exitTimestamp",timestamp).put("pid",pid));
                    write(context,current);
                } finally { LOCK.unlock(); }
            }
        } catch(Throwable ignored) { /* Missing OS history is not an application failure. */ }
    }

    /** Explicit developer acknowledgement, only after a reviewed fix has been verified. */
    public static void acknowledge(Context context, JSONArray ids) throws IOException {
        if (ids == null) return;
        Set<String> accepted = new HashSet<>();
        for (int i=0;i<Math.min(ids.length(), MAX_RECORDS);i++) {
            String id=ids.optString(i, ""); if (id.matches("[0-9a-f]{64}")) accepted.add(id);
        }
        LOCK.lock(); try {
            JSONArray current;
            try { current=read(context); } catch (Exception failure) { throw new IOException("diagnostics read failed", failure); }
            JSONArray remaining=new JSONArray();
            for(int i=0;i<current.length();i++) {
                JSONObject entry=current.optJSONObject(i);
                if(entry!=null && !accepted.contains(entry.optString("id"))) remaining.put(entry);
            }
            write(context,remaining);
        } finally { LOCK.unlock(); }
    }
    public static JSONArray snapshot(Context context) {
        LOCK.lock(); try { try { return read(context); } catch (Throwable ignored) { return new JSONArray(); } } finally { LOCK.unlock(); }
    }

    /** Collector reads must fail visibly on unavailable/corrupt storage, never report clean. */
    public static JSONArray snapshotForExport(Context context) throws IOException {
        LOCK.lock(); try {
            try { return read(context,true); }
            catch(Exception failure) { throw new IOException("diagnostics report unavailable",failure); }
        } finally { LOCK.unlock(); }
    }

    private static void save(Context context, Phase phase, Throwable error, String type) throws Exception {
        JSONObject detail=describe(error);
        String signature=BuildConfig.VERSION_CODE+":"+phase.name()+":"+type+":"+detail.toString();
        String id=hex(MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8)));
        long now=System.currentTimeMillis();
        LOCK.lock(); try {
            JSONArray prior=read(context), next=new JSONArray(); JSONObject same=null;
            for(int i=0;i<prior.length();i++) {
                JSONObject old=prior.optJSONObject(i);
                if(old==null) continue;
                if(id.equals(old.optString("id"))) same=old; else next.put(old);
            }
            JSONObject entry=new JSONObject();
            entry.put("schema",1).put("id",id).put("versionCode",BuildConfig.VERSION_CODE)
                .put("versionName",BuildConfig.VERSION_NAME).put("phase",phase.name().toLowerCase(Locale.ROOT))
                .put("type",type).put("firstSeen",same==null?now:same.optLong("firstSeen",now))
                .put("lastSeen",now).put("count",same==null?1:Math.min(1000000000L,same.optLong("count",1)+1))
                .put("exceptions",detail.getJSONArray("exceptions"));
            next.put(entry); write(context,next);
        } finally { LOCK.unlock(); }
    }
    private static JSONObject describe(Throwable error) throws Exception {
        JSONArray exceptions=new JSONArray(); Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        Throwable cursor=error;
        for(int cause=0;cursor!=null && cause<4 && seen.add(cursor);cause++) {
            JSONObject ex=new JSONObject(); ex.put("class",identifier(cursor.getClass().getName(),160));
            JSONArray frames=new JSONArray(); StackTraceElement[] stack=cursor.getStackTrace();
            for(int i=0;i<Math.min(stack.length,12);i++) {
                StackTraceElement frame=stack[i]; JSONObject f=new JSONObject();
                f.put("class",identifier(frame.getClassName(),160)).put("method",identifier(frame.getMethodName(),100))
                 .put("source",identifier(frame.getFileName(),100)).put("line",frame.getLineNumber()); frames.put(f);
            }
            ex.put("frames",frames); exceptions.put(ex); cursor=cursor.getCause();
        }
        return new JSONObject().put("exceptions",exceptions);
    }
    private static String identifier(String value,int max) {
        if(value==null) return "";
        if(value.length()>max || !value.matches("[A-Za-z0-9_.$<>-]*")) return "unknown";
        // Reject credential-shaped fabricated frames when importing/reloading metadata.
        if(value.matches("(?i).*(?:sk[-_]|ghp_|gho_|github_pat_|AIza|AKIA|ASIA|xox[baprs]-|Bearer).*")) return "unknown";
        if(value.matches(".*[A-Za-z0-9_-]{48,}.*")) return "unknown";
        return value;
    }
    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder(); for(byte b:bytes) out.append(String.format(Locale.ROOT,"%02x",b&255)); return out.toString();
    }
    private static AtomicFile file(Context context) throws IOException {
        File dir=new File(context.getFilesDir(),"diagnostics");
        if(!dir.isDirectory() && !dir.mkdirs()) throw new IOException("diagnostics directory unavailable");
        return new AtomicFile(new File(dir,"events.jsonl"));
    }
    private static JSONArray read(Context context) throws Exception { return read(context,false); }
    private static JSONArray read(Context context,boolean strict) throws Exception {
        AtomicFile f=file(context); JSONArray result=new JSONArray();
        if(!f.getBaseFile().exists() && !new File(f.getBaseFile().getPath()+".bak").exists()) return result;
        try(InputStream in=f.openRead()) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] chunk=new byte[4096]; int n;
            while((n=in.read(chunk))!=-1) { if(bytes.size()+n>MAX_BYTES) { if(strict)throw new IOException("diagnostics exceeds byte limit"); return result; } bytes.write(chunk,0,n); }
            String[] lines=new String(bytes.toByteArray(),StandardCharsets.UTF_8).split("\\n");
            for(String line:lines) {
                if(result.length()>=MAX_RECORDS) { if(strict)throw new IOException("diagnostics exceeds record limit"); break; }
                try { JSONObject clean=sanitize(new JSONObject(line)); if(clean!=null) result.put(clean); else if(strict)throw new IOException("diagnostics entry invalid"); }
                catch(Exception invalid) { if(strict)throw new IOException("diagnostics entry invalid",invalid); }
            }
        }
        return result;
    }
    private static JSONObject sanitize(JSONObject in) throws Exception {
        Object schema=in.opt("schema");
        if(!(schema instanceof Integer) || ((Integer)schema).intValue()!=1) return null;
        String id=in.optString("id"); String phase=in.optString("phase"); String type=in.optString("type");
        if(!id.matches("[0-9a-f]{64}") || (!type.equals("caught")&&!type.equals("uncaught")&&!type.equals("process_exit")&&!type.equals("browser_error"))) return null;
        try { Phase.valueOf(phase.toUpperCase(Locale.ROOT)); } catch(Exception bad) { return null; }
        JSONObject out=new JSONObject().put("schema",1).put("id",id).put("versionCode",in.optInt("versionCode"))
            .put("versionName",identifier(in.optString("versionName"),32)).put("phase",phase).put("type",type)
            .put("firstSeen",in.optLong("firstSeen")).put("lastSeen",in.optLong("lastSeen"))
            .put("count",Math.max(1,Math.min(1000000000L,in.optLong("count",1))));
        if(type.equals("browser_error")) {
            String code=in.optString("code"), sourceFile=in.optString("source");
            if(!"webview_error".equals(code) && !"unhandled_rejection".equals(code)) return null;
            if(!"app.js".equals(sourceFile) && !"index.html".equals(sourceFile)) sourceFile="unknown";
            return out.put("phase","ui").put("code",code).put("source",sourceFile)
                .put("line",Math.max(0,Math.min(10000000,in.optInt("line"))))
                .put("column",Math.max(0,Math.min(10000000,in.optInt("column"))));
        }
        if(type.equals("process_exit")) {
            int reason=in.optInt("exitReason",-1);
            if(reason!=4 && reason!=5 && reason!=6) return null;
            return out.put("phase","startup").put("versionMeaning","observed_at_startup")
                .put("exitReason",reason).put("exitTimestamp",in.optLong("exitTimestamp")).put("pid",in.optInt("pid"));
        }
        JSONArray exceptions=new JSONArray(), source=in.optJSONArray("exceptions");
        for(int i=0;source!=null && i<Math.min(source.length(),4);i++) {
            JSONObject ex=source.optJSONObject(i); if(ex==null) continue;
            JSONObject safe=new JSONObject().put("class",identifier(ex.optString("class"),160));
            JSONArray frames=new JSONArray(), raw=ex.optJSONArray("frames");
            for(int j=0;raw!=null && j<Math.min(raw.length(),12);j++) {
                JSONObject frame=raw.optJSONObject(j); if(frame==null) continue;
                frames.put(new JSONObject().put("class",identifier(frame.optString("class"),160))
                    .put("method",identifier(frame.optString("method"),100)).put("source",identifier(frame.optString("source"),100))
                    .put("line",frame.optInt("line",-1)));
            }
            exceptions.put(safe.put("frames",frames));
        }
        return out.put("exceptions",exceptions);
    }
    private static void write(Context context,JSONArray records) throws IOException {
        List<byte[]> lines=new ArrayList<>(); int total=0;
        for(int i=records.length()-1;i>=0 && lines.size()<MAX_RECORDS;i--) {
            byte[] bytes=(records.optJSONObject(i).toString()+"\n").getBytes(StandardCharsets.UTF_8);
            if(total+bytes.length>MAX_BYTES) break;
            lines.add(bytes); total+=bytes.length;
        }
        AtomicFile f=file(context); FileOutputStream out=null;
        try { out=f.startWrite(); for(int i=lines.size()-1;i>=0;i--) out.write(lines.get(i)); f.finishWrite(out); }
        catch(IOException failure) { if(out!=null) f.failWrite(out); throw failure; }
    }
}
