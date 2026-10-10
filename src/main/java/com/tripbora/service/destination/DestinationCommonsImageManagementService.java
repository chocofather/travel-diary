package com.tripbora.service.destination;

import com.tripbora.model.CountryCategory;
import com.tripbora.model.Destination;
import com.tripbora.model.DestinationImage;
import com.tripbora.model.DestinationTranslation;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.service.category.CountryCategoryService;
import com.tripbora.service.wikidata.CommonsPhotoImportService;
import com.tripbora.service.wikidata.CommonsPhotoSelectionException;
import com.tripbora.service.wikidata.PreparedCommonsPhoto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이미 등록된 해외 여행지에 Wikimedia Commons 사진을 더한다.
 * Wikidata 여행지는 QID 후보(P18·Commons 카테고리)에서, QID가 없거나 후보가 없으면 관리자가 검색한 결과에서 고른다.
 * 후보·라이선스 재검증과 다운로드는 최초 등록(7-B)과 같은 코드를 쓰고, 저장은 한 트랜잭션으로 한다.
 */
@Service
@RequiredArgsConstructor
public class DestinationCommonsImageManagementService {

    private final CommonsPhotoImportService commonsPhotoImportService;
    private final DestinationSavePersistenceService persistenceService;
    private final DestinationImageService destinationImageService;
    private final DestinationMapper destinationMapper;
    private final CountryCategoryService countryCategoryService;

    /** 이미지 관리 화면에서 Commons 추가를 보여줄 여행지의 QID. Wikidata 여행지가 아니면 null. */
    public String findWikidataQid(Long destinationId) {
        String qid = destinationMapper.findExternalContentIdBySourceType(
                destinationId, DestinationService.WIKIDATA_SOURCE_TYPE);
        return qid != null && qid.matches("Q[1-9][0-9]{0,14}") ? qid : null;
    }

    /**
     * 해외 여행지인지. 지역의 최상위 루트가 국내 루트가 아니면 해외다(공개 목록의 domestic/overseas 판별과 같은 규칙).
     * 지역을 확인하지 못하면 국내로 보고 기존 화면(한국관광공사 관광사진)을 그대로 둔다.
     */
    public boolean isOverseasDestination(Long destinationId) {
        List<CountryCategory> path = regionPath(destinationId);
        return !path.isEmpty() && !countryCategoryService.getDomesticRootIds().contains(path.get(0).getId());
    }

    /**
     * 수동 검색 기본값: 여행지 이름 + 도시 + 국가. Commons 파일 설명은 대부분 영어라 영문 이름을 먼저 쓰고,
     * 없으면 한국어 이름을 쓴다. 이름에 이미 들어 있는 도시·국가 이름은 다시 붙이지 않는다.
     */
    public String defaultSearchQuery(Long destinationId) {
        List<DestinationTranslation> translations = destinationMapper.findTranslationsByDestinationId(destinationId);
        String name = translationName(translations, "en");
        boolean english = name != null;
        if (name == null) name = translationName(translations, "ko");
        List<String> parts = new ArrayList<>();
        if (name != null) parts.add(name);
        List<CountryCategory> path = regionPath(destinationId);
        // depth 3 도시, depth 2 국가 순으로 붙인다.
        for (int depth : new int[]{3, 2}) {
            path.stream().filter(region -> region.getDepth() != null && region.getDepth() == depth)
                    .findFirst()
                    .map(region -> english && region.getNameEn() != null && !region.getNameEn().isBlank()
                            ? region.getNameEn() : region.getRegionName())
                    .map(String::strip)
                    .filter(regionName -> !regionName.isEmpty() && parts.stream().noneMatch(part ->
                            part.toLowerCase(Locale.ROOT).contains(regionName.toLowerCase(Locale.ROOT))))
                    .ifPresent(parts::add);
        }
        String query = String.join(" ", parts).strip();
        return query.length() > 200 ? query.substring(0, 200).strip() : query;
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
        boolean searchSelection = commonsPhotoImportService.isSearchSelection(selectionJson);
        String qid = findWikidataQid(destinationId);
        List<CommonsPhotoImportService.Selection> selections;
        if (searchSelection) {
            // 검색 추가는 해외 여행지(또는 이미 Commons 후보를 쓰는 Wikidata 여행지)에서만 받는다. 국내는 관광공사 사진을 쓴다.
            if (qid == null && !isOverseasDestination(destinationId)) {
                throw new CommonsPhotoSelectionException("해외 여행지에서만 Commons 사진을 추가할 수 있습니다.");
            }
            selections = commonsPhotoImportService.parseSearchSelections(selectionJson);
        } else {
            if (qid == null) {
                throw new CommonsPhotoSelectionException(
                        "Wikidata QID가 연결된 해외 여행지에서만 Commons 사진 후보를 추가할 수 있습니다. "
                                + "QID가 없으면 Commons 검색으로 사진을 찾아 주세요.");
            }
            selections = commonsPhotoImportService.parseSelections(selectionJson, qid);
        }
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
        List<PreparedCommonsPhoto> prepared = searchSelection
                ? commonsPhotoImportService.prepareSearchResultsForExistingDestination(selections, hasMain)
                : commonsPhotoImportService.prepareForExistingDestination(qid, selections, hasMain);
        try {
            persistenceService.addCommonsPhotosToExistingDestination(destinationId, prepared);
        } catch (RuntimeException exception) {
            commonsPhotoImportService.cleanup(prepared);
            throw exception;
        }
        return prepared.size();
    }

    private List<CountryCategory> regionPath(Long destinationId) {
        Destination destination = destinationMapper.findById(destinationId);
        if (destination == null || destination.getRegionId() == null) return List.of();
        return countryCategoryService.getRegionPath(destination.getRegionId());
    }

    private String translationName(List<DestinationTranslation> translations, String languageCode) {
        if (translations == null) return null;
        return translations.stream()
                .filter(translation -> languageCode.equals(translation.getLanguageCode()))
                .map(DestinationTranslation::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::strip)
                .findFirst()
                .orElse(null);
    }
}
