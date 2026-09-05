package com.example.travlediary.dto;

import com.example.travlediary.config.i18n.SupportedLanguage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 이벤트의 언어별 입력 슬롯.
 *
 * <p>한국어는 기존 제목·상세 내용 입력이 그대로 base 이자 ko 번역이 되므로 화면에 그리지 않는다.
 * 언어 코드는 화면이 고르지 않고 슬롯에 고정한다. (canonical: en / ja / zh-CN / zh-TW)
 *
 * <p>언어별 인포그래픽 포스터는 파일 선택과 삭제 체크만 받는다.
 * 저장된 경로는 폼에 싣지 않는다 — 화면에 보여 줄 미리보기 경로도, 저장 판단 기준도
 * 서버가 DB 에서 읽은 값만 쓴다.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventTranslationForm {
    private String languageCode;
    private String title;
    private String description;

    /** 새로 올린 언어별 포스터. 비어 있으면 저장된 포스터를 그대로 둔다. */
    private MultipartFile posterFile;

    /** 저장된 언어별 포스터를 지울지 여부. 새 파일이 함께 오면 새 파일이 이긴다. */
    private boolean removePoster;

    public EventTranslationForm(String languageCode) {
        this.languageCode = languageCode;
    }

    public EventTranslationForm(String languageCode, String title, String description) {
        this.languageCode = languageCode;
        this.title = title;
        this.description = description;
    }

    /** canonical 순서(ko / en / ja / zh-CN / zh-TW)로 빈 슬롯을 만든다. */
    public static List<EventTranslationForm> newTranslationSlots() {
        List<EventTranslationForm> slots = new ArrayList<>();
        for (SupportedLanguage language : SupportedLanguage.all()) {
            slots.add(new EventTranslationForm(language.getLanguageTag(), "", ""));
        }
        return slots;
    }
}
