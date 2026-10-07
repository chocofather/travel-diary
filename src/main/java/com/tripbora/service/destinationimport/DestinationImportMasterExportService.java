package com.tripbora.service.destinationimport;

import com.tripbora.model.Category;
import com.tripbora.model.CountryCategory;
import com.tripbora.model.DestinationSeason;
import com.tripbora.model.DestinationType;
import com.tripbora.service.category.CountryCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 가 JSON 일괄등록 파일을 정확히 만들 수 있도록 현재 등록 기준 데이터를 읽기 전용으로 내보낸다.
 *
 * <p>DB 번호(ID)는 계약값이 아니므로 넣지 않는다. 지역은 이름 경로, 카테고리는 이름, 편의시설은 code 로 내보낸다.
 * 지역·카테고리·편의시설 목록은 Master Resolver 가 매핑에 쓰는 것과 같은 기준(숨긴 지역 제외, 음식점·카페 합집합)이고,
 * 필드 규격은 Validator 와 같은 {@link DestinationImportFields} 에서 가져온다.</p>
 */
@Service
@RequiredArgsConstructor
public class DestinationImportMasterExportService {

    public static final String FILE_NAME = "destination-import-master.json";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final DestinationImportMasterResolver resolver;
    private final CountryCategoryService countryCategoryService;

    public record RegionNode(String name, String nameEn, List<RegionNode> children) {
    }

    public record AmenityEntry(String code, String name) {
    }

    public record InfoFieldEntry(String name, String kind, Integer maxLength, Integer maxBytes,
                                 boolean translatable, boolean fact) {
    }

    public record Contract(int version,
                           int maxDestinations,
                           int maxBytes,
                           List<String> types,
                           List<String> seasons,
                           List<String> languages,
                           List<String> infoTranslationLanguages,
                           Map<String, Object> limits,
                           Map<String, String> rules,
                           Map<String, List<InfoFieldEntry>> infoFields) {
    }

    /**
     * @param regions 국내·해외 지역 계층. domestic 은 국가 → 시·도(city) → 시·군·구(district),
     *                overseas 는 대륙 → 국가(country) → 도시·지역(city) 순서다
     */
    public record MasterExport(String schema,
                               int version,
                               String generatedAt,
                               List<String> notes,
                               Contract contract,
                               Map<String, List<RegionNode>> regions,
                               Map<String, List<String>> categoriesByType,
                               Map<String, List<AmenityEntry>> amenitiesByType) {
    }

    public MasterExport export() {
        DestinationImportMasterResolver.MasterData master = resolver.load();
        Map<String, List<RegionNode>> regions = new LinkedHashMap<>();
        regions.put("domestic", master.domesticRoots().stream().map(root -> node(root, master)).toList());
        regions.put("overseas", master.overseasRoots().stream().map(root -> node(root, master)).toList());

        Map<String, List<String>> categories = new LinkedHashMap<>();
        Map<String, List<AmenityEntry>> amenities = new LinkedHashMap<>();
        for (DestinationType type : DestinationType.values()) {
            categories.put(type.name(), master.categories(type).stream().map(Category::getName).toList());
            amenities.put(type.name(), master.amenities(type).stream()
                    .map(amenity -> new AmenityEntry(amenity.code(), amenity.name())).toList());
        }
        return new MasterExport("tripbora-destination-import-master", DestinationImportFields.VERSION,
                OffsetDateTime.now(SEOUL).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                List.of(
                        "이 파일의 지역 이름·카테고리 이름·편의시설 code 를 글자 그대로 사용한다. DB 번호(ID)는 쓰지 않는다.",
                        "region.country 가 '대한민국'이면 국내, 그 밖에는 해외다. 해외는 대륙을 적지 않고 국가부터 적는다.",
                        "하위 지역(children)이 있는 단계는 반드시 그 아래까지 지정한다. children 이 비어 있으면 거기서 멈춘다.",
                        "fact=true 인 값(운영시간·요금·연락처·좌표·외부 ID 등)은 실제 출처에서 확인한 경우에만 넣고 evidence 에 출처를 적는다. 모르면 null 이다.",
                        "이미지 정보는 넣지 않는다. 등록 후 관리자 화면에서 직접 추가한다."),
                contract(), regions, categories, amenities);
    }

    private RegionNode node(CountryCategory region, DestinationImportMasterResolver.MasterData master) {
        List<RegionNode> children = master.children(region.getId(), countryCategoryService).stream()
                .map(child -> node(child, master)).toList();
        return new RegionNode(region.getRegionName(), region.getNameEn(), children);
    }

    private Contract contract() {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("keyMaxLength", DestinationImportFields.KEY_MAX_LENGTH);
        limits.put("nameMaxLength", DestinationImportFields.NAME_MAX_LENGTH);
        limits.put("shortDescriptionMaxLength", DestinationImportFields.SHORT_DESCRIPTION_MAX_LENGTH);
        limits.put("descriptionMaxBytes", DestinationImportFields.TEXT_MAX_BYTES);
        limits.put("tourApiContentId", "숫자 1~30자리 문자열");
        limits.put("wikidataQid", "Q 뒤에 숫자 (예: Q12345)");
        limits.put("googlePlaceId", "영문·숫자·_·- 16~" + DestinationImportFields.GOOGLE_PLACE_ID_MAX_LENGTH + "자");
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("domestic", "tourApiContentId 만 허용. wikidataQid·googlePlaceId 는 넣으면 오류");
        rules.put("overseas", "wikidataQid·googlePlaceId 허용. tourApiContentId 는 넣으면 오류");
        rules.put("categories", "1개 이상 필수. 그 type 의 categoriesByType 목록에 있는 이름만");
        rules.put("mainCategory", "선택. categories 중 하나. 비우면 기존 규칙으로 대표를 고른다");
        rules.put("amenities", "선택. 그 type 의 amenitiesByType 목록에 있는 code 만");
        rules.put("unknownFields", "계약에 없는 필드는 오류");
        rules.put("emptyString", "빈 문자열은 null 로 본다");
        Map<String, List<InfoFieldEntry>> infoFields = new LinkedHashMap<>();
        for (DestinationType type : DestinationType.values()) {
            infoFields.put(type.name(), DestinationImportFields.infoFields(type).stream()
                    .map(field -> new InfoFieldEntry(field.name(), field.kind().name(),
                            field.kind() == DestinationImportFields.Kind.LONG_TEXT || field.maxLength() == 0
                                    ? null : field.maxLength(),
                            field.kind() == DestinationImportFields.Kind.LONG_TEXT ? field.maxLength() : null,
                            field.translatable(), field.fact()))
                    .toList());
        }
        return new Contract(DestinationImportFields.VERSION, DestinationImportFields.MAX_DESTINATIONS,
                DestinationImportFields.MAX_BYTES,
                Arrays.stream(DestinationType.values()).map(Enum::name).toList(),
                Arrays.stream(DestinationSeason.values()).map(Enum::name).toList(),
                DestinationImportFields.LANGUAGES, DestinationImportFields.INFO_TRANSLATION_LANGUAGES,
                limits, rules, infoFields);
    }
}
