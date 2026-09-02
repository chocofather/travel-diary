package com.example.travlediary.service.travelinfo;

import com.example.travlediary.dto.FestivalDetailDto;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.model.FestivalInfo;
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
