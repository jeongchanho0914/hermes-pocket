package dev.chanho.hermes;
public final class OwnerApprovalPolicyHarness {
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static void invalid(Runnable action){try{action.run();throw new AssertionError("unknown owner preference accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        check(OwnerApprovalPolicy.mode("ask").equals("ask")&&OwnerApprovalPolicy.mode("auto").equals("auto"),"stored approval modes rejected");
        check(OwnerApprovalPolicy.scope("all").equals("all")&&OwnerApprovalPolicy.scope("none").equals("none"),"stored global scope rejected");
        for(String bad:new String[]{null,"","automatic","AUTO","ask ","root"})invalid(()->OwnerApprovalPolicy.mode(bad));
        for(String bad:new String[]{null,"","*","allowlist","ALL"})invalid(()->OwnerApprovalPolicy.scope(bad));
        System.out.println("PASS owner preference validation rejects ambiguous modes and scopes");
        check(OwnerApprovalPolicy.allowsApp("all","dev.hermesfixture.android",false),"global scope still requires per-app list");
        check(!OwnerApprovalPolicy.allowsApp("none","dev.hermesfixture.android",false),"disabled scope still permits control");
        check(!OwnerApprovalPolicy.allowsApp("all","dev.chanho.hermes",true),"protected target permitted");
        check(!OwnerApprovalPolicy.allowsApp("all",null,false)&&!OwnerApprovalPolicy.allowsApp("all","",false),"missing target permitted");
        System.out.println("PASS global control requires owner-enabled scope and unprotected actual target");
        check(OwnerApprovalPolicy.evaluate("auto",true,false,false)==OwnerApprovalPolicy.Decision.AUTO,"stored auto ignored for active request");
        check(OwnerApprovalPolicy.evaluate("ask",true,false,false)==OwnerApprovalPolicy.Decision.ASK,"ask mode bypassed prompt");
        check(OwnerApprovalPolicy.evaluate("auto",false,false,false)==OwnerApprovalPolicy.Decision.ASK,"setup unexpectedly bypassed prompt");
        for(String mode:new String[]{"ask","auto"}){
            check(OwnerApprovalPolicy.evaluate(mode,true,true,false)==OwnerApprovalPolicy.Decision.DENY,"cancel bypassed policy");
            check(OwnerApprovalPolicy.evaluate(mode,true,false,true)==OwnerApprovalPolicy.Decision.DENY,"lock bypassed policy");
        }
        System.out.println("PASS stored auto and ask never bypass cancellation or locked-device guards");
        check(OwnerApprovalPolicy.stillValid("ask","ask",true,false,false),"unchanged ask invalidated");
        check(OwnerApprovalPolicy.stillValid("auto","auto",true,false,false),"unchanged active auto invalidated");
        check(!OwnerApprovalPolicy.stillValid("ask","auto",true,false,false)&&!OwnerApprovalPolicy.stillValid("auto","ask",true,false,false),"mode changed during pending approval still valid");
        check(!OwnerApprovalPolicy.stillValid("auto","auto",false,false,false),"ended auto request retained approval");
        for(String mode:new String[]{"ask","auto"})check(!OwnerApprovalPolicy.stillValid(mode,mode,true,true,false)&&!OwnerApprovalPolicy.stillValid(mode,mode,true,false,true),"pending approval bypasses later cancel/lock");
        System.out.println("PASS pending approval loses validity after mode changes task end cancellation or lock");
        System.out.println("OwnerApprovalPolicy: 4 production policy checks passed; actual Android permissions and persistence separate emulator evidence");
    }
}
