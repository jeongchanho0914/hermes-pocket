package dev.chanho.hermes;
import java.util.Arrays;
public final class PhoneFilesPathsHarness {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void denied(String value){try{PhoneFilesPaths.parts(value);throw new AssertionError("unsafe path accepted: "+value);}catch(IllegalArgumentException expected){}}
    static String repeat(String value,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(value);return b.toString();}
    public static void main(String[] args){
        check(PhoneFilesPaths.parts("").length==0,"folder root listing unavailable");
        check(Arrays.equals(PhoneFilesPaths.parts("문서/새 파일.txt"),new String[]{"문서","새 파일.txt"}),"Unicode names lost");
        check(PhoneFilesPaths.parts("a/"+repeat("b",255))[1].length()==255,"boundary name rejected");
        check(PhoneFilesPaths.parts(repeat("a/",11)+"a").length==12,"maximumdepth rejected");
        System.out.println("PASS relative root Unicode nested and valid boundary paths");
        for(String path:new String[]{null,"/etc/passwd","..","a/../b",".","a/./b","a//b","a/","a\\b","content://provider/document","file:///etc/a","a\u0000b","a\nb","a\u007fb"})denied(path);
        System.out.println("PASS traversal absolute URI separator and control paths rejected");
        denied(repeat("x",256));denied(repeat("a/",12)+"a");denied(repeat("a/",200)+"a");
        check(PhoneFilesPaths.parts(repeat("a",199)+"/"+repeat("b",200)).length==2,"path400 boundary rejected");
        denied(repeat("a",199)+"/"+repeat("b",201));
        System.out.println("PASS filename depth and total-path limits enforced");
        System.out.println("PhoneFilesPaths: 3 production path-policy tests passed; actual SAF IO separate emulator checks");
    }
}
