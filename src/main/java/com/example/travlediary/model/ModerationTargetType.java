package com.example.travlediary.model;

/** 관리자 조치 대상 콘텐츠 종류. */
public enum ModerationTargetType {
    POST("게시글"),
    COURSE("여행코스"),
    POST_COMMENT("게시글 댓글"),
    COURSE_COMMENT("여행코스 댓글"),
    DESTINATION_COMMENT("여행지 댓글");

    private final String displayName;

    ModerationTargetType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
