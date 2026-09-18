package com.example.travlediary.service.user;

import java.util.regex.Pattern;

public final class PasswordPolicy {

    public static final String INVALID_MESSAGE =
            "비밀번호는 8자 이상 72자 이하이며, 영문, 숫자, !@#$%^&*만 사용하고 "
                    + "특수문자를 1개 이상 포함해야 합니다.";
    public static final String MISMATCH_MESSAGE = "새 비밀번호가 일치하지 않습니다.";

    /**
     * 받을 수 있는 최대 길이.
     *
     * <p>BCrypt 는 72바이트까지만 실제로 쓴다. 그보다 긴 값은 예전 Spring Security 에서는 조용히
     * 잘려 서로 다른 비밀번호가 같은 해시가 됐고, 지금 판에서는 해싱 자체가 예외로 끝난다.
     * 그래서 해싱까지 내려보내지 않고 여기에서 평범한 입력 오류로 돌려준다.
     *
     * <p>허용 문자가 ASCII 뿐이라 한 글자가 곧 한 바이트다. 72자면 사람이 쓰는 비밀번호와
     * 비밀번호 관리자가 만드는 값(보통 64자 이하) 모두 넉넉히 들어간다.
     *
     * <p>이 상한은 새로 정하거나 바꿀 때만 본다. 로그인은 이 검사를 지나지 않으므로 예전에
     * 더 긴 값으로 가입한 회원의 로그인은 그대로 된다. (저장된 해시도 건드리지 않는다)
     */
    public static final int MAX_LENGTH = 72;

    private static final Pattern ALLOWED_PATTERN = Pattern.compile(
            "^(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]{8," + MAX_LENGTH + "}$");

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || !ALLOWED_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(INVALID_MESSAGE);
        }
    }

    public static boolean isValid(String password) {
        return password != null && ALLOWED_PATTERN.matcher(password).matches();
    }

    public static void validateConfirmation(String password, String passwordConfirmation) {
        if (password == null || !password.equals(passwordConfirmation)) {
            throw new IllegalArgumentException(MISMATCH_MESSAGE);
        }
    }
}
