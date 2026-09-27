package com.example.travlediary.service.destination;

import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.service.wikidata.CommonsPhotoImportService;
import com.example.travlediary.service.wikidata.CommonsPhotoSelectionException;
import com.example.travlediary.service.wikidata.PreparedCommonsPhoto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이미 등록된 Wikidata 여행지에 Wikimedia Commons 사진을 더한다.
 * 후보·라이선스 재검증과 다운로드는 최초 등록(7-B)과 같은 코드를 쓰고, 저장은 한 트랜잭션으로 한다.
 */
@Service
@RequiredArgsConstructor
public class DestinationCommonsImageManagementService {

    private final CommonsPhotoImportService commonsPhotoImportService;
    private final DestinationSavePersistenceService persistenceService;
    private final DestinationImageService destinationImageService;
    private final DestinationMapper destinationMapper;

    /** 이미지 관리 화면에서 Commons 추가를 보여줄 여행지의 QID. Wikidata 여행지가 아니면 null. */
    public String findWikidataQid(Long destinationId) {
        String qid = destinationMapper.findExternalContentIdBySourceType(
                destinationId, DestinationService.WIKIDATA_SOURCE_TYPE);
        return qid != null && qid.matches("Q[1-9][0-9]{0,14}") ? qid : null;
    }

    /** 이미 이 여행지에 있는 Commons 파일명('File:' 제외). 화면에서 중복 선택을 막는 데 쓴다. */
    public List<String> registeredCommonsFileNames(List<DestinationImage> images) {
        return images.stream()
                .map(DestinationImage::getCommonsFileTitle)
                .filter(Objects::nonNull)
                .map(title -> title.startsWith("File:") ? title.substring(5) : title)
                .toList();
    }

    /** @return 추가한 사진 수 */
    public int addPhotos(Long destinationId, String selectionJson) {
        String qid = findWikidataQid(destinationId);
        if (qid == null) {
            throw new CommonsPhotoSelectionException(
                    "Wikidata QID가 연결된 해외 여행지에서만 Commons 사진을 추가할 수 있습니다.");
        }
        List<CommonsPhotoImportService.Selection> selections =
                commonsPhotoImportService.parseSelections(selectionJson, qid);
        if (selections.isEmpty()) {
            throw new CommonsPhotoSelectionException("추가할 Commons 사진을 선택해 주세요.");
        }

        // 이미 있는 사진은 외부 재검증·다운로드 전에 돌려보낸다. 최종 판정은 저장 트랜잭션 안에서 다시 한다.
        List<DestinationImage> images = destinationImageService.getImages(destinationId);
        Set<String> registered = images.stream()
                .map(DestinationImage::getCommonsFileTitle)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<String> duplicates = selections.stream()
                .map(CommonsPhotoImportService.Selection::fileName)
                .filter(fileName -> registered.contains(CommonsPhotoImportService.commonsFileTitle(fileName)))
                .toList();
        if (!duplicates.isEmpty()) {
            throw new CommonsPhotoSelectionException(
                    "이미 이 여행지에 등록된 Commons 사진입니다: " + String.join(", ", duplicates));
        }

        boolean hasMain = images.stream().anyMatch(image -> Boolean.TRUE.equals(image.getIsMain()));
        List<PreparedCommonsPhoto> prepared =
                commonsPhotoImportService.prepareForExistingDestination(qid, selections, hasMain);
        try {
            persistenceService.addCommonsPhotosToExistingDestination(destinationId, prepared);
        } catch (RuntimeException exception) {
            commonsPhotoImportService.cleanup(prepared);
            throw exception;
        }
        return prepared.size();
    }
}
