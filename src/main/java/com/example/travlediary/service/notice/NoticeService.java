package com.example.travlediary.service.notice;

import com.example.travlediary.dto.NoticeDetailDto;
import com.example.travlediary.dto.NoticeForm;
import com.example.travlediary.dto.NoticeListItemDto;
import com.example.travlediary.model.Notice;
import com.example.travlediary.repository.notice.NoticeMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper noticeMapper;
    private final PostContentSanitizer postContentSanitizer;

    @Transactional(readOnly = true)
    public List<NoticeListItemDto> getAdminList() {
        return noticeMapper.findAdminList();
    }

    @Transactional(readOnly = true)
    public NoticeForm getForm(Long id) {
        Notice notice = requireNotice(noticeMapper.findById(id));
        notice.setContent(postContentSanitizer.sanitize(notice.getContent()));
        return NoticeForm.from(notice);
    }

    @Transactional
    public Long create(NoticeForm form, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("관리자 정보를 확인할 수 없습니다.");
        }
        ValidatedNotice validated = validate(form);
        Notice notice = new Notice();
        notice.setTitle(validated.title());
        notice.setContent(validated.content());
        notice.setPinned(form.isPinned());
        notice.setViews(0);
        notice.setUserId(userId);

        if (noticeMapper.insertNotice(notice) != 1 || notice.getId() == null) {
            throw new IllegalStateException("공지사항 저장에 실패했습니다.");
        }
        return notice.getId();
    }

    @Transactional
    public void update(Long id, NoticeForm form) {
        Notice notice = requireNotice(noticeMapper.findByIdForUpdate(id));
        ValidatedNotice validated = validate(form);
        notice.setTitle(validated.title());
        notice.setContent(validated.content());
        notice.setPinned(form.isPinned());
        if (noticeMapper.updateNotice(notice) != 1) {
            throw notFound();
        }
    }

    @Transactional
    public void delete(Long id) {
        requireNotice(noticeMapper.findByIdForUpdate(id));
        if (noticeMapper.deleteNotice(id) != 1) {
            throw notFound();
        }
    }

    @Transactional(readOnly = true)
    public List<NoticeListItemDto> getPublicList(long offset, int limit) {
        return noticeMapper.findPublicList(offset, limit);
    }

    @Transactional(readOnly = true)
    public long countPublicList() {
        return noticeMapper.countPublicList();
    }

    @Transactional
    public NoticeDetailDto getPublicDetail(Long id) {
        if (noticeMapper.incrementPublicViews(id) != 1) {
            throw notFound();
        }
        NoticeDetailDto detail = noticeMapper.findPublicDetailById(id);
        if (detail == null) {
            throw notFound();
        }
        detail.setContent(postContentSanitizer.sanitize(detail.getContent()));
        return detail;
    }

    private ValidatedNotice validate(NoticeForm form) {
        if (form == null) {
            throw new NoticeValidationException(null, "공지사항을 입력해 주세요.");
        }
        String title = form.getTitle() == null ? "" : form.getTitle().strip();
        form.setTitle(title);
        if (title.isEmpty()) {
            throw new NoticeValidationException("title", "제목을 입력해 주세요.");
        }
        if (title.length() > 255) {
            throw new NoticeValidationException("title", "제목은 255자 이하로 입력해 주세요.");
        }

        String content = postContentSanitizer.sanitize(form.getContent());
        org.jsoup.nodes.Document document = Jsoup.parseBodyFragment(content);
        boolean hasText = !document.text().strip().isEmpty();
        boolean hasImage = !document.select("img[src]").isEmpty();
        if (!hasText && !hasImage) {
            throw new NoticeValidationException("content", "본문을 입력해 주세요.");
        }
        return new ValidatedNotice(title, content);
    }

    private Notice requireNotice(Notice notice) {
        if (notice == null) {
            throw notFound();
        }
        return notice;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다.");
    }

    private record ValidatedNotice(String title, String content) {
    }
}
