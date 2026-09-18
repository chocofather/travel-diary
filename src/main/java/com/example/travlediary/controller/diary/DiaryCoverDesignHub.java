package com.example.travlediary.controller.diary;

import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * 표지 디자인 화면은 따로 페이지를 두지 않고, 나의 여행일기(/diaries) 위 패널로 연다.
 *
 * <p>예전 주소(/diaries/cover-designs, /diaries/cover-library...)로 직접 들어오면
 * /diaries 로 보내면서 패널을 열 자리만 쿼리로 넘긴다. 패널 스크립트가 그 값을 읽어 연다.
 * 한 번 더 이동하므로, 앞 요청이 남긴 안내 문구(flash)는 다음 화면까지 그대로 옮겨 준다.
 */
final class DiaryCoverDesignHub {

    /** 기본 진입. 패널이 표지 라이브러리 목록 + 내 보유 디자인으로 열린다. */
    static final String REDIRECT = "redirect:/diaries?coverDesigns=open";

    private static final List<String> FLASH_KEYS = List.of(
            "coverDesignMessage", "coverDesignError", "coverDesignAddedId",
            "coverLibraryMessage", "coverLibraryError");

    private DiaryCoverDesignHub() {
    }

    /** 표지 디자인 패널을 기본 화면으로 연다. */
    static String open(Model model, RedirectAttributes redirectAttributes) {
        carryFlash(model, redirectAttributes);
        return REDIRECT;
    }

    /**
     * 패널을 특정 라이브러리 화면(상세/정렬/쪽/내 공유 디자인)으로 연다.
     * 넘기는 값은 서버가 만든 라이브러리 주소이고, 스크립트도 라이브러리 주소일 때만 연다.
     * params 는 이름, 값 순서로 넘기며 비어 있는 값은 뺀다.
     */
    static String openLibrary(String libraryPath, Model model,
                              RedirectAttributes redirectAttributes, String... params) {
        carryFlash(model, redirectAttributes);
        UriComponentsBuilder library = UriComponentsBuilder.fromPath(libraryPath);
        for (int i = 0; i + 1 < params.length; i += 2) {
            if (params[i + 1] != null && !params[i + 1].isBlank()) {
                library.queryParam(params[i], params[i + 1].strip());
            }
        }
        String target = library.build().toUriString();
        return "redirect:" + UriComponentsBuilder.fromPath("/diaries")
                .queryParam("coverLibrary", target)
                .encode()
                .build()
                .toUriString();
    }

    private static void carryFlash(Model model, RedirectAttributes redirectAttributes) {
        for (String key : FLASH_KEYS) {
            Object value = model.getAttribute(key);
            if (value != null) {
                redirectAttributes.addFlashAttribute(key, value);
            }
        }
    }
}
