package com.example.travlediary.service.bookmark;

import com.example.travlediary.dto.MyPageBookmarkPageDto;
import com.example.travlediary.repository.bookmark.MyPageBookmarkMapper;
import com.example.travlediary.service.category.CountryCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MyPageBookmarkService {

    public static final int PAGE_SIZE = 10;

    private static final String DEFAULT_SECTION = "destination";
    private static final Set<String> SECTIONS =
            Set.of(DEFAULT_SECTION, "community", "travel-info");
    private static final Set<String> SCOPES =
            Set.of("all", "domestic", "international");
    private static final Set<String> COMMUNITY_TYPES =
            Set.of("all", "question", "tip", "course");

    private final MyPageBookmarkMapper myPageBookmarkMapper;
    private final CountryCategoryService countryCategoryService;

    @Transactional(readOnly = true)
    public MyPageBookmarkPageDto getBookmarks(Long userId, String section,
                                               String scope, String type, int page) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 정보를 확인할 수 없습니다.");
        }

        String safeSection = normalize(section, SECTIONS, DEFAULT_SECTION);
        String safeScope = normalize(scope, SCOPES, "all");
        String safeType = normalize(type, COMMUNITY_TYPES, "all");
        int safePage = Math.max(page, 1);
        long offset = (long) (safePage - 1) * PAGE_SIZE;

        return switch (safeSection) {
            case "community" -> communityPage(
                    userId, safeType, safePage, offset);
            case "travel-info" -> travelInfoPage(
                    userId, safeScope, safePage, offset);
            default -> destinationPage(
                    userId, safeScope, safePage, offset);
        };
    }

    private MyPageBookmarkPageDto destinationPage(Long userId, String scope,
                                                    int page, long offset) {
        Long koreaRootId = null;
        if (!"all".equals(scope)) {
            koreaRootId = countryCategoryService.getKoreaRootId();
            if (koreaRootId == null) {
                throw new IllegalStateException("대한민국 지역 기준을 확인할 수 없습니다.");
            }
        }

        int totalCount = myPageBookmarkMapper.countDestinationBookmarks(
                userId, scope, koreaRootId);
        List<?> bookmarks = totalCount == 0
                ? List.of()
                : myPageBookmarkMapper.findDestinationBookmarks(
                        userId, scope, koreaRootId, offset, PAGE_SIZE);
        return page(bookmarks, "destination", scope, "all", page, totalCount);
    }

    private MyPageBookmarkPageDto communityPage(Long userId, String type,
                                                  int page, long offset) {
        int totalCount = myPageBookmarkMapper.countCommunityBookmarks(userId, type);
        List<?> bookmarks = totalCount == 0
                ? List.of()
                : myPageBookmarkMapper.findCommunityBookmarks(
                        userId, type, offset, PAGE_SIZE);
        return page(bookmarks, "community", "all", type, page, totalCount);
    }

    private MyPageBookmarkPageDto travelInfoPage(Long userId, String scope,
                                                   int page, long offset) {
        String mapperScope = switch (scope) {
            case "domestic" -> "DOMESTIC";
            case "international" -> "INTERNATIONAL";
            default -> null;
        };
        int totalCount = myPageBookmarkMapper.countTravelInfoBookmarks(
                userId, mapperScope);
        List<?> bookmarks = totalCount == 0
                ? List.of()
                : myPageBookmarkMapper.findTravelInfoBookmarks(
                        userId, mapperScope, offset, PAGE_SIZE);
        return page(bookmarks, "travel-info", scope, "all", page, totalCount);
    }

    private MyPageBookmarkPageDto page(List<?> bookmarks, String section,
                                        String scope, String type,
                                        int currentPage, int totalCount) {
        int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
        return new MyPageBookmarkPageDto(
                bookmarks, section, scope, type, currentPage, totalPages, totalCount);
    }

    private String normalize(String value, Set<String> allowed, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }
}
