package com.tripbora.service.search;

import com.tripbora.config.i18n.SupportedLanguage;
import com.tripbora.dto.GlobalSearchPage;

public interface GlobalSearchService {

    /**
     * 통합검색.
     *
     * <p>여행지는 요청 언어의 번역명으로도 찾고, 결과에 보이는 여행지 이름·요약도 그 언어로 바꾼다.
     * 커뮤니티·코스·여행정보 등 사용자가 쓴 글은 원문 그대로 찾고 그대로 보여 준다.
     */
    GlobalSearchPage search(String query, String type, int page, SupportedLanguage requestedLanguage);
}
