package com.tripbora.service.destination;

import com.tripbora.dto.DestinationForm;
import com.tripbora.model.DestinationImage;
import com.tripbora.model.DestinationImageCommonsSource;
import com.tripbora.model.DestinationTranslation;
import com.tripbora.model.DestinationTranslationSource;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.repository.destination.DestinationTranslationSourceMapper;
import com.tripbora.service.kto.KtoPhotoImportPersistenceService;
import com.tripbora.service.kto.PreparedKtoPhoto;
import com.tripbora.service.wikidata.CommonsPhotoSelectionException;
import com.tripbora.service.wikidata.PreparedCommonsPhoto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DestinationSavePersistenceService {

    private final DestinationService destinationService;
    private final KtoPhotoImportPersistenceService ktoPhotoImportPersistenceService;
    @Autowired private DestinationMapper destinationMapper;
    @Autowired private DestinationTranslationSourceMapper translationSourceMapper;
    @Autowired private DestinationImageService destinationImageService;

    /**
     * 외부 재검증을 시작하기 전의 사전 확인. 이미 등록된 QID면 바로 거부한다.
     * 최종 판정은 저장 트랜잭션 안의 재확인과 UNIQUE 제약이 맡는다.
     */
    public void rejectRegisteredWikidata(String qid) {
        String normalized = qid == null ? "" : qid.strip().toUpperCase();
        if (normalized.matches("Q[1-9][0-9]{0,14}")
                && destinationMapper.countByExternalContentId(DestinationService.WIKIDATA_SOURCE_TYPE, normalized) > 0) {
            throw new DuplicateWikidataDestinationException(normalized);
        }
    }

    @Transactional
    public Long registerWikidataDestination(DestinationForm form, Long userId,
                                             Map<String, DestinationTranslationSource> sources) {
        return registerWikidataDestination(form, userId, sources, List.of());
    }

    /** 여행지·번역·Wikipedia 출처·Commons 이미지·이미지 출처를 한 트랜잭션으로 저장한다. */
    @Transactional
    public Long registerWikidataDestination(DestinationForm form, Long userId,
                                             Map<String, DestinationTranslationSource> sources,
                                             List<PreparedCommonsPhoto> commonsPhotos) {
        String qid = form.getWikidataQid();
        if (destinationMapper.countByExternalContentId(DestinationService.WIKIDATA_SOURCE_TYPE, qid) > 0) {
            throw new DuplicateWikidataDestinationException(qid);
        }
        Long destinationId = destinationService.registerDestination(form, userId,
                DestinationService.WIKIDATA_SOURCE_TYPE, qid);
        for (Map.Entry<String, DestinationTranslationSource> entry : sources.entrySet()) {
            DestinationTranslation translation = destinationMapper.findTranslationByDestinationAndLanguage(
                    destinationId, entry.getKey());
            if (translation == null || translation.getId() == null) {
                throw new IllegalStateException("Wikipedia 번역 저장 결과를 확인하지 못했습니다.");
            }
            DestinationTranslationSource source = entry.getValue();
            source.setDestinationTranslationId(translation.getId());
            translationSourceMapper.insert(source);
        }
        persistCommonsPhotos(destinationId, commonsPhotos);
        return destinationId;
    }

    /**
     * 기존 여행지에 Commons 사진과 출처를 한 트랜잭션으로 더한다. 기존 사진과 그 출처는 바꾸지 않는다.
     * 여행지 행을 먼저 잠가 같은 여행지에 동시에 추가하는 요청이 중복 검사를 함께 통과하지 못하게 한다.
     * 한 장이라도 실패하면 전체를 되돌린다(파일 정리는 호출한 쪽이 맡는다).
     */
    @Transactional
    public void addCommonsPhotosToExistingDestination(Long destinationId, List<PreparedCommonsPhoto> photos) {
        if (!destinationId.equals(destinationMapper.lockDestinationForImageUpdate(destinationId))) {
            throw new IllegalArgumentException("여행지를 찾을 수 없습니다.");
        }
        persistCommonsPhotos(destinationId, photos);
    }

    private void persistCommonsPhotos(Long destinationId, List<PreparedCommonsPhoto> photos) {
        if (photos == null || photos.isEmpty()) return;
        Set<String> fileTitles = new HashSet<>();
        List<DestinationImage> images = new ArrayList<>();
        for (PreparedCommonsPhoto photo : photos) {
            String fileTitle = photo.source().getCommonsFileTitle();
            if (!fileTitles.add(fileTitle)
                    || destinationMapper.countCommonsImageSource(destinationId, fileTitle) > 0) {
                throw new CommonsPhotoSelectionException(
                        "이미 이 여행지에 등록된 Commons 사진입니다: " + photo.source().getSourceTitle());
            }
            DestinationImage image = new DestinationImage();
            image.setImageUrl(photo.localImageUrl());
            image.setSourceType(DestinationImage.COMMONS_SOURCE_TYPE);
            image.setIsMain(photo.main());
            image.setIsSlide(false);
            images.add(image);
        }
        destinationImageService.saveImages(destinationId, images);
        for (int index = 0; index < photos.size(); index++) {
            Long imageId = images.get(index).getId();
            if (imageId == null) {
                throw new IllegalStateException("저장된 Commons 이미지 ID를 확인할 수 없습니다.");
            }
            DestinationImageCommonsSource source = photos.get(index).source();
            source.setDestinationImageId(imageId);
            destinationMapper.insertCommonsImageSource(source);
        }
    }

    @Transactional
    public void registerDestination(DestinationForm form,
                                    Long userId,
                                    List<PreparedKtoPhoto> preparedPhotos) {
        registerDestination(form, userId, null, preparedPhotos);
    }

    /**
     * TourAPI 여행지 저장. 후보 조회 때 이미 한 번 걸렀더라도 같은 트랜잭션 안에서 한 번 더 확인해,
     * 재클릭이나 동시 작업으로 같은 contentId 가 두 번 저장되지 않게 한다.
     *
     * @param externalContentId null 이면 관리자 직접 등록과 똑같이 저장한다.
     * @return 저장된 여행지 번호
     */
    @Transactional
    public Long registerDestination(DestinationForm form,
                                    Long userId,
                                    String externalContentId,
                                    List<PreparedKtoPhoto> preparedPhotos) {
        if (externalContentId != null
                && destinationService.existsTourApiDestination(externalContentId)) {
            throw new DuplicateTourApiDestinationException(externalContentId);
        }
        Long destinationId = destinationService.registerDestination(form, userId, externalContentId);
        ktoPhotoImportPersistenceService.persistPhotos(destinationId, preparedPhotos);
        return destinationId;
    }

    @Transactional
    public void updateDestination(Long destinationId,
                                  DestinationForm form,
                                  List<PreparedKtoPhoto> preparedPhotos) {
        destinationService.updateDestination(destinationId, form);
        ktoPhotoImportPersistenceService.persistPhotos(destinationId, preparedPhotos);
    }
}
