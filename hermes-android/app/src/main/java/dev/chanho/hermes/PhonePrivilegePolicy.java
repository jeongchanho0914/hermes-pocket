package dev.chanho.hermes;

/** Pure validation shared by the app and the shell service. */
final class PhonePrivilegePolicy {
    static void validatePackage(String pkg){
        if(pkg==null||!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))throw new IllegalArgumentException("잘못된 패키지 이름입니다.");
    }
    static boolean protectedPackage(String pkg){
        return pkg==null||pkg.equals("android")||pkg.startsWith("dev.chanho.hermes")
                ||pkg.equals("moe.shizuku.privileged.api")||pkg.startsWith("com.android.systemui")
                ||pkg.contains("permissioncontroller")||pkg.contains("packageinstaller")
                ||pkg.equals("com.android.settings")||pkg.contains("inputmethod")
                ||pkg.contains("authenticator")||pkg.contains("magisk")
                ||pkg.contains("kernelsu")||pkg.contains("superuser");
    }
    static void requireAppControl(boolean globalScope,String pkg){
        validatePackage(pkg);
        if(!globalScope)throw new SecurityException("앱 제어가 꺼져 있습니다.");
        if(protectedPackage(pkg))throw new SecurityException("시스템 권한·인증·기기 제어 관리 앱은 강제 종료할 수 없습니다.");
    }
    static void validateSetting(String name){
        if(!"screen_brightness".equals(name)&&!"screen_brightness_mode".equals(name)
                &&!"accelerometer_rotation".equals(name)&&!"user_rotation".equals(name)
                &&!"screen_off_timeout".equals(name))throw new SecurityException("지원하지 않는 시스템 설정입니다.");
    }
    static void validateSettingValue(String name,long value){
        validateSetting(name);
        long minimum=0,maximum;
        if("screen_brightness".equals(name))maximum=255;
        else if("screen_brightness_mode".equals(name)||"accelerometer_rotation".equals(name))maximum=1;
        else if("screen_off_timeout".equals(name)){minimum=15000;maximum=1800000;}
        else if("user_rotation".equals(name))maximum=3;
        else throw new SecurityException("이 시스템 설정은 읽기만 지원합니다.");
        if(value<minimum||value>maximum)throw new IllegalArgumentException("시스템 설정 값이 허용 범위를 벗어났습니다.");
    }
}
