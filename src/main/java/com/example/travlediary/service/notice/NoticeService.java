package com.example.travlediary.service.notice;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.NoticeDetailDto;
import com.example.travlediary.dto.NoticeForm;
import com.example.travlediary.dto.NoticeListItemDto;
import com.example.travlediary.dto.NoticeTranslationForm;
import com.example.travlediary.model.Notice;
import com.example.travlediary.model.NoticeTranslation;
import com.example.travlediary.repository.notice.NoticeMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoticeService {

    /** 제목 길이 제한. 한국어 원문과 번역이 같은 기준을 쓴다. (notices.title / notice_translations.title) */
    private static final int MAX_TITLE_LENGTH = 255;

    /** 번역 슬롯 언어. 한국어는 notices 원문이 대신하므로 여기에서 뺀다. */
    private static final Set<String> SUPPORTED_TRANSLATION_CODES = SupportedLanguage.all().stream()
            .filter(language -> language != SupportedLanguage.KOREAN)
            .map(SupportedLanguage::getLanguageTag)
            .collect(Collectors.toUnmodifiableSet());

    private final NoticeMapper noticeMapper;
    private final PostContentSanitizer postContentSanitizer;
    private final NoticeLocalizationService noticeLocalizationService;

    @Transactional(readOnly = true)
    public List<NoticeListItemDto> getAdminList() {
        return noticeMapper.findAdminList();
    }

    @Transactional(readOnly = true)
    public NoticeForm getForm(Long id) {
        Notice notice = requireNotice(noticeMapper.findById(id));
        notice.setContent(postContentSanitizer.sanitize(notice.getContent()));
        NoticeForm form = NoticeForm.from(notice);
        form.setTranslations(getTranslationForms(id));
        return form;
    }

    /**
     * 수정 화면 복원용 번역 슬롯. 저장된 줄이 있으면 채우고, 없는 언어는 빈 슬롯으로 둔다.
     *
     * <p>슬롯은 언어 코드로 찾아 채운다. 자리 번호나 조회 순서에 뜻을 두지 않는다.
     */
    @Transactional(readOnly = true)
    public List<NoticeTranslationForm> getTranslationForms(Long noticeId) {
        List<NoticeTranslationForm> slots = NoticeTranslationForm.newTranslationSlots();
        if (noticeId == null) {
            return slots;
        }

        Map<String, NoticeTranslationForm> slotsByLanguage = new LinkedHashMap<>();
        for (NoticeTranslationForm slot : slots) {
            slotsByLanguage.putIfAbsent(slot.getLanguageCode(), slot);
        }

        for (NoticeTranslation translation : storedTranslations(noticeId)) {
            NoticeTranslationForm slot = slotsByLanguage.get(translation.getLanguageCode());
            if (slot == null) {
                // 슬롯에 없는 언어가 남아 있어도 화면에는 그리지 않는다.
                continue;
            }
            slot.setTitle(translation.getTitle() == null ? "" : translation.getTitle());
            // 번역 본문도 원문과 같은 정제를 거쳐 편집기에 올린다.
            slot.setContent(postContentSanitizer.sanitize(translation.getContent()));
        }
        return slots;
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
        // 원문 저장과 같은 트랜잭션에서 번역까지 끝낸다. 비워 둔 언어는 줄을 만들지 않는다.
        saveTranslations(notice.getId(), form.getTranslations());
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
        // 원문 수정과 같은 트랜잭션에서 번역까지 끝낸다. 비운 언어는 그 줄만 지워진다.
        saveTranslations(id, form.getTranslations());
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

    /**
     * 공개 목록의 제목을 요청 언어로 바꿔 둔다.
     *
     * <p>번역은 목록 전체를 한 번에 읽는다. 조회 결과 DTO 는 이 요청에서만 쓰는 값이라
     * 표시할 제목을 그대로 담아도 관리자 화면이 읽는 원문에는 영향이 없다.
     * 정렬·페이징·고정·조회수 같은 값은 건드리지 않는다.
     */
    @Transactional(readOnly = true)
    public void localizePublicList(List<NoticeListItemDto> notices,
                                   SupportedLanguage requestedLanguage) {
        if (notices == null || notices.isEmpty()) {
            // 볼 공지가 없으면 번역도 읽지 않는다.
            return;
        }

        Map<Long, String> baseTitles = new LinkedHashMap<>();
        for (NoticeListItemDto item : notices) {
            if (item != null && item.getId() != null) {
                baseTitles.putIfAbsent(item.getId(), item.getTitle());
            }
        }
        if (baseTitles.isEmpty()) {
            return;
        }

        Map<Long, String> localizedTitles = noticeLocalizationService
                .resolveLocalizedTitlesByNoticeIds(baseTitles, requestedLanguage);
        for (NoticeListItemDto item : notices) {
            if (item == null || item.getId() == null) {
                continue;
            }
            String title = localizedTitles.get(item.getId());
            if (title != null) {
                item.setTitle(title);
            }
        }
    }

    /**
     * 공개 상세의 제목·본문을 요청 언어로 바꿔 둔다.
     *
     * <p>제목과 본문은 각각 따로 대체되므로 한쪽만 번역돼 있어도 된다.
     * 최종 표시 본문은 원문이든 번역이든 sanitize 를 <b>정확히 한 번</b> 거쳐 화면으로 나간다.
     * 한국어 원문은 {@link #getPublicDetail(Long)} 에서 이미 거쳤으므로, 번역 본문으로
     * 바뀐 경우에만 여기서 정제한다.
     */
    @Transactional(readOnly = true)
    public void localizePublicDetail(NoticeDetailDto detail,
                                     SupportedLanguage requestedLanguage) {
        if (detail == null || detail.getId() == null) {
            return;
        }

        NoticeTranslation display = noticeLocalizationService.resolveLocalizedContent(
                detail.getId(), detail.getTitle(), detail.getContent(), requestedLanguage);
        detail.setTitle(display.getTitle());
        if (!Objects.equals(display.getContent(), detail.getContent())) {
            detail.setContent(postContentSanitizer.sanitize(display.getContent()));
        }
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
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new NoticeValidationException("title", "제목은 255자 이하로 입력해 주세요.");
        }

        // 한국어 원문 본문은 필수다. (번역 본문은 선택이라 같은 검증을 걸지 않는다)
        String content = postContentSanitizer.sanitize(form.getContent());
        if (!NoticeContent.hasContent(content)) {
            throw new NoticeValidationException("content", "본문을 입력해 주세요.");
        }
        return new ValidatedNotice(title, content);
    }

    /**
     * 공지사항 번역을 언어 한 줄씩 저장한다. (여행정보 번역과 같은 정책)
     *
     * <p>한국어는 notices 원문이 대신하므로 ko 슬롯이 섞여 들어와도 쓰지 않는다.
     * 나머지 언어는 제목·본문 중 하나라도 값이 있으면 없던 줄은 INSERT, 있던 줄은 UPDATE 하고,
     * 둘 다 비우면 그 언어 줄만 DELETE 한다. 다른 언어 줄은 건드리지 않는다.
     */
    private void saveTranslations(Long noticeId, List<NoticeTranslationForm> translationForms) {
        if (noticeId == null || translationForms == null) {
            return;
        }

        // 기존 줄은 한 번만 읽고 언어 코드로 찾아 쓴다.
        Map<String, NoticeTranslation> existing = new LinkedHashMap<>();
        for (NoticeTranslation translation : storedTranslations(noticeId)) {
            existing.putIfAbsent(translation.getLanguageCode(), translation);
        }

        Set<String> handledLanguages = new HashSet<>();
        for (NoticeTranslationForm form : translationForms) {
            if (form == null || form.getLanguageCode() == null) {
                continue;
            }
            String languageCode = form.getLanguageCode();
            if (!SUPPORTED_TRANSLATION_CODES.contains(languageCode)) {
                // 화면이 정한 슬롯 언어만 저장한다. ko 슬롯과 임의 언어 코드는 무시한다.
                continue;
            }
            if (!handledLanguages.add(languageCode)) {
                // 같은 언어가 두 번 들어오면 앞의 값만 쓴다. (UNIQUE 충돌을 만들지 않는다)
                continue;
            }
            saveTranslation(translationOf(noticeId, form), existing.containsKey(languageCode));
        }
    }

    /** 제목·본문이 모두 없으면 그 언어 줄을 남기지 않는다. */
    private void saveTranslation(NoticeTranslation translation, boolean exists) {
        if (translation.getTitle() == null && translation.getContent() == null) {
            if (exists) {
                noticeMapper.deleteTranslation(
                        translation.getNoticeId(), translation.getLanguageCode());
            }
            return;
        }
        if (exists) {
            noticeMapper.updateTranslation(translation);
        } else {
            noticeMapper.insertTranslation(translation);
        }
    }

    private NoticeTranslation translationOf(Long noticeId, NoticeTranslationForm form) {
        NoticeTranslation translation = new NoticeTranslation();
        translation.setNoticeId(noticeId);
        translation.setLanguageCode(form.getLanguageCode());
        translation.setTitle(translationTitle(form.getTitle()));
        translation.setContent(translationContent(form.getContent()));
        return translation;
    }

    /** 번역 제목도 원문과 같은 기준으로 다듬는다. 비어 있는 것은 오류가 아니라 '없음'이다. */
    private String translationTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String stripped = title.strip();
        if (stripped.length() > MAX_TITLE_LENGTH) {
            throw new NoticeValidationException(null, "번역 제목은 255자 이하로 입력해 주세요.");
        }
        return stripped;
    }

    /** 번역 본문도 원문과 같은 sanitize 를 거친다. 태그만 남은 Quill HTML 은 값이 없는 것으로 본다. */
    private String translationContent(String content) {
        String sanitized = postContentSanitizer.sanitize(content);
        return NoticeContent.hasContent(sanitized) ? sanitized : null;
    }

    /** 언어 코드가 없는 줄은 어느 슬롯에도 맞출 수 없으므로 걸러 낸다. */
    private List<NoticeTranslation> storedTranslations(Long noticeId) {
        List<NoticeTranslation> stored = noticeMapper.findTranslationsByNoticeId(noticeId);
        if (stored == null) {
            return List.of();
        }
        List<NoticeTranslation> usable = new ArrayList<>();
        for (NoticeTranslation translation : stored) {
            if (translation != null && translation.getLanguageCode() != null) {
                usable.add(translation);
            }
        }
        return usable;
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
