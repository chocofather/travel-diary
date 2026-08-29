package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverStyle;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 커스텀 표지가 저장 전에 거르는 값들.
 *
 * <p>보관함 원본(diary_cover_designs)과 실제 적용본(diary_covers)이 같은 컬럼을 쓰므로
 * 두 서비스가 같은 규칙을 나눠 쓴다. DB 의 CHECK/기본값과 같은 값을 여기에 적어 둔다.
 * 페이지 다꾸 쪽 검증(DiaryElementServiceImpl)은 그대로 두고 건드리지 않는다.
 */
final class DiaryCoverValues {

    /** 디자인 이름 길이 상한 (diary_cover_designs.name VARCHAR(50)) */
    static final int NAME_MAX = 50;
    /** 표지 바탕색은 #RRGGBB 만 저장한다. (background_color VARCHAR(7)) */
    private static final Pattern BACKGROUND_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    /**
     * 표지에 올릴 수 있는 요소 유형. (DB chk_..._type 과 같은 값)
     * TEXT 는 컬럼과 CHECK 만 열어 둔 상태다. 화면은 후속 단계이고, 글꼴/색/정렬 같은 값이
     * 필요해지면 NULL 허용 컬럼을 더하는 방식으로 넓힌다.
     */
    static final Set<String> ALLOWED_ELEMENT_TYPES = Set.of("PHOTO", "STICKER", "NOTE", "TEXT");

    private DiaryCoverValues() {
    }

    /** 로그인하지 않은 요청은 여기서 끊는다. */
    static void requireUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    /** 디자인 이름. 앞뒤 공백을 정리하고 빈 이름은 막는다. (이름 중복은 허용한다) */
    static String name(String value) {
        String name = value == null ? "" : value.strip();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "디자인 이름을 입력해 주세요.");
        }
        if (name.length() > NAME_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "디자인 이름은 " + NAME_MAX + "자 이하로 입력해 주세요.");
        }
        return name;
    }

    /**
     * 바탕으로 쓸 기본 표지 스타일.
     * 고르지 않았으면 기본 표지로 두고, 값이 왔다면 아는 표지 스타일만 통과시킨다.
     * (기존 표지 목록 DiaryCoverStyle 을 그대로 쓴다. 표지 전용 목록을 따로 두지 않는다)
     */
    static String baseCoverStyle(String value) {
        String style = value == null ? "" : value.strip();
        if (style.isEmpty()) {
            return DiaryCoverStyle.DEFAULT.getCode();
        }
        if (!DiaryCoverStyle.isSupported(style)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "표지 스타일을 다시 선택해 주세요.");
        }
        return style;
    }

    /** 표지 바탕색. 없어도 되고, 값이 있으면 #RRGGBB 만 허용한다. */
    static String backgroundColor(String value) {
        String color = value == null ? "" : value.strip();
        if (color.isEmpty()) {
            return null;
        }
        if (!BACKGROUND_COLOR.matcher(color).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "표지 색상을 다시 선택해 주세요.");
        }
        return color;
    }

    /** 저장할 수 있는 요소 유형인지 확인한다. */
    static String elementType(String value) {
        String type = value == null ? "" : value.strip().toUpperCase();
        if (!ALLOWED_ELEMENT_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "표지에 올릴 수 없는 요소입니다.");
        }
        return type;
    }
}
