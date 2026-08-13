package com.example.travlediary.service.search;

import com.example.travlediary.dto.GlobalSearchPage;
import com.example.travlediary.dto.GlobalSearchResultDto;
import com.example.travlediary.repository.search.GlobalSearchMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GlobalSearchServiceImpl implements GlobalSearchService {

    static final int PAGE_SIZE = 10;
    static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_SUMMARY_LENGTH = 180;

    private final GlobalSearchMapper globalSearchMapper;

    @Override
    public GlobalSearchPage search(String query, String type, int page) {
        String normalizedQuery = normalizeQuery(query);
        GlobalSearchType normalizedType = GlobalSearchType.from(type);
        if (normalizedQuery == null) {
            return emptyPage(normalizedType.getQueryValue());
        }

        String keywordPattern = toLikeLiteral(normalizedQuery);
        long totalCount = globalSearchMapper.count(keywordPattern, normalizedType.getQueryValue());
        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / PAGE_SIZE);
        int currentPage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
        long offset = (long) (currentPage - 1) * PAGE_SIZE;
        List<GlobalSearchResultDto> results = totalCount == 0
                ? List.of()
                : globalSearchMapper.search(
                        keywordPattern, normalizedType.getQueryValue(), offset, PAGE_SIZE);
        results.forEach(result -> result.setSummary(toPlainSummary(result.getSummary())));

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

    private String toPlainSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return "내용 미리보기가 없습니다.";
        }
        String plainText = Jsoup.parse(summary).text().replaceAll("\\s+", " ").strip();
        if (plainText.codePointCount(0, plainText.length()) <= MAX_SUMMARY_LENGTH) {
            return plainText;
        }
        int endIndex = plainText.offsetByCodePoints(0, MAX_SUMMARY_LENGTH);
        return plainText.substring(0, endIndex) + "…";
    }
}
