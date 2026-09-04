package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoForeignTourDetailResponse;
import com.example.travlediary.dto.kto.KtoForeignTourMatchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 관리자 영문 자동입력이 쓰는 진입점.
 *
 * <p>호출 규칙은 다른 언어와 같아 {@link KtoForeignTourService} 에 그대로 맡기고,
 * 여기서는 언어만 영어로 고정한다.
 */
@Service
@RequiredArgsConstructor
public class KtoEnglishTourService {

    private final KtoForeignTourService ktoForeignTourService;

    public KtoForeignTourMatchResponse match(String koreanTitle, String mapX, String mapY) {
        return ktoForeignTourService.match(
                KtoForeignLanguage.ENGLISH, koreanTitle, mapX, mapY);
    }

    public KtoForeignTourDetailResponse getDetail(String contentId, String contentTypeId) {
        return ktoForeignTourService.getDetail(
                KtoForeignLanguage.ENGLISH, contentId, contentTypeId);
    }
}
