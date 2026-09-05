package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoForeignTourDetailResponse;
import com.example.travlediary.dto.kto.KtoForeignTourMatchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 영문 자동입력 진입점은 공통 외국어 서비스에 영어로만 위임한다.
 *
 * <p>호출 규칙 자체는 {@link KtoForeignTourServiceTest} 가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class KtoEnglishTourServiceTest {

    @Mock private KtoForeignTourService ktoForeignTourService;
    @InjectMocks private KtoEnglishTourService ktoEnglishTourService;

    @Test
    void matchAsksTheCommonServiceForEnglish() {
        KtoForeignTourMatchResponse expected = KtoForeignTourMatchResponse.noMatch();
        when(ktoForeignTourService.match(
                KtoForeignLanguage.ENGLISH, "경복궁", "126.991", "37.579")).thenReturn(expected);

        assertThat(ktoEnglishTourService.match("경복궁", "126.991", "37.579")).isSameAs(expected);
        verify(ktoForeignTourService).match(
                KtoForeignLanguage.ENGLISH, "경복궁", "126.991", "37.579");
    }

    @Test
    void detailKeepsTheMatchedContentTypeIdAndAsksForEnglish() {
        KtoForeignTourDetailResponse expected = new KtoForeignTourDetailResponse(
                "Changdeokgung Palace", "overview", null, null, null, null, null, null,
                null, null, null, null, null, null);
        when(ktoForeignTourService.getDetail(KtoForeignLanguage.ENGLISH, "eng-1", "82"))
                .thenReturn(expected);

        assertThat(ktoEnglishTourService.getDetail("eng-1", "82")).isSameAs(expected);
        // 유형 코드는 매칭 결과를 그대로 넘긴다 (국문 코드로 바꾸지 않는다)
        verify(ktoForeignTourService).getDetail(KtoForeignLanguage.ENGLISH, "eng-1", "82");
    }
}
