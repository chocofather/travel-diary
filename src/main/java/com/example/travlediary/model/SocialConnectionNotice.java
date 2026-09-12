package com.example.travlediary.model;

import java.io.Serializable;

public record SocialConnectionNotice(Type type, SocialProvider provider)
        implements Serializable {

    public static final String SESSION_ATTRIBUTE = "socialConnectionNotice";

    public enum Type {
        CONNECTED,
        ALREADY_CONNECTED,
        /** 연결 대상과 다른 계정으로 로그인해 소셜 계정을 붙이지 않았다. */
        TARGET_MISMATCH,
        ERROR,
        DISCONNECTED,
        ALREADY_DISCONNECTED,
        LAST_LOGIN_METHOD,
        DISCONNECT_ERROR
    }
}
