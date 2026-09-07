package com.example.travlediary.service.notice;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.NoticeTranslation;
import com.example.travlediary.repository.notice.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 공지사항 번역을 읽어 공개 화면에 쓸 제목·본문을 만든다.
 *
 * <p>대체는 <b>필드마다 따로</b>, 그리고 <b>요청 언어 → 한국어 원문</b> 한 단계까지만 한다.
 * 고객센터 공지는 관리자가 쓴 한국어 원문이 늘 있으므로, 요청 언어 줄이 없다고
 * 다른 언어(en → ja 등) 번역을 찾아가지 않는다. 여행정보의 "남은 언어" 단계는 쓰지 않는다.
 *
 * <p>한국어 요청은 번역을 아예 읽지 않고 원문을 그대로 쓴다.
 *
 * <p><b>공개 화면 전용이다.</b> 표시할 값만 만들어 돌려주고 관리자 경로가 읽는 원문은
 * 건드리지 않는다. 본문 정제(sanitize)는 표시 직전에 부르는 쪽에서 한다.
 */
@Service
@RequiredArgsConstructor
public class NoticeLocalizationService {

    private final NoticeMapper noticeMapper;

    /**
     * 공지 한 건의 표시용 제목·본문. 번역은 이 안에서 한 번 읽는다.
     *
     * @param baseTitle   notices 원문 제목 (대체 값)
     * @param baseContent notices 원문 본문 (대체 값)
     * @return 제목·본문이 모두 채워진 표시용 값. 번역이 없으면 원문 그대로다.
     */
    @Transactional(readOnly = true)
    public NoticeTranslation resolveLocalizedContent(Long noticeId,
                                                     String baseTitle,
                                                     String baseContent,
                                                     SupportedLanguage requestedLanguage) {
        if (noticeId == null || isKorean(requestedLanguage)) {
            // 한국어 요청과 번호가 없는 공지는 읽을 번역이 없다.
            return display(baseTitle, baseContent);
        }

        String languageCode = requestedLanguage.getLanguageTag();
        NoticeTranslation requested = null;
        List<NoticeTranslation> stored = noticeMapper.findTranslationsByNoticeId(noticeId);
        if (stored != null) {
            for (NoticeTranslation translation : stored) {
                // 요청 언어 줄만 본다. 다른 언어 줄은 대체 후보가 아니다.
                if (translation != null
                        && languageCode.equals(translation.getLanguageCode())) {
                    requested = translation;
                    break;
                }
            }
        }
        return display(localizedTitle(requested, baseTitle),
                localizedContent(requested, baseContent));
    }

    /**
     * 목록용. 번역을 <b>한 번의 조회</b>로 모아 읽어 공지마다 표시용 제목을 만든다.
     *
     * @param baseTitles 공지 번호 → 원문 제목. 여기 담긴 번호가 조회 대상이다.
     * @return 공지 번호 → 표시용 제목. 번역이 없으면 원문 제목이 그대로 들어 있다.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> resolveLocalizedTitlesByNoticeIds(
            Map<Long, String> baseTitles, SupportedLanguage requestedLanguage) {
        if (baseTitles == null || baseTitles.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> titles = new LinkedHashMap<>(baseTitles);
        if (isKorean(requestedLanguage)) {
            // 한국어 요청은 번역을 읽지 않는다.
            return titles;
        }

        List<Long> noticeIds = baseTitles.keySet().stream().filter(Objects::nonNull).toList();
        if (noticeIds.isEmpty()) {
            return titles;
        }

        String languageCode = requestedLanguage.getLanguageTag();
        List<NoticeTranslation> stored = noticeMapper.findTranslationsByNoticeIds(noticeIds);
        if (stored == null) {
            return titles;
        }
        for (NoticeTranslation translation : stored) {
            if (translation == null || translation.getNoticeId() == null
                    || !languageCode.equals(translation.getLanguageCode())) {
                continue;
            }
            String title = nonBlank(translation.getTitle());
            if (title != null && titles.containsKey(translation.getNoticeId())) {
                titles.put(translation.getNoticeId(), title);
            }
        }
        return titles;
    }

    private boolean isKorean(SupportedLanguage requestedLanguage) {
        return requestedLanguage == null || requestedLanguage == SupportedLanguage.KOREAN;
    }

    /** 번역 제목이 비어 있으면 한국어 원문을 쓴다. */
    private String localizedTitle(NoticeTranslation requested, String baseTitle) {
        String title = requested == null ? null : nonBlank(requested.getTitle());
        return title == null ? baseTitle : title;
    }

    /** 태그만 남은 번역 본문은 값이 없는 것으로 보고 한국어 원문을 쓴다. */
    private String localizedContent(NoticeTranslation requested, String baseContent) {
        String content = requested == null ? null : requested.getContent();
        return NoticeContent.hasContent(content) ? content : baseContent;
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private NoticeTranslation display(String title, String content) {
        NoticeTranslation display = new NoticeTranslation();
        display.setTitle(title);
        display.setContent(content);
        return display;
    }
}
