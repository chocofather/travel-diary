package com.example.travlediary.service.travelinfo;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.FestivalDetailDto;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.FestivalInfoTranslation;
import com.example.travlediary.model.InfoImage;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FestivalDetailService {

    private final TravelInfoService travelInfoService;
    private final TravelInfoMapper travelInfoMapper;
    private final FestivalInfoMapper festivalInfoMapper;
    private final FestivalInfoLocalizationService festivalInfoLocalizationService;

    @Transactional(readOnly = true)
    public boolean isPublicFestival(Long id) {
        if (id == null) {
            return false;
        }
        TravelInfoDetailDto detail = travelInfoMapper.findPublicDetailById(id);
        return detail != null && detail.getContentType() == TravelInfoContentType.FESTIVAL;
    }

    @Transactional
    public FestivalDetailDto getPublicDetail(Long id) {
        if (!isPublicFestival(id)) {
            throw notFound();
        }

        TravelInfoDetailDto travelInfo = travelInfoService.getPublicDetail(id);
        if (travelInfo.getContentType() != TravelInfoContentType.FESTIVAL) {
            throw notFound();
        }
        FestivalInfo festivalInfo = festivalInfoMapper.findByInfoId(id);
        List<InfoImage> images = prioritizeGalleryImages(travelInfoMapper.findImagesByInfoId(id));
        return new FestivalDetailDto(travelInfo, festivalInfo, images);
    }

    /**
     * 행사 상세정보를 요청 언어로 바꿔 둔다.
     *
     * <p>장소·주소·행사시간·이용요금·주최·주관 여섯 개만 언어별로 바뀌고, 연락처·홈페이지·
     * 기간·이미지와 TourAPI 식별자는 원문 그대로 둔다. 값은 필드마다 따로 대체되므로
     * 서로 다른 언어에서 올 수 있고, 번역이 비면 원문까지 내려간다.
     *
     * <p>여기서 값을 담는 {@link FestivalInfo} 는 이 공개 요청이 방금 읽어 온 표시용 객체다.
     * 관리자 화면은 자기 조회로 원문을 따로 읽으므로 영향이 없다.
     */
    @Transactional(readOnly = true)
    public void localizePublicDetail(FestivalDetailDto festival,
                                     SupportedLanguage requestedLanguage) {
        if (festival == null) {
            return;
        }
        FestivalInfo festivalInfo = festival.getFestivalInfo();
        FestivalInfoTranslation display = festivalInfoLocalizationService
                .resolveLocalizedInfo(festivalInfo, requestedLanguage);
        if (display == null) {
            return;
        }

        festivalInfo.setEventPlace(display.getEventPlace());
        festivalInfo.setAddress(display.getAddress());
        festivalInfo.setPlayTime(display.getPlayTime());
        festivalInfo.setUseTime(display.getUseTime());
        festivalInfo.setSponsor1(display.getSponsor1());
        festivalInfo.setSponsor2(display.getSponsor2());
    }

    private List<InfoImage> prioritizeGalleryImages(List<InfoImage> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
                .sorted(Comparator.comparingInt(this::galleryPriority)
                        .thenComparing(InfoImage::getOrderIndex,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private int galleryPriority(InfoImage image) {
        if (Boolean.TRUE.equals(image.getIsThumbnail())) {
            return 0;
        }
        if (Boolean.TRUE.equals(image.getIsMain())) {
            return 1;
        }
        return 2;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "축제·행사 정보를 찾을 수 없습니다.");
    }
}
