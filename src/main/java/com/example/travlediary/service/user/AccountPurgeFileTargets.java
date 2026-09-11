package com.example.travlediary.service.user;

import java.util.List;

/**
 * FILE_DELETE task 로 남겨도 되는 파일 경로인지 판정한다.
 *
 * <p>나중에 파일을 지우는 worker 가 target_value 를 그대로 믿게 두지 않으려고, task 를
 * 만드는 이 단계에서 먼저 거른다. 회원이 지운 뒤 남는 개인 업로드 폴더만 허용하고,
 * 스티커 같은 정적 리소스(/images/...)나 공개 콘텐츠 업로드 폴더는 통과시키지 않는다.
 */
public final class AccountPurgeFileTargets {

    /** 이 회원만 쓰는 업로드 폴더. 공개 콘텐츠(posts, comments, editor)는 들어 있지 않다. */
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "/uploads/profiles/",
            "/uploads/diary-covers/",
            "/uploads/diary-pages/",
            "/uploads/diary-cover-designs/");

    /** account_purge_tasks.target_value 의 컬럼 길이. */
    private static final int MAX_TARGET_LENGTH = 1024;

    private AccountPurgeFileTargets() {
    }

    /**
     * @return 허용 경로면 앞뒤 공백을 없앤 값, 아니면 null
     */
    public static String normalizeDeletable(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }
        String value = imageUrl.strip();
        if (value.isEmpty() || value.length() > MAX_TARGET_LENGTH) {
            return null;
        }
        String prefix = allowedPrefix(value);
        if (prefix == null) {
            return null;
        }
        return isSimpleFileName(value.substring(prefix.length())) ? value : null;
    }

    private static String allowedPrefix(String value) {
        for (String prefix : ALLOWED_PREFIXES) {
            if (value.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    /**
     * 허용 폴더 바로 아래의 파일 하나만 지운다.
     * 하위 경로, 상위로 올라가는 경로, 제어문자가 섞인 값은 받지 않는다.
     */
    private static boolean isSimpleFileName(String fileName) {
        if (fileName.isEmpty() || fileName.equals(".") || fileName.equals("..")) {
            return false;
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")
                || fileName.contains("?") || fileName.contains("#") || fileName.contains(":")) {
            return false;
        }
        for (int index = 0; index < fileName.length(); index++) {
            char character = fileName.charAt(index);
            if (character < 0x20 || character == 0x7f || Character.isWhitespace(character)) {
                return false;
            }
        }
        return true;
    }
}
