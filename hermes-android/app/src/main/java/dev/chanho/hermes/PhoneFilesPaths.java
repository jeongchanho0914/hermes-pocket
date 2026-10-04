package dev.chanho.hermes;

/** Relative document-name policy; independent of Android for security boundary tests. */
final class PhoneFilesPaths {
    private PhoneFilesPaths(){}
    static String[] parts(String path){
        if(path==null||path.length()>400||path.startsWith("/")||path.endsWith("/")||path.contains("\\")||path.contains(":")||path.matches("(?s).*[\\x00-\\x1f\\x7f].*"))throw new IllegalArgumentException("선택한 폴더 안의 상대 경로를 입력하세요.");
        if(path.isEmpty())return new String[0];String[] parts=path.split("/",-1);
        if(parts.length>12)throw new IllegalArgumentException("하위 폴더 깊이는 12단계까지 지원합니다.");
        for(String part:parts)if(part.isEmpty()||part.equals(".")||part.equals("..")||part.length()>255)throw new IllegalArgumentException("파일 경로가 올바르지 않습니다.");
        return parts;
    }
}
