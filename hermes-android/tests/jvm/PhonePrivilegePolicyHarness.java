package dev.chanho.hermes;
public final class PhonePrivilegePolicyHarness {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void denied(Runnable action){try{action.run();throw new AssertionError("unsafe privilege request accepted");}catch(IllegalArgumentException|SecurityException expected){}}
    public static void main(String[] args){
        PhonePrivilegePolicy.requireAppControl(true,"dev.hermesfixture.android");
        PhonePrivilegePolicy.validatePackage("com.example.app_2");
        denied(()->PhonePrivilegePolicy.requireAppControl(false,"dev.hermesfixture.android"));
        for(String pkg:new String[]{null,"","android","com.example;reboot","com.example app","com.example\nreboot","com.example.$(id)","../app","com..example"})denied(()->PhonePrivilegePolicy.validatePackage(pkg));
        System.out.println("PASS typed app control rejects disabled scope malformed packages and command injection");
        for(String pkg:new String[]{"dev.chanho.hermes","moe.shizuku.privileged.api","com.android.systemui","com.android.settings","com.android.permissioncontroller","com.android.packageinstaller","com.example.inputmethod","com.topjohnwu.magisk","me.weishu.kernelsu","com.example.authenticator"}){
            check(PhonePrivilegePolicy.protectedPackage(pkg),"privileged protected package missing: "+pkg);
            denied(()->PhonePrivilegePolicy.requireAppControl(true,pkg));
        }
        check(!PhonePrivilegePolicy.protectedPackage("dev.hermesfixture.android"),"ordinary fixture unexpectedly protected");
        System.out.println("PASS typed force-stop refuses permission authentication and privilege-controller targets");
        for(String key:new String[]{"screen_brightness","screen_brightness_mode","accelerometer_rotation","user_rotation","screen_off_timeout"})PhonePrivilegePolicy.validateSetting(key);
        for(String key:new String[]{null,"","android_id","secure/android_id","global/adb_enabled","screen_brightness;reboot","screen_off_timeout\nreboot","unknown"})denied(()->PhonePrivilegePolicy.validateSetting(key));
        System.out.println("PASS typed setting whitelist refuses identifiers other namespaces and command injection");
        for(long value:new long[]{0,255})PhonePrivilegePolicy.validateSettingValue("screen_brightness",value);
        for(long value:new long[]{-1,256,Long.MAX_VALUE})denied(()->PhonePrivilegePolicy.validateSettingValue("screen_brightness",value));
        for(String key:new String[]{"screen_brightness_mode","accelerometer_rotation"}){
            PhonePrivilegePolicy.validateSettingValue(key,0);PhonePrivilegePolicy.validateSettingValue(key,1);
            denied(()->PhonePrivilegePolicy.validateSettingValue(key,2));denied(()->PhonePrivilegePolicy.validateSettingValue(key,-1));
        }
        PhonePrivilegePolicy.validateSettingValue("screen_off_timeout",15000);PhonePrivilegePolicy.validateSettingValue("screen_off_timeout",1800000);
        denied(()->PhonePrivilegePolicy.validateSettingValue("screen_off_timeout",14999));denied(()->PhonePrivilegePolicy.validateSettingValue("screen_off_timeout",1800001));
        for(long rotation:new long[]{0,1,2,3})PhonePrivilegePolicy.validateSettingValue("user_rotation",rotation);
        for(long rotation:new long[]{-1,4,Long.MAX_VALUE})denied(()->PhonePrivilegePolicy.validateSettingValue("user_rotation",rotation));
        System.out.println("PASS typed write and rotation bounds prevent uncontrolled settings mutation");
        System.out.println("PhonePrivilegePolicy: 4 production validation checks passed; no binder or privileged process simulated");
    }
}
