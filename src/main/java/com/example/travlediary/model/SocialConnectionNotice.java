package com.example.travlediary.model;

import java.io.Serializable;

public record SocialConnectionNotice(Type type, SocialProvider provider)
        implements Serializable {

    public static final String SESSION_ATTRIBUTE = "socialConnectionNotice";

    public enum Type {
        CONNECTED,
        ALREADY_CONNECTED,
        ERROR,
        DISCONNECTED,
        ALREADY_DISCONNECTED,
        LAST_LOGIN_METHOD,
        DISCONNECT_ERROR
    }
}
