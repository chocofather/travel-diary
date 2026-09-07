package com.example.travlediary.dto;

import com.example.travlediary.model.Notice;
import lombok.Data;

import java.util.List;

@Data
public class NoticeForm {
    private String title;
    private String content;
    private boolean pinned;

    /**
     * notice_translations. 언어별 제목·본문 입력 슬롯이다.
     * 0번은 한국어 자리이고 화면에 그리지 않는다 — 한국어는 위의 title/content 가 그대로 원문이다.
     */
    private List<NoticeTranslationForm> translations =
            NoticeTranslationForm.newTranslationSlots();

    public static NoticeForm from(Notice notice) {
        NoticeForm form = new NoticeForm();
        form.setTitle(notice.getTitle());
        form.setContent(notice.getContent());
        form.setPinned(notice.isPinned());
        return form;
    }
}
