package dev.chanho.hermes;

/** Owner preferences never grant Android permissions or bypass a cancelled/locked request. */
final class OwnerApprovalPolicy {
    enum Decision {DENY,ASK,AUTO}
    static String mode(String value){if("ask".equals(value)||"auto".equals(value))return value;throw new IllegalArgumentException("승인 방식은 ask 또는 auto로 선택하세요.");}
    static String scope(String value){if("all".equals(value)||"none".equals(value))return value;throw new IllegalArgumentException("앱 제어 범위는 all 또는 none으로 선택하세요.");}
    static boolean allowsApp(String scope,String target,boolean protectedTarget){return "all".equals(scope)&&target!=null&&!target.isEmpty()&&!protectedTarget;}
    static Decision evaluate(String mode,boolean activeRequest,boolean cancelled,boolean locked){
        if(cancelled||locked)return Decision.DENY;
        return "auto".equals(mode)&&activeRequest?Decision.AUTO:Decision.ASK;
    }
    static boolean stillValid(String originalMode,String currentMode,boolean activeRequest,boolean cancelled,boolean locked){
        if(!originalMode.equals(currentMode)||cancelled||locked)return false;
        return !"auto".equals(originalMode)||activeRequest;
    }
}
