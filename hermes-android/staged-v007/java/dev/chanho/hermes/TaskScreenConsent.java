package dev.chanho.hermes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** In-memory consent for one foreground app during a single explicit chat task. */
final class TaskScreenConsent {
    interface Approval {boolean approve(String targetPackage) throws Exception;}
    private static final Set<String> SCREEN_OPERATIONS=new HashSet<>(Arrays.asList(
        "read_screen","click_element","type_text","scroll_element","press_back","press_home","tap_screen","swipe_screen"));
    private final Predicate<String> protectedTarget;
    private String runId="",grantedPackage="",pending="";
    private long generation;

    TaskScreenConsent(Predicate<String> protectedTarget){this.protectedTarget=protectedTarget;}
    synchronized String begin(){
        runId=UUID.randomUUID().toString();grantedPackage="";pending="";generation++;return runId;
    }
    synchronized boolean hasRun(){return !runId.isEmpty();}
    synchronized void end(){runId="";grantedPackage="";pending="";generation++;}
    synchronized void observeForeground(String targetPackage){
        if(!grantedPackage.isEmpty()&&!grantedPackage.equals(targetPackage)){
            grantedPackage="";pending="";generation++;
        }
    }
    boolean authorize(String targetPackage,String operation,BooleanSupplier cancelled,
                      Supplier<String> foreground,Approval approval) throws Exception {
        if(cancelled.getAsBoolean()){end();return false;}
        if(!SCREEN_OPERATIONS.contains(operation))return false;
        if(!validTarget(targetPackage)){observeForeground(targetPackage);return false;}
        String before=foreground.get();observeForeground(before);
        if(!targetPackage.equals(before))return false;
        final String request,run;final long epoch;
        synchronized(this){
            if(cancelled.getAsBoolean()){end();return false;}
            if(runId.isEmpty())return false;
            if(targetPackage.equals(grantedPackage))return true;
            if(!pending.isEmpty())return false;
            request=UUID.randomUUID().toString();pending=request;run=runId;epoch=generation;
        }
        try{
            if(!approval.approve(targetPackage))return false;
            if(cancelled.getAsBoolean()){end();return false;}
            String after=foreground.get();
            synchronized(this){
                if(cancelled.getAsBoolean()){end();return false;}
                if(!run.equals(runId)||epoch!=generation||!request.equals(pending)
                    ||!targetPackage.equals(after)||!validTarget(after))return false;
                grantedPackage=targetPackage;return true;
            }
        }finally{synchronized(this){if(request.equals(pending))pending="";}}
    }
    private boolean validTarget(String targetPackage){return targetPackage!=null&&!targetPackage.isEmpty()&&!protectedTarget.test(targetPackage);}
}
