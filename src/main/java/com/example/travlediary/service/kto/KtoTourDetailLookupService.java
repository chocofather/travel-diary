package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoTourAutofillResponse;
import com.example.travlediary.dto.kto.KtoTourRegionMatchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * TourAPI 상세조회(detailCommon2 + detailIntro2)와 주소 → 지역 매칭을 한 번에 끝낸다.
 * 단건 자동입력(관리자 화면)과 지역별 일괄등록이 같은 결과를 쓰도록 이 한 곳만 거친다.
 */
@Service
@RequiredArgsConstructor
public class KtoTourDetailLookupService {

    private final KtoTourService ktoTourService;
    private final KtoTourRegionMatchService ktoTourRegionMatchService;

    public KtoTourAutofillResponse lookup(String contentId, String contentTypeId) {
        KtoTourAutofillResponse detail = ktoTourService.getDetail(contentId, contentTypeId);
        return detail.withRegionMatch(matchRegion(detail.address()));
    }

    /** 지역 매칭 실패가 상세조회 자체를 실패로 만들지 않는다. */
    private KtoTourRegionMatchResponse matchRegion(String address) {
        try {
            return ktoTourRegionMatchService.match(address);
        } catch (RuntimeException exception) {
            return KtoTourRegionMatchResponse.unmatched();
        }
    }
}
