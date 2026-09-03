package com.example.travlediary.service.search;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.GlobalSearchPage;
import com.example.travlediary.dto.GlobalSearchResultDto;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.repository.search.GlobalSearchMapper;
import com.example.travlediary.service.destination.DestinationLocalizationService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GlobalSearchServiceImpl implements GlobalSearchService {

    static final int PAGE_SIZE = 10;
    static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_SUMMARY_LENGTH = 180;

    private static final String DESTINATION_TYPE = "destination";

    private final GlobalSearchMapper globalSearchMapper;
    /** 결과에 보이는 여행지 이름·요약을 현재 언어로 바꾼다. (여행지 목록과 같은 일괄 조회) */
    private final DestinationLocalizationService destinationLocalizationService;
    private final MessageSource messageSource;

    @Override
    public GlobalSearchPage search(String query, String type, int page,
                                   SupportedLanguage requestedLanguage) {
        SupportedLanguage language = requestedLanguage == null
                ? SupportedLanguage.KOREAN
                : requestedLanguage;
        String normalizedQuery = normalizeQuery(query);
        GlobalSearchType normalizedType = GlobalSearchType.from(type);
        if (normalizedQuery == null) {
            return emptyPage(normalizedType.getQueryValue());
        }

        String keywordPattern = toLikeLiteral(normalizedQuery);
        long totalCount = globalSearchMapper.count(
                keywordPattern, normalizedType.getQueryValue(), language.getLanguageTag());
        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / PAGE_SIZE);
        int currentPage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
        long offset = (long) (currentPage - 1) * PAGE_SIZE;
        List<GlobalSearchResultDto> results = totalCount == 0
                ? List.of()
                : globalSearchMapper.search(
                        keywordPattern, normalizedType.getQueryValue(),
                        language.getLanguageTag(), offset, PAGE_SIZE);
        localizeDestinationResults(results, language);
        results.forEach(result -> result.setSummary(toPlainSummary(result.getSummary(), language)));

        int pageStart = Math.max(1, currentPage - 2);
        int pageEnd = Math.min(totalPages, pageStart + 4);
        pageStart = Math.max(1, pageEnd - 4);
        return new GlobalSearchPage(
                normalizedQuery,
                normalizedType.getQueryValue(),
                results,
                totalCount,
                currentPage,
                PAGE_SIZE,
                totalPages,
                pageStart,
                pageEnd);
    }

    private GlobalSearchPage emptyPage(String type) {
        return new GlobalSearchPage(null, type, List.of(), 0, 1, PAGE_SIZE, 0, 1, 0);
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) <= MAX_QUERY_LENGTH) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, MAX_QUERY_LENGTH);
        return normalized.substring(0, endIndex);
    }

    private String toLikeLiteral(String query) {
        return query.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /**
     * 이번 페이지에 나온 여행지 이름·요약을 현재 언어로 바꾼다.
     *
     * <p>여행지 번호를 모아 한 번만 읽는다. 번역이 없으면 원래 값이 그대로 남는다.
     * 사용자가 쓴 글(커뮤니티·코스 등)은 손대지 않는다.
     */
    private void localizeDestinationResults(List<GlobalSearchResultDto> results,
                                            SupportedLanguage language) {
        List<Long> destinationIds = results.stream()
                .filter(result -> DESTINATION_TYPE.equals(result.getType()))
                .map(GlobalSearchResultDto::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (destinationIds.isEmpty()) {
            return;
        }

        Map<Long, DestinationTranslation> localizedContent =
                destinationLocalizationService.resolveLocalizedContentByDestinationIds(
                        destinationIds, language);
        for (GlobalSearchResultDto result : results) {
            if (!DESTINATION_TYPE.equals(result.getType()) || result.getId() == null) {
                continue;
            }
            DestinationTranslation content = localizedContent.get(result.getId());
            if (content == null) {
                continue;
            }
            if (hasText(content.getName())) {
                result.setTitle(content.getName());
            }
            if (hasText(content.getShortDescription())) {
                result.setSummary(content.getShortDescription());
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String toPlainSummary(String summary, SupportedLanguage language) {
        if (summary == null || summary.isBlank()) {
            return noPreviewText(language);
        }
        String plainText = Jsoup.parse(summary).text().replaceAll("\\s+", " ").strip();
        if (plainText.codePointCount(0, plainText.length()) <= MAX_SUMMARY_LENGTH) {
            return plainText;
        }
        int endIndex = plainText.offsetByCodePoints(0, MAX_SUMMARY_LENGTH);
        return plainText.substring(0, endIndex) + "…";
    }

    /** 미리보기가 없을 때 쓰는 문구도 메시지에서 읽는다. */
    private String noPreviewText(SupportedLanguage language) {
        Locale locale = language == null ? SupportedLanguage.KOREAN.getLocale() : language.getLocale();
        return messageSource.getMessage("search.result.noPreview", null, locale);
    }
}
