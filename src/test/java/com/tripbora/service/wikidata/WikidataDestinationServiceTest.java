package com.tripbora.service.wikidata;

import com.tripbora.model.CountryCategory;
import com.tripbora.service.category.CountryCategoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WikidataDestinationServiceTest {

    @Mock private WikidataApiClient apiClient;
    @Mock private CountryCategoryService countryCategoryService;
    @Mock private WikipediaApiClient wikipediaApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WikidataDestinationService service;

    @BeforeEach
    void setUp() {
        service = new WikidataDestinationService(apiClient, countryCategoryService,
                new WikidataAutofillCache(apiClient), wikipediaApiClient);
    }

    @Test
    void quickSearchReturnsOnlyTheSearchResultWithoutFetchingEntities() {
        when(apiClient.searchHits("에펠탑", "ko")).thenReturn(List.of(
                new WikidataApiClient.SearchHit("Q243", "에펠탑", "ko", "파리의 건축물", "ko")));

        var candidates = service.quickSearch(" 에펠탑 ");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).qid()).isEqualTo("Q243");
        assertThat(candidates.get(0).name()).isEqualTo("에펠탑");
        assertThat(candidates.get(0).shortDescription()).isEqualTo("파리의 건축물");
        assertThat(candidates.get(0).country()).isNull();
        verify(apiClient, never()).getEntities(anyList(), anyBoolean());
        verify(apiClient, never()).getAutofillEntities(anyList());
    }

    @Test
    void searchDetailsRejectInvalidOrTooManyQidsBeforeCallingWikidata() {
        assertThatThrownBy(() -> service.searchDetails(List.of("Q243", "Q1|Q2")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchDetails(java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(i -> "Q" + i).toList()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchDetails(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(apiClient, never()).getAutofillEntities(anyList());
    }

    @Test
    void autofillPreviewReusesTheEntityFetchedBySearchDetailsAndReadsRegionsWithSmallCalls() throws Exception {
        stubEntities(Map.of(
                "Q243", entity("""
                        {"id":"Q243","labels":{"ko":{"value":"에펠탑"}},
                         "claims":{"P17":[{"mainsnak":{"datavalue":{"value":{"id":"Q142"}}}}],
                                   "P131":[{"mainsnak":{"datavalue":{"value":{"id":"Q259463"}}}}]}}
                        """),
                "Q142", entity("{\"id\":\"Q142\",\"labels\":{\"ko\":{\"value\":\"프랑스\"}}}"),
                "Q259463", entity("{\"id\":\"Q259463\",\"labels\":{\"ko\":{\"value\":\"파리 7구\"}}}"),
                "Q90", entity("{\"id\":\"Q90\",\"labels\":{\"ko\":{\"value\":\"파리\"},\"en\":{\"value\":\"Paris\"}}}")));
        when(apiClient.getClaims("Q259463", "P131")).thenReturn(entity(
                "{\"claims\":{\"P131\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q90\"}}}}]}}"));
        when(apiClient.getClaims("Q90", "P131")).thenReturn(entity("{\"claims\":{}}"));

        service.searchDetails(List.of("Q243"));
        var preview = service.previewForAutofill("q243");

        assertThat(preview.qid()).isEqualTo("Q243");
        assertThat(preview.country()).isEqualTo("프랑스");
        assertThat(preview.regionPath()).containsExactly("파리 7구", "파리");
        verify(apiClient, times(1)).getAutofillEntities(anyList());
        // 상위 지역은 전체 claims(수백 KB) 대신 라벨과 P131만 받는다.
        verify(apiClient, never()).getEntities(anyList(), eq(true));
    }

    @Test
    void saveTimeRevalidationPreviewNeverUsesTheAutofillCache() throws Exception {
        stubEntities(Map.of("Q243", entity("{\"id\":\"Q243\",\"labels\":{\"ko\":{\"value\":\"에펠탑\"}}}")));

        service.previewForAutofill("Q243");
        service.preview("Q243");
        service.preview("Q243");

        // 자동입력 1회(캐시) + 저장 전 재검증 2회(매번 새로 조회)
        verify(apiClient, times(3)).getAutofillEntities(List.of("Q243"));
    }

    @Test
    void saveTimeRevalidationReadsParentRegionsWithSmallFreshCallsInsteadOfFullClaims() throws Exception {
        stubEntities(Map.of(
                "Q243", entity("""
                        {"id":"Q243","labels":{"ko":{"value":"에펠탑"}},
                         "claims":{"P17":[{"mainsnak":{"datavalue":{"value":{"id":"Q142"}}}}],
                                   "P131":[{"mainsnak":{"datavalue":{"value":{"id":"Q90"}}}}]}}
                        """),
                "Q142", entity("{\"id\":\"Q142\",\"labels\":{\"ko\":{\"value\":\"프랑스\"}}}"),
                "Q90", entity("{\"id\":\"Q90\",\"labels\":{\"ko\":{\"value\":\"파리\"}}}")));

        service.preview("Q243");
        service.preview("Q243");

        assertThat(service.preview("Q243").regionPath()).containsExactly("파리");
        verify(apiClient, times(3)).getClaims("Q90", "P131");
        verify(apiClient, never()).getEntities(anyList(), eq(true));
    }

    @Test
    void koreanSearchKeepsOnlyPlaceCandidatesAndShowsQidCountryAndImage() throws Exception {
        stubEntities(Map.of(
                "Q243", entity("""
                        {"id":"Q243","labels":{"ko":{"value":"에펠탑"},"en":{"value":"Eiffel Tower"}},
                         "descriptions":{"ko":{"value":"프랑스 파리 마르스 광장의 건축물"}},
                         "claims":{"P17":[{"mainsnak":{"datavalue":{"value":{"id":"Q142"}}}}],
                                   "P131":[{"mainsnak":{"datavalue":{"value":{"id":"Q259463"}}}}],
                                   "P625":[{"mainsnak":{"datavalue":{"value":{"latitude":48.858296,"longitude":2.294479}}}}],
                                   "P18":[{"mainsnak":{"datavalue":{"value":"Tour Eiffel Wikimedia Commons.jpg"}}}]}}
                        """),
                "Q3821251", entity("{" + "\"id\":\"Q3821251\",\"labels\":{\"en\":{\"value\":\"The Eiffel Tower\"}}}"),
                "Q142", entity("{" + "\"id\":\"Q142\",\"labels\":{\"ko\":{\"value\":\"프랑스\"}}}"),
                "Q259463", entity("{" + "\"id\":\"Q259463\",\"labels\":{\"ko\":{\"value\":\"파리 7구\"}}}")));

        var candidates = service.searchDetails(List.of("Q243", "Q3821251"));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).qid()).isEqualTo("Q243");
        assertThat(candidates.get(0).name()).isEqualTo("에펠탑");
        assertThat(candidates.get(0).shortDescription()).isEqualTo("프랑스 파리 마르스 광장의 건축물");
        assertThat(candidates.get(0).country()).isEqualTo("프랑스");
        assertThat(candidates.get(0).region()).isEqualTo("파리 7구");
        assertThat(candidates.get(0).imageFileName()).isEqualTo("Tour Eiffel Wikimedia Commons.jpg");
        assertThat(candidates.get(0).imageUrl()).startsWith("https://commons.wikimedia.org/wiki/Special:FilePath/");
    }

    @Test
    void previewKeepsMissingLanguageEmptyAndMatchesParisOnlyThroughItsCountry() throws Exception {
        stubEntities(Map.of(
                "Q243", entity("""
                        {"id":"Q243","labels":{"ko":{"value":"에펠탑"},"en":{"value":"Eiffel Tower"},
                         "ja":{"value":"エッフェル塔"},"zh-hans":{"value":"埃菲尔铁塔"}},
                         "descriptions":{"ko":{"value":"프랑스 파리 마르스 광장의 건축물"},
                         "en":{"value":"tower located in Paris"}},
                         "claims":{"P17":[{"mainsnak":{"datavalue":{"value":{"id":"Q142"}}}}],
                                   "P131":[{"mainsnak":{"datavalue":{"value":{"id":"Q259463"}}}}],
                                   "P625":[{"mainsnak":{"datavalue":{"value":{"latitude":48.858296,"longitude":2.294479}}}}],
                                   "P18":[{"mainsnak":{"datavalue":{"value":"Tour Eiffel Wikimedia Commons.jpg"}}}]}}
                        """),
                "Q142", entity("{" + "\"id\":\"Q142\",\"labels\":{\"ko\":{\"value\":\"프랑스\"},\"en\":{\"value\":\"France\"}}}"),
                "Q259463", entity("{" + "\"id\":\"Q259463\",\"labels\":{\"ko\":{\"value\":\"파리 7구\"}},\"claims\":{\"P131\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q90\"}}}}]}}"),
                "Q90", entity("{" + "\"id\":\"Q90\",\"labels\":{\"ko\":{\"value\":\"파리\"},\"en\":{\"value\":\"Paris\"}}}")));
        when(countryCategoryService.getRegionsByDepth(2)).thenReturn(List.of(category(17L, 16L, "프랑스", "France")));
        when(countryCategoryService.getRegionsByParentId(17L)).thenReturn(List.of(category(106L, 17L, "파리", "Paris")));
        when(countryCategoryService.getRegionPath(106L)).thenReturn(List.of(
                category(16L, null, "유럽", "Europe"),
                category(17L, 16L, "프랑스", "France"),
                category(106L, 17L, "파리", "Paris")));

        var preview = service.preview("Q243");

        assertThat(preview.names()).containsEntry("ko", "에펠탑").containsEntry("en", "Eiffel Tower")
                .containsEntry("ja", "エッフェル塔").containsEntry("zh-CN", "埃菲尔铁塔")
                .doesNotContainKey("zh-TW");
        assertThat(preview.shortDescriptions()).containsOnlyKeys("ko", "en");
        assertThat(preview.regionPath()).containsExactly("파리 7구", "파리");
        assertThat(preview.latitude()).isEqualTo(48.858296);
        assertThat(preview.longitude()).isEqualTo(2.294479);
        assertThat(preview.regionMatch().matched()).isTrue();
        assertThat(preview.regionMatch().countryId()).isEqualTo(17L);
        assertThat(preview.regionMatch().regionId()).isEqualTo(106L);
        assertThat(preview.regionMatch().path()).extracting("id").containsExactly(16L, 17L, 106L);
    }

    @Test
    void regionIsNotApplicableWithoutACompleteExistingCategoryPath() throws Exception {
        stubEntities(Map.of(
                "Q243", entity("""
                        {"id":"Q243","claims":{
                         "P17":[{"mainsnak":{"datavalue":{"value":{"id":"Q142"}}}}],
                         "P131":[{"mainsnak":{"datavalue":{"value":{"id":"Q90"}}}}]}}
                        """),
                "Q142", entity("{" + "\"id\":\"Q142\",\"labels\":{\"en\":{\"value\":\"France\"}}}"),
                "Q90", entity("{" + "\"id\":\"Q90\",\"labels\":{\"en\":{\"value\":\"Paris\"}}}")));
        when(countryCategoryService.getRegionsByDepth(2)).thenReturn(List.of(category(17L, 16L, "프랑스", "France")));
        when(countryCategoryService.getRegionsByParentId(17L)).thenReturn(List.of(category(106L, 17L, "파리", "Paris")));

        var preview = service.preview("Q243");

        assertThat(preview.regionMatch().matched()).isFalse();
    }

    @Test
    void missingLocationAndImageStayMissingAndRequireAdminReview() throws Exception {
        stubEntities(Map.of("Q999", entity("{" + "\"id\":\"Q999\",\"labels\":{\"en\":{\"value\":\"Unnamed site\"}}}")));

        var preview = service.preview("Q999");

        assertThat(preview.names()).containsOnlyKeys("en");
        assertThat(preview.country()).isNull();
        assertThat(preview.latitude()).isNull();
        assertThat(preview.imageUrl()).isNull();
        assertThat(preview.regionMatch().matched()).isFalse();
        verify(countryCategoryService, never()).getRegionsByDepth(2);
    }

    @Test
    void fallbackLanguageIsNotPresentedAsAnOriginalTranslation() throws Exception {
        stubEntities(Map.of("Q999", entity("""
                {"id":"Q999","labels":{"en":{"language":"en","value":"Unnamed site"},
                 "zh-hant":{"language":"zh-hans","value":"简体名称"}},
                 "descriptions":{"zh-hant":{"language":"zh-hans","value":"简体说明"}}}
                """)));

        var preview = service.preview("Q999");

        assertThat(preview.names()).containsOnlyKeys("en");
        assertThat(preview.shortDescriptions()).doesNotContainKey("zh-TW");
    }

    @Test
    void traditionalChineseNamePrefersTaiwanLabelThenGenericTraditionalButNeverSimplified() throws Exception {
        stubEntities(Map.of(
                "Q1", entity("""
                        {"id":"Q1","labels":{"zh-tw":{"language":"zh-tw","value":"臺灣標題"},
                         "zh-hant":{"language":"zh-hant","value":"繁體標題"},
                         "zh-hans":{"language":"zh-hans","value":"简体标题"}}}
                        """),
                "Q2", entity("""
                        {"id":"Q2","labels":{"zh-hant":{"language":"zh-hant","value":"繁體標題"}}}
                        """),
                // 실제 Q243: 번체 label 없이 간체(zh-hans)만 있다.
                "Q243", entity("""
                        {"id":"Q243","labels":{"zh-hans":{"language":"zh-hans","value":"埃菲尔铁塔"},
                         "ko":{"language":"ko","value":"에펠탑"}}}
                        """)));

        assertThat(service.preview("Q1").names()).containsEntry("zh-TW", "臺灣標題").containsEntry("zh-CN", "简体标题");
        assertThat(service.preview("Q2").names()).containsEntry("zh-TW", "繁體標題");
        assertThat(service.preview("Q243").names()).containsEntry("zh-CN", "埃菲尔铁塔").doesNotContainKey("zh-TW");
    }

    @Test
    void missingTraditionalIsConvertedFromTheSimplifiedOriginalInOneRequestAndMarkedAsConversion() throws Exception {
        // 실제 Q243: zh-hans·zh-cn·zh(간체) 설명만 있고 번체 설명은 없다.
        stubEntities(Map.of("Q243", entity("""
                {"id":"Q243","descriptions":{
                 "zh-hans":{"language":"zh-hans","value":"位于法国巴黎战神广场的铁制镂空塔"},
                 "zh-cn":{"language":"zh-cn","value":"巴黎建筑"},
                 "zh":{"language":"zh","value":"特大型铁制镂空塔、法国巴黎地标性建筑"}}}
                """)));
        // zh 원문 확인과 간체 원문 변환을 한 요청으로 처리한다. zh 원문은 번체로 바꾸면 달라지므로 번체 원문이 아니다.
        when(wikipediaApiClient.convertChineseScript(
                List.of("特大型铁制镂空塔、法国巴黎地标性建筑", "位于法国巴黎战神广场的铁制镂空塔"), "zh-hant"))
                .thenReturn(List.of("特大型鐵製鏤空塔、法國巴黎地標性建築", "位於法國巴黎戰神廣場的鐵製鏤空塔"));

        var preview = service.previewForAutofill("Q243");

        assertThat(preview.shortDescriptions())
                .containsEntry("zh-CN", "位于法国巴黎战神广场的铁制镂空塔")
                .containsEntry("zh-TW", "位於法國巴黎戰神廣場的鐵製鏤空塔");
        assertThat(preview.shortDescriptionConversions()).containsExactly(Map.entry("zh-TW", "zh-hans"));
        verify(wikipediaApiClient, times(1)).convertChineseScript(anyList(), anyString());
    }

    @Test
    void missingSimplifiedIsConvertedFromTheTraditionalOriginalButVerifiedGenericChineseWins() throws Exception {
        stubEntities(Map.of(
                // 실제 Q2981: 번체(zh-tw·zh-hant·zh)만 있다.
                "Q2981", entity("""
                        {"id":"Q2981","descriptions":{
                         "zh-tw":{"language":"zh-tw","value":"天主教巴黎總教區的主教座堂"},
                         "zh-hant":{"language":"zh-hant","value":"天主教巴黎總教區的主教座堂"},
                         "zh":{"language":"zh","value":"天主教巴黎總教區的主教座堂"}}}
                        """),
                // 실제 Q9188: 간체는 zh-cn, zh 설명은 번체 원문이다. 번체 칸은 변환이 아니라 zh 원문을 쓴다.
                "Q9188", entity("""
                        {"id":"Q9188","descriptions":{
                         "zh-cn":{"language":"zh-cn","value":"美国纽约摩天大楼"},
                         "zh":{"language":"zh","value":"位於美國紐約曼哈頓中城的摩天大樓"}}}
                        """)));
        when(wikipediaApiClient.convertChineseScript(List.of("天主教巴黎總教區的主教座堂"), "zh-hans"))
                .thenReturn(List.of("天主教巴黎总教区的主教座堂"));
        when(wikipediaApiClient.convertChineseScript(List.of("位於美國紐約曼哈頓中城的摩天大樓", "美国纽约摩天大楼"), "zh-hant"))
                .thenReturn(List.of("位於美國紐約曼哈頓中城的摩天大樓", "美國紐約摩天大樓"));

        var notreDame = service.previewForAutofill("Q2981");
        var empire = service.previewForAutofill("Q9188");

        assertThat(notreDame.shortDescriptions()).containsEntry("zh-CN", "天主教巴黎总教区的主教座堂")
                .containsEntry("zh-TW", "天主教巴黎總教區的主教座堂");
        assertThat(notreDame.shortDescriptionConversions()).containsExactly(Map.entry("zh-CN", "zh-tw"));
        assertThat(empire.shortDescriptions()).containsEntry("zh-CN", "美国纽约摩天大楼")
                .containsEntry("zh-TW", "位於美國紐約曼哈頓中城的摩天大樓");
        assertThat(empire.shortDescriptionConversions()).isEmpty();
    }

    @Test
    void bothOriginalsNeedNoConversionRequest() throws Exception {
        // 실제 Q12501: zh-hans·zh-hant 원문이 모두 있다.
        stubEntities(Map.of("Q12501", entity("""
                {"id":"Q12501","descriptions":{
                 "zh-hans":{"language":"zh-hans","value":"中国古代的一系列边界防御工事"},
                 "zh-hant":{"language":"zh-hant","value":"中國世界遺產"},
                 "zh":{"language":"zh","value":"中国古代的一系列边界防御工事"}}}
                """)));

        var preview = service.previewForAutofill("Q12501");

        assertThat(preview.shortDescriptions()).containsEntry("zh-CN", "中国古代的一系列边界防御工事")
                .containsEntry("zh-TW", "中國世界遺產");
        assertThat(preview.shortDescriptionConversions()).isEmpty();
        verify(wikipediaApiClient, never()).convertChineseScript(anyList(), anyString());
    }

    @Test
    void failedOrTooLongConversionLeavesTheFieldEmptyAndSaveRevalidationNeverConverts() throws Exception {
        stubEntities(Map.of(
                "Q45178", entity("""
                        {"id":"Q45178","descriptions":{"zh":{"language":"zh","value":"澳大利亞悉尼（Sydney ）的建築"}}}
                        """),
                "Q1", entity("""
                        {"id":"Q1","descriptions":{"zh-hans":{"language":"zh-hans","value":"简体说明"}}}
                        """)));
        when(wikipediaApiClient.convertChineseScript(List.of("澳大利亞悉尼（Sydney ）的建築"), "zh-hans"))
                .thenThrow(new WikipediaApiException("Wikipedia 요청이 많습니다. 잠시 후 다시 시도해 주세요."));
        when(wikipediaApiClient.convertChineseScript(List.of("澳大利亞悉尼（Sydney ）的建築"), "zh-hant"))
                .thenReturn(List.of("澳大利亞悉尼（Sydney ）的建築"));
        when(wikipediaApiClient.convertChineseScript(List.of("简体说明"), "zh-hant"))
                .thenReturn(List.of("說".repeat(251)));

        var sydney = service.previewForAutofill("Q45178");
        var tooLong = service.previewForAutofill("Q1");

        // 번체 칸은 확인된 zh 원문, 간체 칸은 변환 요청이 실패해 비워 둔다.
        assertThat(sydney.shortDescriptions()).containsEntry("zh-TW", "澳大利亞悉尼（Sydney ）的建築")
                .doesNotContainKey("zh-CN");
        assertThat(sydney.shortDescriptionConversions()).isEmpty();
        assertThat(tooLong.shortDescriptions()).doesNotContainKey("zh-TW");
        assertThat(tooLong.shortDescriptionConversions()).isEmpty();
        assertThat(service.preview("Q45178").shortDescriptions()).doesNotContainKeys("zh-CN", "zh-TW");
        verify(wikipediaApiClient, times(3)).convertChineseScript(anyList(), anyString());
    }

    @Test
    void q243TravelInfoUsesTheEnglishOfficialWebsiteAndHasNoPhoneOrOpeningHours() throws Exception {
        // 실제 Q243: P856 이 프랑스어·영어·이탈리아어 페이지 3개, 전화·운영시간·요금 없음.
        stubEntities(Map.of("Q243", entity("""
                {"id":"Q243","claims":{"P856":[
                  %s, %s, %s]}}
                """.formatted(website("https://www.toureiffel.paris", "Q150", "normal"),
                website("https://www.toureiffel.paris/en", "Q1860", "normal"),
                website("https://www.toureiffel.paris/it", "Q652", "normal")))));

        var travelInfo = service.preview("Q243").travelInfo();

        assertThat(travelInfo.homepageUrl()).isEqualTo("https://www.toureiffel.paris/en");
        assertThat(travelInfo.contactNumber()).isNull();
        assertThat(travelInfo.openingHoursStated()).isFalse();
        assertThat(travelInfo.admissionFeeStated()).isFalse();
    }

    @Test
    void louvreStyleEntityFillsPhoneAndOnlyFlagsOpeningHoursAndFees() throws Exception {
        stubEntities(Map.of("Q19675", entity("""
                {"id":"Q19675","claims":{
                  "P856":[%s, %s],
                  "P1329":[{"rank":"normal","mainsnak":{"datavalue":{"value":"+33-1-40-20-53-17"}}},
                           {"rank":"normal","mainsnak":{"datavalue":{"value":"+33-1-40-20-50-50"}}}],
                  "P3025":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q99731117"}}},
                            "qualifiers":{"P8626":[{"datavalue":{"value":{"id":"Q41618181"}}}]}}],
                  "P2555":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"amount":"+15","unit":"http://www.wikidata.org/entity/Q4916"}}}}]}}
                """.formatted(website("https://www.louvre.fr/", "Q150", "normal"),
                website("https://www.louvre.fr/en/", "Q1860", "normal")))));

        var travelInfo = service.preview("Q19675").travelInfo();

        assertThat(travelInfo.homepageUrl()).isEqualTo("https://www.louvre.fr/en/");
        assertThat(travelInfo.contactNumber()).isEqualTo("+33-1-40-20-53-17");
        // 운영시간·요금은 값이 있다는 사실만 알리고 문장으로 만들지 않는다.
        assertThat(travelInfo.openingHoursStated()).isTrue();
        assertThat(travelInfo.admissionFeeStated()).isTrue();
    }

    @Test
    void travelInfoPrefersRankedKoreanPagesAndRejectsUnsafeOrOversizedValues() throws Exception {
        stubEntities(Map.of(
                "Q1", entity("""
                        {"id":"Q1","claims":{"P856":[%s, %s, %s],
                         "P1329":[{"rank":"deprecated","mainsnak":{"datavalue":{"value":"+1 555 0100"}}},
                                  {"rank":"normal","mainsnak":{"datavalue":{"value":"call us"}}}]}}
                        """.formatted(website("https://example.org/en", "Q1860", "normal"),
                        website("https://example.org/ko", "Q9176", "normal"),
                        website("https://old.example.org", null, "deprecated"))),
                "Q2", entity("""
                        {"id":"Q2","claims":{"P856":[%s, %s]}}
                        """.formatted(website("https://example.org/en", "Q1860", "normal"),
                        website("https://example.org/official", null, "preferred"))),
                "Q3", entity("""
                        {"id":"Q3","claims":{"P856":[%s]}}
                        """.formatted(website("javascript:alert(1)", null, "normal"))),
                "Q4", entity("""
                        {"id":"Q4","claims":{"P856":[%s]}}
                        """.formatted(website("https://example.org/" + "a".repeat(260), null, "normal")))));

        assertThat(service.preview("Q1").travelInfo().homepageUrl()).isEqualTo("https://example.org/ko");
        assertThat(service.preview("Q1").travelInfo().contactNumber()).isNull();
        assertThat(service.preview("Q2").travelInfo().homepageUrl()).isEqualTo("https://example.org/official");
        assertThat(service.preview("Q3").travelInfo().homepageUrl()).isNull();
        assertThat(service.preview("Q4").travelInfo().homepageUrl()).isNull();
    }

    @Test
    void britishEnglishPageCountsAsEnglishLikeTheBritishMuseumEntity() throws Exception {
        // 실제 대영박물관(Q6373): https 주소는 영국 영어(Q7979), 옛 http 주소는 영어(Q1860)로 표시돼 있다.
        stubEntities(Map.of("Q6373", entity("""
                {"id":"Q6373","claims":{"P856":[%s, %s]}}
                """.formatted(website("https://www.britishmuseum.org/", "Q7979", "normal"),
                website("http://britishmuseum.org/", "Q1860", "normal")))));

        assertThat(service.preview("Q6373").travelInfo().homepageUrl()).isEqualTo("https://www.britishmuseum.org/");
    }

    private String website(String url, String languageQid, String rank) {
        String qualifiers = languageQid == null ? "" : """
                ,"qualifiers":{"P407":[{"datavalue":{"value":{"id":"%s"}}}]}""".formatted(languageQid);
        return """
                {"rank":"%s","mainsnak":{"datavalue":{"value":"%s"}}%s}""".formatted(rank, url, qualifiers);
    }

    @Test
    void emptySearchIsDistinctFromApiFailure() {
        when(apiClient.searchHits("Unknown place", "en")).thenReturn(List.of());

        assertThat(service.quickSearch("Unknown place")).isEmpty();
        verify(apiClient, never()).getEntities(anyList(), anyBoolean());
    }

    @Test
    void malformedQidIsRejectedBeforeCallingWikidata() {
        assertThatThrownBy(() -> service.preview("Q243|Q9202"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(apiClient, never()).getEntities(anyList(), anyBoolean());
    }

    private void stubEntities(Map<String, JsonNode> entities) {
        org.mockito.stubbing.Answer<Map<String, JsonNode>> answer = invocation -> {
            Map<String, JsonNode> found = new HashMap<>();
            for (String qid : invocation.<List<String>>getArgument(0)) {
                if (entities.containsKey(qid)) {
                    found.put(qid, entities.get(qid));
                }
            }
            return found;
        };
        lenient().when(apiClient.getEntities(anyList(), anyBoolean())).thenAnswer(answer);
        lenient().when(apiClient.getAutofillEntities(anyList())).thenAnswer(answer);
        // 상위 지역은 wbgetclaims 로 P131만 받는다. 같은 엔티티 고정값의 P131을 돌려준다.
        lenient().when(apiClient.getClaims(org.mockito.ArgumentMatchers.anyString(), eq("P131"))).thenAnswer(invocation -> {
            var claims = objectMapper.createObjectNode();
            JsonNode entity = entities.get(invocation.<String>getArgument(0));
            if (entity != null && entity.path("claims").has("P131")) {
                claims.putObject("claims").set("P131", entity.path("claims").path("P131"));
            } else {
                claims.putObject("claims");
            }
            return claims;
        });
    }

    private JsonNode entity(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private CountryCategory category(Long id, Long parentId, String korean, String english) {
        CountryCategory category = new CountryCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setRegionName(korean);
        category.setNameEn(english);
        return category;
    }
}
