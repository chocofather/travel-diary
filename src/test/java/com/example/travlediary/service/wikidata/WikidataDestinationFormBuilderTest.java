package com.example.travlediary.service.wikidata;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.wikidata.WikidataDestinationPreview;
import com.example.travlediary.dto.wikidata.WikidataDestinationPreview.RegionMatch;
import com.example.travlediary.dto.wikidata.WikidataDestinationPreview.RegionMatch.RegionPathItem;
import com.example.travlediary.dto.wikidata.WikipediaDescriptionPreview;
import com.example.travlediary.dto.wikidata.WikipediaDescriptionPreview.LanguageEntry;
import com.example.travlediary.model.DestinationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WikidataDestinationFormBuilderTest {
    private final WikidataDestinationFormBuilder builder = new WikidataDestinationFormBuilder(new ObjectMapper());

    @Test
    void draftFillsOnlyVerifiedValuesAndLeavesTypeSeasonForTheAdmin() {
        var draft = builder.draft(preview(matchedRegion(), Map.of("ko", "에펠탑", "en", "Eiffel Tower", "ja", "エッフェル塔")),
                wikipedia(
                        entry("ko", "ko", null, "에펠탑 설명", 11L, "https://creativecommons.org/licenses/by-sa/4.0/"),
                        // 판본이 없으면 출처를 확인할 수 없어 넣지 않는다.
                        entry("en", "en", null, "Eiffel description", null, "https://creativecommons.org/licenses/by-sa/4.0/"),
                        // 번체 칸은 중국어 문서를 번체로 변환한 것만 쓴다.
                        entry("zh-TW", "zh", "zh-tw", "艾菲爾鐵塔介紹", 33L, "https://creativecommons.org/licenses/by-sa/4.0/")
                                .withDisplayTitle("艾菲爾鐵塔")));
        DestinationForm form = draft.form();

        assertThat(form.getWikidataQid()).isEqualTo("Q243");
        assertThat(form.getType()).isNull();
        assertThat(form.getSeason()).isNull();
        assertThat(form.getTranslations()).extracting("languageCode").containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(form.getTranslations().get(0).getName()).isEqualTo("에펠탑");
        assertThat(form.getTranslations().get(0).getDescription()).isEqualTo("에펠탑 설명");
        assertThat(form.getTranslations().get(1).getDescription()).isEmpty();
        // Wikidata에 번체 제목이 없으면 Wikipedia 번체 변환 제목을 쓴다.
        assertThat(form.getTranslations().get(4).getName()).isEqualTo("艾菲爾鐵塔");
        assertThat(form.getTranslations().get(4).getDescription()).isEqualTo("艾菲爾鐵塔介紹");
        assertThat(form.getWikipediaRevisionIds()).containsExactly(11L, null, null, null, 33L);
        assertThat(draft.wikipediaLanguages()).containsExactly("ko", "zh-TW");
        assertThat(form.getLatitude()).isEqualByComparingTo(new BigDecimal("48.8583"));
        assertThat(draft.autoRegionId()).isEqualTo(300L);
        assertThat(form.getRegionId()).isEqualTo(300L);
    }

    @Test
    void languagesWithoutTitleDropTheirDescriptionsAndUnconfirmedRegionIsLeftEmpty() {
        var unmatched = new RegionMatch(200L, null, false, "도시를 확인하지 못했습니다.", List.of());
        var draft = builder.draft(preview(unmatched, Map.of("en", "Eiffel Tower")), wikipedia(
                entry("ja", "ja", null, "説明", 22L, "https://creativecommons.org/licenses/by-sa/4.0/")));

        assertThat(draft.form().getTranslations().get(0).getName()).isEmpty();
        assertThat(draft.form().getTranslations().get(2).getDescription()).isEmpty();
        assertThat(draft.form().getWikipediaRevisionIds()).containsOnlyNulls();
        assertThat(draft.notes()).anyMatch(note -> note.contains("일본어 제목이 없어"));
        assertThat(draft.autoRegionId()).isNull();
        assertThat(draft.form().getRegionId()).isNull();

        // 경로가 매칭 결과의 국가·지역과 어긋나면 자동 선택하지 않는다.
        var inconsistent = new RegionMatch(200L, 300L, true, "",
                List.of(new RegionPathItem(1L, "유럽"), new RegionPathItem(999L, "다른 국가"), new RegionPathItem(300L, "파리")));
        assertThat(builder.draft(preview(inconsistent, Map.of("ko", "에펠탑")), null).autoRegionId()).isNull();
    }

    @Test
    void registrationFormAddsAdminChoicesTravelInfoForTheChosenTypeOnlyAndOnePhoto() throws Exception {
        var unmatched = new RegionMatch(200L, null, false, "", List.of());
        DestinationForm form = builder.form(preview(unmatched, Map.of("en", "Eiffel Tower")), null,
                new WikidataDestinationFormBuilder.Choices(DestinationType.ACCOMMODATION, "SUMMER", 301L,
                        "  에펠탑  ", "Tour Eiffel.jpg"));

        assertThat(form.getType()).isEqualTo(DestinationType.ACCOMMODATION);
        assertThat(form.getSeason()).isEqualTo("SUMMER");
        assertThat(form.getRegionId()).isEqualTo(301L);
        assertThat(form.getTranslations().get(0).getName()).isEqualTo("에펠탑");
        assertThat(form.getAccommodationInfo().getHomepageUrl()).isEqualTo("https://www.toureiffel.paris/");
        // 숙소 전화번호 칸은 32자라 더 긴 번호는 넣지 않는다.
        assertThat(form.getAccommodationInfo().getContactNumber()).isNull();
        assertThat(form.getAttractionInfo()).isNull();
        var selection = new ObjectMapper().readTree(form.getCommonsSelectedPhotosJson());
        assertThat(selection.path("qid").asText()).isEqualTo("Q243");
        assertThat(selection.path("photos")).hasSize(1);
        assertThat(selection.path("photos").get(0).path("fileName").asText()).isEqualTo("Tour Eiffel.jpg");
        assertThat(selection.path("photos").get(0).path("main").asBoolean()).isTrue();

        DestinationForm attraction = builder.form(preview(unmatched, Map.of("ko", "에펠탑")), null,
                new WikidataDestinationFormBuilder.Choices(DestinationType.ATTRACTION, "SPRING", null, null, null));
        assertThat(attraction.getAttractionInfo().getContactNumber()).isEqualTo(LONG_PHONE);
        assertThat(attraction.getRegionId()).isNull();
        assertThat(attraction.getCommonsSelectedPhotosJson()).isEmpty();
    }

    private static final String LONG_PHONE = "+33 8 92 70 12 39 (standard, tarif spécial)";

    private RegionMatch matchedRegion() {
        return new RegionMatch(200L, 300L, true, "",
                List.of(new RegionPathItem(1L, "유럽"), new RegionPathItem(200L, "프랑스"), new RegionPathItem(300L, "파리")));
    }

    private WikidataDestinationPreview preview(RegionMatch match, Map<String, String> names) {
        return new WikidataDestinationPreview("Q243", names, Map.of("ko", "파리의 탑"), "Q142", "프랑스",
                List.of("파리"), 48.8583, 2.2944, null, null, null, match,
                new WikidataDestinationPreview.TravelInfo("https://www.toureiffel.paris/", LONG_PHONE, false, false),
                Map.of());
    }

    private WikipediaDescriptionPreview wikipedia(TestEntry... entries) {
        return new WikipediaDescriptionPreview("Q243", List.of(entries).stream().map(TestEntry::build).toList());
    }

    private TestEntry entry(String language, String sourceLanguage, String variant, String description,
                            Long revisionId, String licenseUrl) {
        return new TestEntry(language, sourceLanguage, variant, description, revisionId, licenseUrl, null);
    }

    private record TestEntry(String language, String sourceLanguage, String variant, String description,
                             Long revisionId, String licenseUrl, String displayTitle) {
        TestEntry withDisplayTitle(String title) {
            return new TestEntry(language, sourceLanguage, variant, description, revisionId, licenseUrl, title);
        }

        LanguageEntry build() {
            return new LanguageEntry(language, "AVAILABLE", "title", description, description.length(),
                    "https://" + sourceLanguage + ".wikipedia.org/wiki/x", sourceLanguage, variant,
                    "CC BY-SA 4.0", licenseUrl, revisionId, null, displayTitle);
        }
    }
}
