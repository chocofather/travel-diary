package com.example.travlediary.service.faq;

import java.util.Map;

/**
 * 공개 FAQ 카테고리 뱃지의 표시 클래스.
 *
 * <p>카테고리 이름은 언어에 따라 바뀌고(회원/계정 → Account → アカウント) 관리자가 고칠 수도 있으므로
 * 화면에 찍히는 글자를 스타일 판단에 쓰지 않는다. 판정 기준은 <b>카테고리 번호</b> 하나뿐이라
 * 어떤 언어로 보든, 이름을 바꾸든 같은 뱃지가 나온다.
 *
 * <p>기존에 스타일을 갖고 있던 다섯 카테고리만 알아보고, 관리자가 새로 만든 카테고리는
 * 중립 뱃지({@link #DEFAULT})로 둔다. 새 카테고리에 기존 색을 자동으로 나눠 주지 않는다.
 * (DB 에 코드/클래스 컬럼을 새로 만들지 않는다)
 */
public final class FaqCategoryBadge {

    /** 알아보지 못한 카테고리의 중립 뱃지. */
    public static final String DEFAULT = "is-default";

    /** 카테고리 번호 → 표시 클래스. 기존 공개 화면이 쓰던 클래스를 그대로 유지한다. */
    private static final Map<Long, String> BADGES_BY_CATEGORY_ID = Map.of(
            1L, "is-account",   // 회원/계정
            2L, "is-travel",    // 여행정보
            3L, "is-community", // 커뮤니티
            4L, "is-service",   // 서비스 이용
            5L, "is-etc"        // 기타
    );

    private FaqCategoryBadge() {
    }

    /** 카테고리 번호로 표시 클래스를 고른다. 이름이나 언어는 보지 않는다. */
    public static String of(Long categoryId) {
        if (categoryId == null) {
            return DEFAULT;
        }
        return BADGES_BY_CATEGORY_ID.getOrDefault(categoryId, DEFAULT);
    }
}
