package com.example.travlediary.dto;

import com.example.travlediary.model.Notice;
import lombok.Data;

@Data
public class NoticeForm {
    private String title;
    private String content;
    private boolean pinned;

    public static NoticeForm from(Notice notice) {
        NoticeForm form = new NoticeForm();
        form.setTitle(notice.getTitle());
        form.setContent(notice.getContent());
        form.setPinned(notice.isPinned());
        return form;
    }
}
