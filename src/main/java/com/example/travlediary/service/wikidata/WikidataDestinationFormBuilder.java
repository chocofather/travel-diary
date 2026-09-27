package com.example.travlediary.service.wikidata;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.DestinationTranslationForm;
import com.example.travlediary.dto.wikidata.WikidataDestinationPreview;
import com.example.travlediary.dto.wikidata.WikipediaDescriptionPreview;
import com.example.travlediary.model.AccommodationInfo;
import com.example.travlediary.model.ActivityInfo;
import com.example.travlediary.model.AttractionInfo;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.model.RestaurantInfo;
import com.example.travlediary.model.ShopInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Wikidata·Wikipedia 미리보기로 등록폼({@link DestinationForm})을 만든다. 해외 일괄 등록이 쓴다.
 *
 * <p>규칙은 단건 등록 화면의 자동입력(admin-wikidata-form-apply.js, admin-wikipedia-description-apply.js)과 같다.
 * 여기서는 값을 옮겨 담기만 하고, 출처·라이선스·지역 재검증과 저장은 기존 등록 경로
 * ({@code DestinationSaveOrchestrationService} → {@link WikidataRegistrationService})가 그대로 맡는다.</p>
 *
 * <p>유형·시즌은 Wikidata 값으로 정하지 않는다. 관리자가 고른 값({@link Choices})이 없으면 비워 두고,
 * 지역도 자동 매핑이 확정되지 않으면 비워 둔다. 임의의 기본값으로 채우지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
public class WikidataDestinationFormBuilder {
    static final List<String> LANGUAGES = List.of("ko", "en", "ja", "zh-CN", "zh-TW");
    static final List<String> LANGUAGE_LABELS = List.of("한국어", "영어", "일본어", "중국어(간체)", "중국어(번체)");
    /** destination_translations.name·short_description 과 같은 한도(글자 수). */
    private static final int TEXT_LIMIT = 255;
    private static final int DESCRIPTION_MAX_BYTES = 65_535;
    private static final int HOMEPAGE_LIMIT = 255;
    /** 숙소·음식점 전화번호 칸은 32자, 나머지 유형은 255자다. */
    private static final int SHORT_CONTACT_LIMIT = 32;
    private static final int CONTACT_LIMIT = 255;
    /** 동음이의 구분용 괄호가 붙은 문서 제목은 여행지명으로 쓰지 않는다. */
    private static final Pattern DISAMBIGUATION = Pattern.compile("[（(][^（()）]*[)）]$");

    private final ObjectMapper objectMapper;

    /** 관리자가 검토 화면에서 정한 값. regionId 가 null 이면 자동 매핑된 지역을 쓴다. */
    public record Choices(DestinationType type, String season, Long regionId, String koreanName,
                          String photoFileName) {
    }

    /**
     * @param form               유형·시즌을 비워 둔 폼
     * @param autoRegionId       자동 매핑이 확정된 지역. 없으면 null(확인 필요)
     * @param wikipediaLanguages Wikipedia 상세 설명을 넣은 언어
     * @param notes              넣지 못한 값과 이유
     */
    public record Draft(DestinationForm form, Long autoRegionId, List<String> wikipediaLanguages,
                        List<String> notes) {
    }

    /** 검토용 초안. 관리자 선택값 없이 Wikidata·Wikipedia에서 확인되는 값만 채운다. */
    public Draft draft(WikidataDestinationPreview preview, WikipediaDescriptionPreview wikipedia) {
        DestinationForm form = new DestinationForm();
        form.setWikidataQid(preview.qid());
        List<String> wikipediaLanguages = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (int index = 0; index < LANGUAGES.size(); index++) {
            String language = LANGUAGES.get(index);
            DestinationTranslationForm slot = form.getTranslations().get(index);
            String name = limited(value(preview.names(), language));
            if (name == null && "zh-TW".equals(language)) name = traditionalTitle(wikipedia, preview);
            String shortDescription = limited(value(preview.shortDescriptions(), language));
            WikipediaDescriptionPreview.LanguageEntry entry = wikipediaEntry(wikipedia, language);
            // 한국어 이름은 관리자가 검토에서 채울 수 있다. 다른 언어는 이름 없이 설명만 저장할 수 없다.
            if (index > 0 && name == null) {
                if (shortDescription != null || entry != null) {
                    notes.add(LANGUAGE_LABELS.get(index) + " 제목이 없어 이 언어의 설명은 넣지 않았습니다.");
                }
                continue;
            }
            slot.setName(name == null ? "" : name);
            slot.setShortDescription(shortDescription == null ? "" : shortDescription);
            if (entry != null) {
                slot.setDescription(entry.description());
                form.getWikipediaRevisionIds().set(index, entry.revisionId());
                wikipediaLanguages.add(language);
            }
        }
        if (inRange(preview.latitude(), 90)) form.setLatitude(BigDecimal.valueOf(preview.latitude()));
        if (inRange(preview.longitude(), 180)) form.setLongitude(BigDecimal.valueOf(preview.longitude()));
        Long autoRegionId = matchedRegionId(preview.regionMatch());
        form.setRegionId(autoRegionId);
        return new Draft(form, autoRegionId, List.copyOf(wikipediaLanguages), List.copyOf(notes));
    }

    /** 등록용 폼. 초안에 관리자가 정한 유형·시즌·지역·한국어 이름·대표 사진을 더한다. */
    public DestinationForm form(WikidataDestinationPreview preview, WikipediaDescriptionPreview wikipedia,
                                Choices choices) {
        DestinationForm form = draft(preview, wikipedia).form();
        form.setType(choices.type());
        form.setSeason(choices.season());
        if (choices.regionId() != null) form.setRegionId(choices.regionId());
        String koreanName = limited(choices.koreanName());
        if (koreanName != null) form.getTranslations().get(0).setName(koreanName);
        if (choices.type() != null) applyTravelInfo(form, choices.type(), preview.travelInfo());
        if (choices.photoFileName() != null && !choices.photoFileName().isBlank()) {
            form.setCommonsSelectedPhotosJson(photoSelection(preview.qid(), choices.photoFileName().strip()));
        }
        return form;
    }

    /** 공식 웹사이트·전화번호를 고른 유형의 상세정보 칸에만 넣는다. 칸 길이를 넘으면 넣지 않는다. */
    private void applyTravelInfo(DestinationForm form, DestinationType type,
                                 WikidataDestinationPreview.TravelInfo travelInfo) {
        String homepage = fits(travelInfo == null ? null : travelInfo.homepageUrl(), HOMEPAGE_LIMIT);
        String rawContact = travelInfo == null ? null : travelInfo.contactNumber();
        switch (type) {
            case ATTRACTION -> {
                AttractionInfo info = new AttractionInfo();
                info.setHomepageUrl(homepage);
                info.setContactNumber(fits(rawContact, CONTACT_LIMIT));
                form.setAttractionInfo(info);
            }
            case ACCOMMODATION -> {
                AccommodationInfo info = new AccommodationInfo();
                info.setHomepageUrl(homepage);
                info.setContactNumber(fits(rawContact, SHORT_CONTACT_LIMIT));
                form.setAccommodationInfo(info);
            }
            case RESTAURANTS, CAFE -> {
                RestaurantInfo info = new RestaurantInfo();
                info.setHomepageUrl(homepage);
                info.setContactNumber(fits(rawContact, SHORT_CONTACT_LIMIT));
                form.setRestaurantInfo(info);
            }
            case ACTIVITY -> {
                ActivityInfo info = new ActivityInfo();
                info.setHomepageUrl(homepage);
                info.setContactNumber(fits(rawContact, CONTACT_LIMIT));
                form.setActivityInfo(info);
            }
            case SHOP -> {
                ShopInfo info = new ShopInfo();
                info.setHomepageUrl(homepage);
                info.setContactNumber(fits(rawContact, CONTACT_LIMIT));
                form.setShopInfo(info);
            }
        }
    }

    /** 등록폼과 같은 형식의 Commons 선택값. 파일명·대표 여부만 담고, 출처는 저장할 때 서버가 다시 조회한다. */
    private String photoSelection(String qid, String fileName) {
        try {
            return objectMapper.writeValueAsString(Map.of("qid", qid,
                    "photos", List.of(Map.of("fileName", fileName, "main", true))));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("사진 선택값을 만들지 못했습니다.", exception);
        }
    }

    /** 단건 화면과 같은 기준: 원문 언어·중국어 변형·판본·라이선스가 모두 확인된 설명만 쓴다. */
    private WikipediaDescriptionPreview.LanguageEntry wikipediaEntry(WikipediaDescriptionPreview wikipedia,
                                                                     String language) {
        if (wikipedia == null || wikipedia.languages() == null) return null;
        String sourceLanguage = language.startsWith("zh-") ? "zh" : language;
        String variant = "zh-CN".equals(language) ? "zh-cn" : "zh-TW".equals(language) ? "zh-tw" : null;
        return wikipedia.languages().stream()
                .filter(entry -> language.equals(entry.language()) && "AVAILABLE".equals(entry.status())
                        && sourceLanguage.equals(entry.sourceLanguage())
                        && java.util.Objects.equals(variant, entry.variant())
                        && entry.revisionId() != null && entry.revisionId() > 0
                        && hasText(entry.licenseName())
                        && entry.licenseUrl() != null && entry.licenseUrl().startsWith("https://creativecommons.org/")
                        && hasText(entry.description())
                        && entry.description().getBytes(StandardCharsets.UTF_8).length <= DESCRIPTION_MAX_BYTES)
                .findFirst().orElse(null);
    }

    /** Wikidata에 번체 제목이 없을 때만 Wikipedia가 번체로 변환해 준 문서 제목을 쓴다. */
    private String traditionalTitle(WikipediaDescriptionPreview wikipedia, WikidataDestinationPreview preview) {
        if (hasText(value(preview.names(), "zh-TW")) || wikipedia == null || wikipedia.languages() == null) {
            return null;
        }
        return wikipedia.languages().stream()
                .filter(entry -> "zh-TW".equals(entry.language()) && "AVAILABLE".equals(entry.status())
                        && "zh".equals(entry.sourceLanguage()) && "zh-tw".equals(entry.variant()))
                .map(entry -> entry.displayTitle() == null ? null : entry.displayTitle().strip())
                .filter(title -> hasText(title) && !DISAMBIGUATION.matcher(title).find())
                .map(this::limited)
                .findFirst().orElse(null);
    }

    /** 단건 화면과 같은 기준: 대륙·국가·도시 경로가 매칭 결과의 국가·지역과 맞을 때만 자동 선택한다. */
    private Long matchedRegionId(WikidataDestinationPreview.RegionMatch match) {
        if (match == null || !match.matched() || match.regionId() == null || match.path() == null
                || match.path().size() != 3) return null;
        for (var item : match.path()) {
            if (item.id() == null || item.id() <= 0 || !hasText(item.regionName())) return null;
        }
        return match.path().get(1).id().equals(match.countryId())
                && match.path().get(2).id().equals(match.regionId()) ? match.regionId() : null;
    }

    private String value(Map<String, String> values, String language) {
        return values == null ? null : values.get(language);
    }

    private String limited(String text) {
        if (!hasText(text)) return null;
        String value = text.strip();
        return value.codePointCount(0, value.length()) <= TEXT_LIMIT ? value : null;
    }

    private String fits(String text, int limit) {
        if (!hasText(text)) return null;
        String value = text.strip();
        return value.length() <= limit ? value : null;
    }

    private boolean inRange(Double value, double limit) {
        return value != null && Double.isFinite(value) && value >= -limit && value <= limit;
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
