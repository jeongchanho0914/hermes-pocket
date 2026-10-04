package dev.chanho.hermes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Real pipe-based shell sessions. Android uses its existing shell, not a bundled Linux runtime. */
public final class TerminalSessions implements AutoCloseable {
    private static final int RETAIN=65536, MAX_JOBS=32;
    private final File workspace;
    private final String shell, namespace;
    private volatile File sessionCwd;
    private volatile Map<String,String> sessionEnvironment;
    private final Map<String,Job> jobs=new LinkedHashMap<>();
    private final Set<String> cancelledRequests=new LinkedHashSet<>();
    private final Map<String,Job> activeRequests=new HashMap<>();
    private final ExecutorService workers=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"hermes-terminal");t.setDaemon(true);return t;});
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"hermes-terminal-deadline");t.setDaemon(true);return t;});
    private boolean closed;
    private long generation;
    private volatile Runnable jobsListener;
    private volatile java.util.function.BooleanSupplier executionGuard=()->true;
    public void setExecutionGuard(java.util.function.BooleanSupplier guard){executionGuard=guard;}
    public void setJobsListener(Runnable listener){jobsListener=listener;}
    private void changed(){Runnable listener=jobsListener;if(listener!=null)try{listener.run();}catch(RuntimeException ignored){}}
    public synchronized boolean hasRunningJobs(){for(Job job:jobs.values())if(job.done.getCount()!=0)return true;return false;}
    private static final class Job {
        final String id,requestId;
        final Process process;
        final StringBuilder output=new StringBuilder();
        final CountDownLatch ready=new CountDownLatch(1),done=new CountDownLatch(1),drained=new CountDownLatch(1);
        volatile long group=-1;
        volatile Integer exit;
        volatile String status="running";
        volatile boolean stdinClosed;
        long removed,pollPosition;
        ScheduledFuture<?> deadline;
        Job(String id,String requestId,Process process){this.id=id;this.requestId=requestId;this.process=process;}
    }
    public TerminalSessions(File workspace,String shell,String namespace)throws IOException {
        this.workspace=workspace.getCanonicalFile();this.sessionCwd=this.workspace;this.shell=shell;
        this.namespace=namespace.replaceAll("[^a-zA-Z0-9_-]","")+"_"+UUID.randomUUID().toString().substring(0,8);
        if(!this.workspace.isDirectory()&&!this.workspace.mkdirs())throw new IOException("Cannot create terminal workspace");
        if(!new File(shell).canExecute())throw new IOException("Platform shell is unavailable");
        Map<String,String> initial=new HashMap<>();initial.put("PATH",shell.startsWith("/system/")?"/system/bin:/system/xbin":"/usr/bin:/bin");initial.put("HOME",this.workspace.getPath());initial.put("TMPDIR",this.workspace.getPath());initial.put("LANG","C.UTF-8");sessionEnvironment=Collections.unmodifiableMap(initial);
    }
    public File workspace(){return workspace;}
    public File cwd(){return sessionCwd;}
    private String[] launcher()throws IOException {
        for(String path:new String[]{"/system/bin/setsid","/usr/bin/setsid","/bin/setsid"})if(new File(path).canExecute())return new String[]{path};
        File toybox=new File("/system/bin/toybox");
        if(toybox.canExecute()){
            Process probe=new ProcessBuilder(toybox.getPath(),"setsid",shell,"-c","exit 0").start();
            try{if(probe.waitFor(2,TimeUnit.SECONDS)&&probe.exitValue()==0)return new String[]{toybox.getPath(),"setsid"};}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Cancelled",e);}finally{probe.destroy();}
        }
        throw new IOException("This device lacks setsid; safely owned terminal process groups are unavailable");
    }
    public Map<String,Object> terminal(Map<String,Object> args)throws Exception {
        final long startGeneration; synchronized(this){startGeneration=generation;}
        String command=text(args,"command",true,12000);
        if(bool(args,"pty",false))throw new IllegalArgumentException("PTY is unsupported: Android terminal sessions use stdin/stdout pipes");
        int timeout=number(args,"timeout",30,1,120),cap=number(args,"max_output_chars",12000,1,32768);
        boolean background=bool(args,"background",false);String request=text(args,"requestId",false,100);
        String path=text(args,"workdir",false,4096);File cwd=path.isEmpty()?sessionCwd:new File(path);
        if(!cwd.isAbsolute()||!cwd.isDirectory()||!cwd.canRead())throw new IllegalArgumentException("workdir must be an existing readable absolute directory");
        cwd=cwd.getCanonicalFile();String[] prefix=launcher();List<String> launch=new ArrayList<>(Arrays.asList(prefix));
        final File cwdSnapshot=background?null:File.createTempFile(".hermes-cwd-",".txt",workspace);
        final File envSnapshot=background?null:File.createTempFile(".hermes-env-",".bin",workspace);
        if(cwdSnapshot!=null){privateFile(cwdSnapshot);privateFile(envSnapshot);}
        final String envBinary=shell.startsWith("/system/")?"/system/bin/env":"/usr/bin/env";
        launch.add(shell);launch.add("-c");launch.add("printf '%s\n' \"$$\"; eval \"$1\"; hermes_code=$?; if [ -n \"$2\" ]; then pwd -P > \"$2\"; \"$4\" -0 > \"$3\" 2>/dev/null; fi; exit \"$hermes_code\"");launch.add("hermes-terminal");launch.add(command);launch.add(cwdSnapshot==null?"":cwdSnapshot.getPath());launch.add(envSnapshot==null?"":envSnapshot.getPath());launch.add(envBinary);
        final Job job;
        try{synchronized(this){
            if(closed||startGeneration!=generation||!executionGuard.getAsBoolean()||Thread.currentThread().isInterrupted()||cancelledRequests.contains(request))throw new InterruptedException("Terminal request was cancelled");
            if(jobs.size()>=MAX_JOBS){Iterator<Job> it=jobs.values().iterator();while(it.hasNext()&&jobs.size()>=MAX_JOBS){if(it.next().done.getCount()==0)it.remove();}}
            if(jobs.size()>=MAX_JOBS)throw new IllegalStateException("Too many terminal sessions");
            ProcessBuilder builder=new ProcessBuilder(launch).directory(cwd).redirectErrorStream(true);
            Map<String,String> env=builder.environment();env.clear();env.putAll(sessionEnvironment);
            job=new Job("proc_"+namespace+"_"+UUID.randomUUID().toString().replace("-",""),request,builder.start());jobs.put(job.id,job);
            workers.execute(()->read(job));workers.execute(()->reap(job));
            job.deadline=deadlines.schedule(()->terminate(job,"timeout"),timeout,TimeUnit.SECONDS);
        }}catch(Exception e){if(cwdSnapshot!=null)cwdSnapshot.delete();if(envSnapshot!=null)envSnapshot.delete();throw e;}
        changed();
        try{
            if(!job.ready.await(3,TimeUnit.SECONDS)||job.group<=1){terminate(job,"failed");throw new IOException("Unable to establish owned terminal process group");}
            if(!background)await(job,timeout*1000L+1000);
            boolean persisted=false;
            if(cwdSnapshot!=null&&job.done.getCount()==0&&"exited".equals(job.status)&&cwdSnapshot.length()>0&&cwdSnapshot.length()<4096){
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(cwdSnapshot),StandardCharsets.UTF_8))){String observed=reader.readLine();if(observed!=null){File next=new File(observed);if(next.isAbsolute()&&next.isDirectory()&&next.canRead())synchronized(this){if(startGeneration==generation&&path.isEmpty()){sessionCwd=next.getCanonicalFile();persisted=true;}}}}catch(IOException ignored){}
            }
            boolean envPersisted=false;
            if(envSnapshot!=null&&job.done.getCount()==0&&"exited".equals(job.status)){
                Map<String,String> observed=readEnvironment(envSnapshot);
                if(observed!=null)synchronized(this){if(startGeneration==generation){sessionEnvironment=Collections.unmodifiableMap(observed);envPersisted=true;}}
            }
            Map<String,Object> result=snapshot(job,cap,false);result.put("workdir",cwd.getPath());result.put("cwd",sessionCwd.getPath());result.put("background",background);result.put("pty",false);result.put("environment_persistence",envPersisted);result.put("cwd_persistence",persisted);return result;
        }catch(InterruptedException e){terminate(job,"cancelled");throw e;}finally{if(cwdSnapshot!=null)cwdSnapshot.delete();if(envSnapshot!=null)envSnapshot.delete();}
    }
    private static void privateFile(File file)throws IOException{if(!file.setReadable(false,false)||!file.setWritable(false,false)||!file.setReadable(true,true)||!file.setWritable(true,true)){file.delete();throw new IOException("Cannot secure terminal state snapshot");}}
    private static Map<String,String> readEnvironment(File file){
        long length=file.length();if(length<=0||length>65536)return null;
        try(FileInputStream input=new FileInputStream(file)){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[2048];int n;while((n=input.read(buffer))!=-1){if(bytes.size()+n>65536)return null;bytes.write(buffer,0,n);}byte[] raw=bytes.toByteArray();if(raw.length==0||raw[raw.length-1]!=0)return null;Map<String,String> result=new HashMap<>();int begin=0;for(int i=0;i<raw.length;i++)if(raw[i]==0){String entry=new String(raw,begin,i-begin,StandardCharsets.UTF_8);begin=i+1;int equals=entry.indexOf('=');if(equals<=0)return null;String key=entry.substring(0,equals),value=entry.substring(equals+1);if(key.length()>128||!key.matches("[A-Za-z_][A-Za-z0-9_]*")||value.length()>16384||result.size()>=128)return null;result.put(key,value);}return result;}
        catch(IOException e){return null;}
    }
    private void read(Job job){
        try(BufferedReader input=new BufferedReader(new InputStreamReader(job.process.getInputStream(),StandardCharsets.UTF_8))){
            String first=input.readLine();if(first!=null&&first.matches("[0-9]{1,12}"))job.group=Long.parseLong(first);job.ready.countDown();
            char[] buffer=new char[2048];int count;while((count=input.read(buffer))!=-1)synchronized(job){job.output.append(buffer,0,count);int excess=job.output.length()-RETAIN;if(excess>0){if(excess<job.output.length()&&Character.isLowSurrogate(job.output.charAt(excess)))excess++;job.output.delete(0,excess);job.removed+=excess;}}
        }catch(IOException ignored){}finally{job.ready.countDown();job.drained.countDown();}
    }
    private void reap(Job job){
        try{int code=job.process.waitFor();killGroup(job);job.drained.await(2,TimeUnit.SECONDS);synchronized(job){job.exit=code;if("running".equals(job.status))job.status="exited";}}
        catch(InterruptedException e){Thread.currentThread().interrupt();terminate(job,"cancelled");}
        finally{ScheduledFuture<?> deadline=job.deadline;if(deadline!=null)deadline.cancel(false);closeStdin(job);job.done.countDown();changed();}
    }
    private void killGroup(Job job){
        if(job.group<=1)return;
        try{Process killer=new ProcessBuilder(shell,"-c","kill -KILL -"+job.group).redirectErrorStream(true).start();if(!killer.waitFor(1,TimeUnit.SECONDS))killer.destroy();}
        catch(Exception ignored){}
    }
    private void terminate(Job job,String status){synchronized(job){if(job.done.getCount()==0)return;if("running".equals(job.status))job.status=status;}killGroup(job);job.process.destroy();try{if(!job.process.waitFor(100,TimeUnit.MILLISECONDS))job.process.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();}closeStdin(job);}
    private static void closeStdin(Job job){try{job.process.getOutputStream().close();}catch(IOException ignored){}job.stdinClosed=true;}
    private void await(Job job,long millis)throws InterruptedException{long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(millis);while(job.done.getCount()!=0&&System.nanoTime()<end){if(Thread.currentThread().isInterrupted())throw new InterruptedException("Terminal wait cancelled");job.done.await(100,TimeUnit.MILLISECONDS);}}
    public Map<String,Object> process(Map<String,Object> args)throws Exception {
        String action=text(args,"action",true,20);int cap=number(args,"max_output_chars",12000,1,32768);
        if("list".equals(action))return inventory();
        Job job;String id=text(args,"session_id",true,120);synchronized(this){job=jobs.get(id);}if(job==null)throw new IllegalArgumentException("Unknown session_id for this terminal owner");
        String request=text(args,"requestId",false,100);synchronized(this){if(closed||!executionGuard.getAsBoolean()||cancelledRequests.contains(request))throw new InterruptedException("Terminal request cancelled");}
        synchronized(this){if(!request.isEmpty())activeRequests.put(request,job);}
        try{switch(action){
            case "poll":return snapshot(job,cap,true);
            case "log":{
                int offset=number(args,"offset",0,0,Integer.MAX_VALUE),limit=number(args,"limit",200,1,1000);
                synchronized(job){String[] lines=job.output.toString().split("\\n",-1);if(!args.containsKey("offset"))offset=Math.max(0,lines.length-limit);StringBuilder part=new StringBuilder();for(int i=offset;i<lines.length&&i<offset+limit;i++){if(part.length()>0)part.append('\n');part.append(lines[i]);}Map<String,Object> result=metadata(job);String value=part.toString();result.put("output",clip(value,cap));result.put("truncated",job.removed>0||value.length()>cap);result.put("offset",offset);return result;}
            }
            case "wait":await(job,number(args,"timeout",30,1,120)*1000L);return snapshot(job,cap,true);
            case "kill":terminate(job,"killed");await(job,3000);return snapshot(job,cap,true);
            case "close":closeStdin(job);return snapshot(job,cap,false);
            case "write":case "submit":{
                if(job.done.getCount()==0||job.stdinClosed)throw new IllegalStateException("Session stdin is closed");if(!args.containsKey("data"))throw new IllegalArgumentException("data is required");String data=text(args,"data",false,16384)+("submit".equals(action)?"\n":"");
                Future<?> write=workers.submit(()->{try{synchronized(job.process.getOutputStream()){if(!executionGuard.getAsBoolean())throw new IOException("Terminal permission revoked");job.process.getOutputStream().write(data.getBytes(StandardCharsets.UTF_8));job.process.getOutputStream().flush();}}catch(IOException e){throw new UncheckedIOException(e);}});
                try{write.get(3,TimeUnit.SECONDS);}catch(Exception e){write.cancel(true);terminate(job,"failed");throw new IOException("Terminal stdin write failed or blocked",e);}return snapshot(job,cap,false);
            }
            default:throw new IllegalArgumentException("Unsupported process action");
        }}finally{synchronized(this){activeRequests.remove(request);}}
    }
    public Map<String,Object> inventory(){List<Object> list=new ArrayList<>();synchronized(this){for(Job job:jobs.values())list.add(metadata(job));}return object("sessions",list,"status","ok");}
    private Map<String,Object> metadata(Job job){synchronized(job){return object("session_id",job.id,"status",job.status,"exit_code",job.exit,"running",job.done.getCount()!=0,"stdin_closed",job.stdinClosed);}}
    private Map<String,Object> snapshot(Job job,int cap,boolean incremental){synchronized(job){long end=job.removed+job.output.length(),start=incremental?job.pollPosition:job.removed;int from=(int)Math.max(0,Math.min(job.output.length(),start-job.removed));String value=job.output.substring(from);boolean truncated=start<job.removed||value.length()>cap||(!incremental&&job.removed>0);Map<String,Object> result=metadata(job);result.put("output",clip(value,cap));result.put("truncated",truncated);result.put("output_chars_dropped",job.removed);if(incremental)job.pollPosition=end;return result;}}
    private static String clip(String value,int cap){if(value.length()<=cap)return value;int start=value.length()-cap;if(Character.isLowSurrogate(value.charAt(start)))start++;return value.substring(start);}
    public void cancelRequest(String requestId){List<Job> owned=new ArrayList<>();synchronized(this){cancelledRequests.add(requestId);while(cancelledRequests.size()>4096){Iterator<String> it=cancelledRequests.iterator();it.next();it.remove();}Job active=activeRequests.get(requestId);if(active!=null)owned.add(active);for(Job job:jobs.values())if(job.requestId.equals(requestId))owned.add(job);}for(Job job:owned)terminate(job,"cancelled");}
    public void cancelAll(){List<Job> owned;synchronized(this){generation++;owned=new ArrayList<>(jobs.values());}for(Job job:owned){synchronized(job){if(job.done.getCount()!=0&&"running".equals(job.status))job.status="cancelled";}try{workers.execute(()->terminate(job,"cancelled"));}catch(RejectedExecutionException ignored){terminate(job,"cancelled");}}changed();}
    @Override public void close(){List<Job> owned;synchronized(this){closed=true;generation++;owned=new ArrayList<>(jobs.values());}for(Job job:owned)terminate(job,"cancelled");deadlines.shutdownNow();workers.shutdownNow();}
    private static boolean bool(Map<String,Object> args,String key,boolean fallback){Object v=args.get(key);if(v==null)return fallback;if(!(v instanceof Boolean))throw new IllegalArgumentException(key+" must be boolean");return(Boolean)v;}
    private static String text(Map<String,Object> args,String key,boolean required,int max){Object value=args.get(key);if(value==null&&!required)return "";if(!(value instanceof String)||((String)value).length()>max||((String)value).indexOf('\0')>=0||(required&&((String)value).isEmpty()))throw new IllegalArgumentException("Invalid "+key);return(String)value;}
    private static int number(Map<String,Object> args,String key,int fallback,int min,int max){Object v=args.get(key);if(v==null)return fallback;if(!(v instanceof Number)||((Number)v).doubleValue()!=((Number)v).longValue()||((Number)v).longValue()<min||((Number)v).longValue()>max)throw new IllegalArgumentException("Invalid "+key);return((Number)v).intValue();}
    private static Map<String,Object> object(Object... pairs){Map<String,Object> map=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)map.put((String)pairs[i],pairs[i+1]);return map;}
}
